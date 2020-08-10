/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package de.embl.cba.bigdataprocessor;

import de.embl.cba.bigdataprocessor.ui.BigDataProcessorUI;
import de.embl.cba.imaris.ImarisDataSet;
import de.embl.cba.imaris.ImarisUtils;
import de.embl.cba.imaris.ImarisWriter;
import de.embl.cba.bigdataprocessor.save.SaveVS2TiffPlane;
import de.embl.cba.bigdataprocessor.virtualstack2.*;
import de.embl.cba.bigdataprocessor.log.IJLazySwingLogger;
import de.embl.cba.bigdataprocessor.log.Logger;
import de.embl.cba.bigdataprocessor.save.SaveVS2Frame;
import de.embl.cba.bigdataprocessor.save.SavingSettings;
import de.embl.cba.bigdataprocessor.utils.ImageDataInfo;
import de.embl.cba.bigdataprocessor.utils.MonitorThreadPoolStatus;
import de.embl.cba.bigdataprocessor.utils.Utils;
import ch.systemsx.cisd.hdf5.*;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import de.embl.cba.util.Point3D;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BigDataProcessor
{

    private static Logger logger = new IJLazySwingLogger();

    public boolean interruptSavingThreads = false;

    public BigDataProcessor()
    {
    }

    public ImagePlus openFromDirectory(
            String directory,
            String namingScheme,
            String filterPattern,
            String h5DataSetName,
            ImageDataInfo imageDataInfo,
            int numIOThreads,
            boolean showImage,
            boolean partialDataSet)
    {

        if ( imageDataInfo == null )
        {
            imageDataInfo = new ImageDataInfo();
        }


        if ( namingScheme.contains("<Z") )
        {
            // TODO: change below logic somehow (maybe via GUI?)
            partialDataSet = true;

            if ( ! setMissingInfos(
                    imageDataInfo,
                    directory,
                    namingScheme ) )
            {
                return null;
            }
        }
        else
        {
            imageDataInfo = new ImageDataInfo();
            imageDataInfo.h5DataSetName = h5DataSetName;

            if ( ! setAllInfosByParsingFilesAndFolders(
                    imageDataInfo,
                    directory,
                    namingScheme,
                    filterPattern ) )
            {
                return null;
            }

        }


        //
        // init the virtual stack
        //
        VirtualStack2 stack = new VirtualStack2(
                directory,
                imageDataInfo.channelFolders,
                imageDataInfo.ctzFileList,
                imageDataInfo.nC,
                imageDataInfo.nT,
                imageDataInfo.nX,
                imageDataInfo.nY,
                imageDataInfo.nZ,
                imageDataInfo.bitDepth,
                imageDataInfo.fileType,
                imageDataInfo.h5DataSetName);


        stack.setFilterPattern( filterPattern );
        stack.setH5DataSet( h5DataSetName );
        stack.setDirectory( directory );
        stack.setNamingScheme( namingScheme );

        ImagePlus imp = createImagePlusFromVS2( stack );

        // obtain file header informations for all c, t, z
        //
        try
        {
            boolean throwFileNotExistsError = partialDataSet ? false : true;

            // Spawn the threads
            //
            ExecutorService es = Executors.newFixedThreadPool(numIOThreads);
            List<Future> futures = new ArrayList<>();
            for (int t = 0; t < imageDataInfo.nT; t++)
            {
                if ( imageDataInfo.fileType.equals( Utils.FileType.TIFF_PLANES.toString() ) )
                {
                    for (int z = 0; z < imageDataInfo.nZ; z++)
                    {
                        futures.add(
                                es.submit(
                                        new ParseFilesIntoVirtualStack(imp, t, z, showImage, throwFileNotExistsError)
                                )
                        );
                    }
                }
                else
                {
                    // z = 0 will parse the whole stack file
                    futures.add(
                            es.submit(
                                    new ParseFilesIntoVirtualStack(imp, t, 0, showImage, throwFileNotExistsError)
                            )
                    );
                }
            }

            // Monitor the progress
            //
            Thread thread = new Thread(new Runnable() {
                public void run()
                {
                    MonitorThreadPoolStatus.showProgressAndWaitUntilDone(
                            futures,
                            "Parsing files...",
                            2000);
                }
            });
            thread.start();

        }
        catch (Exception e)
        {
            logger.error("DataStreamingTools:openFromDirectory:ParseFilesIntoVirtualStack: "+e.toString());
        }

        return( imp );

    }

    public boolean setMissingInfos(
            ImageDataInfo imageDataInfo,
            String directory,
            String namingPattern
    )
    {
        int[] ctzMin = new int[3];
        int[] ctzMax = new int[3];
        int[] ctzPad = new int[3];
        int[] ctzSize = new int[3];
        boolean hasC = false;
        boolean hasT = false;
        boolean hasZ = false;

        Matcher matcher;

        logger.info( "Importing/creating file information from pre-defined naming scheme." );

        // channels
        matcher = Pattern.compile(".*<C(\\d+)-(\\d+)>.*").matcher( namingPattern );
        if ( matcher.matches() )
        {
            hasC = true;
            ctzMin[0] = Integer.parseInt( matcher.group(1) );
            ctzMax[0] = Integer.parseInt( matcher.group(2) );
            ctzPad[0] = matcher.group(1).length();
        }
        else
        {
            ctzMin[0] = ctzMax[0] = ctzPad[0] = 0;
        }

        imageDataInfo.channelFolders = new String[ ctzMax[ 0 ] - ctzMin[ 0 ] + 1 ];
        Arrays.fill( imageDataInfo.channelFolders, "" );

        // frames
        matcher = Pattern.compile(".*<T(\\d+)-(\\d+)>.*").matcher( namingPattern );
        if ( matcher.matches() )
        {
            hasT = true;
            ctzMin[1] = Integer.parseInt( matcher.group(1) );
            ctzMax[1] = Integer.parseInt( matcher.group(2) );
            ctzPad[1] = matcher.group(1).length();
        }
        else
        {
            ctzMin[1] = ctzMax[1] = ctzPad[1] = 0;
        }

        // slices
        matcher = Pattern.compile(".*<Z(\\d+)-(\\d+)>.*").matcher( namingPattern );
        if ( matcher.matches() )
        {
            hasZ = true;
            ctzMin[2] = Integer.parseInt( matcher.group(1) );
            ctzMax[2] = Integer.parseInt( matcher.group(2) );
            ctzPad[2] = matcher.group(1).length();
        }
        else
        {
            // determine number of slices from a file...
            logger.error("Please provide a z range as well.");
            return false;
        }

        for ( int i = 0; i < 3; ++i ) ctzSize[i] = ctzMax[i] - ctzMin[i] + 1;

        imageDataInfo.nC = ctzSize[0];
        imageDataInfo.nT = ctzSize[1];
        imageDataInfo.nZ = ctzSize[2];

        imageDataInfo.ctzFileList = new String[ctzSize[0]][ctzSize[1]][ctzSize[2]];

        if ( namingPattern.contains("<Z") && namingPattern.contains(".tif") )
        {
            imageDataInfo.fileType = Utils.FileType.TIFF_PLANES.toString();
        }
        else
        {
            logger.error("Sorry, currently only single tiff planes supported");
            return false;
        }

        boolean isObtainedImageDataInfo = false;

        for (int c = ctzMin[0]; c <= ctzMax[0]; c++)
        {
            for (int t = ctzMin[1]; t <= ctzMax[1] ; t++)
            {
                for (int z = ctzMin[2]; z <= ctzMax[2] ; z++)
                {

                    String fileName = "";

                    if (imageDataInfo.fileType.equals( Utils.FileType.TIFF_PLANES.toString()) )
                    {
                        fileName = namingPattern.replaceFirst(
                                "<Z(\\d+)-(\\d+)>",
                                String.format("%1$0" + ctzPad[2] + "d", z));
                    }
                    else
                    {
                        logger.error("DataStreamingTools:setMissingInfos:unsupported file type");
                    }


                    if ( hasC )
                    {
                        fileName = fileName.replaceFirst(
                                "<C(\\d+)-(\\d+)>",
                                String.format("%1$0" + ctzPad[0] + "d", c));
                    }

                    if ( hasT )
                    {
                        fileName = fileName.replaceFirst(
                                "<T(\\d+)-(\\d+)>",
                                String.format("%1$0" + ctzPad[1] + "d", t));
                    }

                    imageDataInfo.ctzFileList[c-ctzMin[0]][t-ctzMin[1]][z-ctzMin[2]] = fileName;

                    if ( ! isObtainedImageDataInfo )
                    {
                        File f = new File(directory + imageDataInfo.channelFolders[c-ctzMin[0]] + "/" + fileName);

                        if ( f.exists() && !f.isDirectory() )
                        {
                            setImageDataInfoFromTiff(
                                    imageDataInfo,
                                    directory + imageDataInfo.channelFolders[c-ctzMin[0]],
                                    fileName);

                            if ( imageDataInfo.fileType.equals( Utils.FileType.TIFF_PLANES.toString() ))
                                imageDataInfo.nZ = ctzSize[2];

                            logger.info(
                                    "Found one file; setting nx,ny,nz and bit-depth from this file: "
                                    + fileName
                            );

                            isObtainedImageDataInfo = true;
                        }
                    }
                }
            }
        }

        if ( ! isObtainedImageDataInfo )
        {
            logger.error("Could not open data set. There needs to be at least one file matching the naming scheme.");
        }

        return isObtainedImageDataInfo;
    }


    public boolean setAllInfosByParsingFilesAndFolders(
            ImageDataInfo imageDataInfo,
            String directory,
            String namingScheme,
            String filterPattern
    )
    {

        String[][] fileLists;
        int t = 0, z = 0, c = 0;
        String fileType = "not determined";
        FileInfoSer[] info;
        FileInfoSer fi0;
        List<String> channels = null, timepoints = null;

        int nC = 0, nT = 0, nZ = 0, nX = 0, nY = 0, bitDepth = 16;

        if ( namingScheme.equals( BigDataProcessorUI.LOAD_CHANNELS_FROM_FOLDERS) )
        {
            //
            // Check for sub-folders
            //
            logger.info("checking for sub-folders...");
            imageDataInfo.channelFolders = getFoldersInFolder(directory);

            if ( imageDataInfo.channelFolders != null )
            {
                fileLists = new String[imageDataInfo.channelFolders.length][];
                for (int i = 0; i < imageDataInfo.channelFolders.length; i++)
                {
                    fileLists[i] = getFilesInFolder(directory + imageDataInfo.channelFolders[i], filterPattern);
                    if ( fileLists[i] == null )
                    {
                        logger.info("no files found in folder: " + directory + imageDataInfo.channelFolders[i]);
                        return false;
                    }
                }
                logger.info( "found sub-folders => interpreting as channel folders." );
            }
            else
            {
                logger.info("no sub-folders found.");
                IJ.showMessage("No sub-folders found; please specify a different options for loading " +
                        "the channels");
                return false;
            }

        }
        else
        {

            //
            // Get files in main directory
            //

            logger.info("Searching files in folder: " + directory);
            fileLists = new String[ 1 ][ ];
            fileLists[ 0 ] = getFilesInFolder( directory, filterPattern );
            logger.info("Number of files in main folder matching the filter pattern: " + fileLists[0].length );

            if ( fileLists[0] == null || fileLists[0].length == 0 )
            {
                IJ.showMessage("No files matching this pattern were found: " + filterPattern);
                return false;
            }

        }


        if ( namingScheme.equals( BigDataProcessorUI.LEICA_SINGLE_TIFF ) ) // isLeicaSinglePlaneTiffFileType( fileLists[ 0 ] ) )
        {

            imageDataInfo.fileType = Utils.FileType.TIFF_PLANES.toString();

            // generate Leica nC,nT,nZ fileList
            //
            String dataDirectory;
            if ( imageDataInfo.channelFolders == null )
            {
                dataDirectory = directory;
            }
            else
            {
                dataDirectory = directory + imageDataInfo.channelFolders[ 0 ];
            }

            boolean success = initLeicaSinglePlaneTiffData( imageDataInfo, dataDirectory, filterPattern, fileLists[ 0 ], t, z, nC, nZ );

            if ( ! success )
            {
                return false;
            }

        }
        else // tiff stacks or h5 stacks
        {
            boolean hasCTPattern = false;

            if ( namingScheme.equals( BigDataProcessorUI.LOAD_CHANNELS_FROM_FOLDERS) )
            {
                nC = imageDataInfo.channelFolders.length;
                nT = fileLists[0].length;
            }
            else if ( namingScheme.equals( "None" ) )
            {
                nC = 1;
                nT = fileLists[0].length;
            }
            else if ( namingScheme.equals( BigDataProcessorUI.EM_TIFF_SLICES ) )
            {
                nC = 1;
                nT = 1;
                imageDataInfo.fileType = Utils.FileType.TIFF_PLANES.toString();
            }
            else
            {

                hasCTPattern = true;

                if (!(namingScheme.contains("<c>") && namingScheme.contains("<t>")))
                {
                    IJ.showMessage("The pattern for multi-channel loading must" +
                            "contain <c> and <t> to match channels and time in the filenames.");
                    return false;
                }

                // replace shortcuts by actual regexp
                namingScheme = namingScheme.replace("<c>", "(?<C>.*)");
                namingScheme = namingScheme.replace("<t>", "(?<T>.*)");

                imageDataInfo.channelFolders = new String[]{""};

                HashSet<String> channelsHS = new HashSet();
                HashSet<String> timepointsHS = new HashSet();

                Pattern patternCT = Pattern.compile( namingScheme );

                for (String fileName : fileLists[0])
                {

                    Matcher matcherCT = patternCT.matcher(fileName);
                    if (matcherCT.matches())
                    {
                        channelsHS.add(matcherCT.group("C"));
                        timepointsHS.add(matcherCT.group("T"));
                    }

                }

                // convert HashLists to sorted Lists

                channels = new ArrayList<String>(channelsHS);
                Collections.sort( channels );
                nC = channels.size();

                timepoints = new ArrayList<String>(timepointsHS);
                Collections.sort(timepoints);
                nT = timepoints.size();

            }

            //
            // Create dummy channel folders, if no real ones exist
            //
            if ( ! namingScheme.equals( BigDataProcessorUI.LOAD_CHANNELS_FROM_FOLDERS) )
            {
                imageDataInfo.channelFolders = new String[nC];
                for (int ic = 0; ic < nC; ic++) imageDataInfo.channelFolders[ic] = "";
            }


            // read nX,nY,nZ and bitdepth from first image
            //

            if ( fileLists[0][0].endsWith(".tif") )
            {
                setImageDataInfoFromTiff( imageDataInfo, directory + imageDataInfo.channelFolders[0], fileLists[0][0] );

                if ( namingScheme.equals( BigDataProcessorUI.EM_TIFF_SLICES ) )
                {
                    imageDataInfo.fileType = Utils.FileType.TIFF_PLANES.toString();
                    imageDataInfo.nZ = fileLists[ 0 ].length;
                }
                else
                {
                    imageDataInfo.fileType = Utils.FileType.TIFF_STACKS.toString();
                }
            }
            else if (fileLists[0][0].endsWith(".h5"))
            {
                setImageDataInfoFromH5(imageDataInfo, directory + imageDataInfo.channelFolders[0], fileLists[0][0], imageDataInfo.h5DataSetName);
                imageDataInfo.fileType = Utils.FileType.HDF5.toString();
            }
            else
            {
                IJ.showMessage("Unsupported file type: " + fileLists[0][0]);
                return false;
            }


            imageDataInfo.nT = nT;
            imageDataInfo.nC = nC;

            logger.info("File type: " + imageDataInfo.fileType );

            //
            // create the final file list
            //

            imageDataInfo.ctzFileList = new String[ imageDataInfo.nC ][ imageDataInfo.nT ][ imageDataInfo.nZ ];

            if ( hasCTPattern )
            {

                // no sub-folders
                // channel and t determined by pattern matching

                Pattern patternCT = Pattern.compile( namingScheme );

                for ( String fileName : fileLists[0] )
                {

                    Matcher matcherCT = patternCT.matcher( fileName );
                    if (matcherCT.matches())
                    {
                        try
                        {
                            c = channels.indexOf( matcherCT.group("C") );
                            t = timepoints.indexOf( matcherCT.group("T") );
                        }
                        catch (Exception e)
                        {
                            IJ.showMessage("The multi-channel loading did not match the filenames.\n" +
                                    "Please change the pattern.\n\n" +
                                    "The Java error message was:\n" +
                                    e.toString());
                            return false;
                        }
                    }

                    for (z = 0; z < imageDataInfo.nZ; z++)
                    {
                        imageDataInfo.ctzFileList[c][t][z] = fileName; // all z with same file-name, because it is stacks
                    }

                }

            }
            else
            {

                if ( namingScheme.equals( BigDataProcessorUI.EM_TIFF_SLICES ) )
                {
                    for ( z = 0; z < imageDataInfo.nZ; z++ )
                    {
                        imageDataInfo.ctzFileList[ 0 ][ 0 ][ z ] = fileLists[ 0 ][ z ];
                    }
                }
                else
                {
                    for ( c = 0; c < imageDataInfo.nC; c++ )
                    {
                        for ( t = 0; t < imageDataInfo.nT; t++ )
                        {
                            for ( z = 0; z < imageDataInfo.nZ; z++ )
                            {
                                imageDataInfo.ctzFileList[ c ][ t ][ z ] = fileLists[ c ][ t ]; // all z with same file-name, because it is stacks
                            }
                        }
                    }
                }

            }

        }

        return true;

    }

    private boolean isLeicaSinglePlaneTiffFileType( String[] fileList )
    {
        Pattern leicaPattern01 = Pattern.compile( "LightSheet .*" );
        Pattern leicaPattern02 = Pattern.compile( "DLS .*" );

        for ( String fileName : fileList )
        {
            if ( leicaPattern01.matcher( fileName ).matches() || leicaPattern02.matcher( fileName ).matches() )
            {
                logger.info( "Detected Leica single plane SPIM fileType." );
                return true;
            }
        }

        return false;
    }

    private boolean initLeicaSinglePlaneTiffData( ImageDataInfo imageDataInfo, String directory, String filterPattern, String[] fileList, int t, int z, int nC, int nZ )
    {
        int nT;
        int c;
        imageDataInfo.fileType = Utils.FileType.TIFF_PLANES.toString();

        //
        // Do special stuff related to Leica files
        //
        Matcher matcherZ, matcherC, matcherT, matcherID;
        Pattern patternC = Pattern.compile(".*--C(\\d+).*");
        Pattern patternZ = Pattern.compile(".*--Z(\\d+).*");
        Pattern patternT = Pattern.compile(".*--t(\\d+).*");
        Pattern patternID = Pattern.compile(".*?_(\\d+).*");

        if ( fileList.length == 0 )
        {
            IJ.showMessage("No files matching this pattern were found: " + filterPattern);
            return false;
        }

        // check which different fileIDs there are
        // those are three numbers after the first _
        // this happens due to restarting the imaging
        Set<String> fileIDset = new HashSet();

        for ( String fileName : fileList )
        {
            matcherID = patternID.matcher( fileName );
            if (matcherID.matches())
            {
                fileIDset.add( matcherID.group(1) );
            }
        }

        if( fileIDset.size() == 0 )
        {
            fileIDset.add( ".*" );
        }

        String[] fileIDs = fileIDset.toArray( new String[fileIDset.size()] );

        // check which different C, T and Z there are for each FileID

        ArrayList<HashSet<String>> channelsHS = new ArrayList();
        ArrayList<HashSet<String>> timepointsHS = new ArrayList();
        ArrayList<HashSet<String>> slicesHS = new ArrayList();

        //
        // Deal with different file-names (fileIDs) due to
        // series being restarted during the imaging
        //

        for ( String fileID : fileIDs )
        {
            channelsHS.add( new HashSet() );
            timepointsHS.add( new HashSet() );
            slicesHS.add( new HashSet() );
        }

        boolean hasMultiChannelNamingScheme = false;

        for (int iFileID = 0; iFileID < fileIDs.length; iFileID++)
        {
            Pattern patternFileID;

            if ( fileIDs[ iFileID ].equals( ".*" ) )
            {
                patternFileID = Pattern.compile( ".*" );
            }
            else
            {
                patternFileID = Pattern.compile(".*?_" + fileIDs[ iFileID ] + ".*");
            }

            for ( String fileName : fileList )
            {
                if ( patternFileID.matcher( fileName ).matches() )
                {
                    matcherC = patternC.matcher( fileName );

                    if ( matcherC.matches() )
                    {
                        // has multi-channels
                        hasMultiChannelNamingScheme = true;
                        channelsHS.get(iFileID).add( matcherC.group(1) );
                        matcherZ = patternZ.matcher( fileName );
                        if ( matcherZ.matches() )
                        {
                            slicesHS.get( iFileID ).add( matcherZ.group(1) );
                        }
                        else
                        {
                            slicesHS.get( iFileID ).add( "Z00" );
                        }
                    }
                    else
                    {
                        // has only one channel
                        matcherZ = patternZ.matcher(fileName);

                        if ( matcherZ.matches() )
                        {
                            slicesHS.get( iFileID ).add( matcherZ.group(1) );
                        }
                        else
                        {
                            slicesHS.get( iFileID ).add( "Z00" );
                        }

                    }

                    matcherT = patternT.matcher( fileName );

                    if ( matcherT.matches() )
                    {
                        timepointsHS.get( iFileID ).add( matcherT.group( 1 ) );
                    }
                    else
                    {
                        // has only one timepoint
                        timepointsHS.get( iFileID ).add( "T00" );
                    }
                }
            }

        }

        nT = 0;
        int[] tOffsets = new int[ fileIDs.length + 1 ]; // last offset is not used, but added anyway
        tOffsets[0] = 0;

        for (int iFileID = 0; iFileID < fileIDs.length; iFileID++)
        {

            nC = Math.max( 1, channelsHS.get(iFileID).size()) ;
            nZ = slicesHS.get( iFileID ).size(); // must be the same for all fileIDs

            logger.info("FileID: " + fileIDs[iFileID]);
            logger.info("  Channels: " + nC);
            logger.info("  TimePoints: " + timepointsHS.get( iFileID ).size());
            logger.info("  Slices: " + nZ);

            nT += timepointsHS.get( iFileID ).size();
            tOffsets[iFileID + 1] = nT;
        }


        //
        // Create dummy channel folders, because no real ones exist
        //

        imageDataInfo.channelFolders = new String[nC];
        for (int ic = 0; ic < nC; ic++) imageDataInfo.channelFolders[ic] = "";

        //
        // sort into the final file list
        //

        imageDataInfo.ctzFileList = new String[nC][nT][nZ];

        for (int iFileID = 0; iFileID < fileIDs.length; iFileID++)
        {

            Pattern patternFileID = Pattern.compile(".*" + fileIDs[iFileID] + ".*");

            for ( String fileName : fileList )
            {

                if ( patternFileID.matcher(fileName).matches() )
                {

                    // figure out which C,Z,T the file is
                    matcherC = patternC.matcher(fileName);
                    matcherT = patternT.matcher(fileName);
                    matcherZ = patternZ.matcher(fileName);

                    if ( matcherZ.matches() )
                    {
                        z = Integer.parseInt( matcherZ.group(1).toString() );
                    }
                    else
                    {
                        z = 0;
                    }

                    if ( matcherT.matches() )
                    {
                        t = Integer.parseInt( matcherT.group(1).toString() );
                        t += tOffsets[iFileID];
                    }
                    else
                    {
                        t = 0;
                    }

                    if ( matcherC.matches() )
                    {
                        if ( nC == 1 )
                        {
                            c = 0; // hack to also make it work in case the channel string is not "C00", but e.g. "C01"
                        }
                        else
                        {
                            c = Integer.parseInt(  matcherC.group(1).toString() );
                        }
                    }
                    else
                    {
                        c = 0;
                    }

                    imageDataInfo.ctzFileList[ c ][ t ][ z ] = fileName;

                }
            }
        }

        setImageDataInfoFromTiff(imageDataInfo, directory, imageDataInfo.ctzFileList[0][0][0]);
        imageDataInfo.nZ = nZ;
        imageDataInfo.nC = nC;
        imageDataInfo.nT = nT;

        return true;
    }


    public void setImageDataInfoFromH5(
            ImageDataInfo imageDataInfo,
            String directory,
            String fileName,
            String hdf5DataSet)
    {

        IHDF5Reader reader = HDF5Factory.openForReading( directory + "/" + fileName );

        if ( ! hdf5DataSetExists(reader, hdf5DataSet) ) return;

        HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation("/" + hdf5DataSet);

        if ( dsInfo.getDimensions().length == 3 )
        {
            imageDataInfo.nZ = (int) dsInfo.getDimensions()[0];
            imageDataInfo.nY = (int) dsInfo.getDimensions()[1];
            imageDataInfo.nX = (int) dsInfo.getDimensions()[2];
        }
        else if ( dsInfo.getDimensions().length == 2 )
        {
            imageDataInfo.nZ = 1;
            imageDataInfo.nY = (int) dsInfo.getDimensions()[0];
            imageDataInfo.nX = (int) dsInfo.getDimensions()[1];
        }

        imageDataInfo.bitDepth = assignHDF5TypeToImagePlusBitdepth(dsInfo);


    }

    public void setImageDataInfoFromTiff(
            ImageDataInfo imageDataInfo,
            String directory,
            String fileName)
    {
        FileInfoSer[] info;

        try
        {
            FastTiffDecoder ftd = new FastTiffDecoder(directory, fileName);
            info = ftd.getTiffInfo();
        }
        catch (Exception e)
        {
            info = null;
            IJ.showMessage("Error: " + e.toString());
        }

        if ( info[0].nImages > 1 )
        {
            imageDataInfo.nZ = info[0].nImages;
            info[0].nImages = 1;
        }
        else
        {
            imageDataInfo.nZ = info.length;
        }

        imageDataInfo.nX = info[0].width;
        imageDataInfo.nY = info[0].height;
        imageDataInfo.bitDepth = info[0].bytesPerPixel * 8;

    }

    public ImagePlus openFromInfoFile(String directory, String fileName)
    {

        File f = new File(directory + fileName);

        if (f.exists() && !f.isDirectory())
        {

            logger.info("Loading: " + directory + fileName);
            FileInfoSer[][][] infos = readFileInfosSer(directory + fileName);

            int nC = infos.length;
            int nT = infos[0].length;
            int nZ = infos[0][0].length;

            if (logger.isShowDebug())
            {
                logger.info("nC: " + infos.length);
                logger.info("nT: " + infos[0].length);
                logger.info("nz: " + infos[0][0].length);
            }

            // init the VS2
            VirtualStack2 stack = new VirtualStack2(directory, infos);
            ImagePlus imp = createImagePlusFromVS2(stack);
            return (imp);

        }
        else
        {
            return (null);
        }
    }

    private boolean hdf5DataSetExists( IHDF5Reader reader, String hdf5DataSet )
    {
        String dataSets = "";
        boolean dataSetExists = false;

        if ( reader.object().isDataSet( hdf5DataSet ) )
        {
            return true;
        }

        for ( String dataSet : reader.getGroupMembers("/") )
        {
            /*
            if (dataSet.equals(hdf5DataSet)) {
                dataSetExists = true;
            }
            */
            dataSets += "- " + dataSet + "\n";
        }

        if (  ! dataSetExists )
        {
            IJ.showMessage("The selected Hdf5 data set does not exist; " +
                    "please change to one of the following:\n\n" +
                    dataSets);
        }

        return dataSetExists;
    }

    String[] sortAndFilterFileList(String[] rawlist, String filterPattern)
    {
        int count = 0;

        Pattern patternFilter = Pattern.compile(filterPattern);

        for (int i = 0; i < rawlist.length; i++)
        {
            String name = rawlist[i];
            if (!patternFilter.matcher(name).matches())
                rawlist[i] = null;
            else if (name.endsWith(".tif") || name.endsWith(".h5"))
                count++;
            else
                rawlist[i] = null;


        }
        if (count == 0) return null;
        String[] list = rawlist;
        if (count < rawlist.length)
        {
            list = new String[count];
            int index = 0;
            for (int i = 0; i < rawlist.length; i++)
            {
                if (rawlist[i] != null)
                    list[index++] = rawlist[i];
            }
        }
        int listLength = list.length;
        boolean allSameLength = true;
        int len0 = list[0].length();
        for (int i = 0; i < listLength; i++)
        {
            if (list[i].length() != len0)
            {
                allSameLength = false;
                break;
            }
        }
        if (allSameLength)
        {
            ij.util.StringSorter.sort(list);
            return list;
        }
        int maxDigits = 15;
        String[] list2 = null;
        char ch;
        for (int i = 0; i < listLength; i++)
        {
            int len = list[i].length();
            String num = "";
            for (int j = 0; j < len; j++)
            {
                ch = list[i].charAt(j);
                if (ch >= 48 && ch <= 57) num += ch;
            }
            if (list2 == null) list2 = new String[listLength];
            if (num.length() == 0) num = "aaaaaa";
            num = "000000000000000" + num; // prepend maxDigits leading zeroes
            num = num.substring(num.length() - maxDigits);
            list2[i] = num + list[i];
        }
        if (list2 != null)
        {
            ij.util.StringSorter.sort(list2);
            for (int i = 0; i < listLength; i++)
                list2[i] = list2[i].substring(maxDigits);
            return list2;
        }
        else
        {
            ij.util.StringSorter.sort(list);
            return list;
        }
    }

    String[] getFilesInFolder(String directory, String filterPattern)
    {
        // TODO: can getting the file-list be faster?

//        Path folder = Paths.get( directory );
//        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
//            for (Path entry : stream) {
//                 Process the entry
//            }
//        } catch (IOException ex) {
//             An I/O problem has occurred
//        }

        String[] list = new File( directory ).list();


        if (list == null || list.length == 0)
            return null;

        logger.info( "Sorting and filtering file list..." );
        list = this.sortAndFilterFileList( list, filterPattern );

        if (list == null) return null;

        else return ( list );
    }

    String[] getFoldersInFolder(String directory)
    {
        //info("# getFoldersInFolder: " + directory);
        String[] list = new File(directory).list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name)
            {
                return new File(current, name).isDirectory();
            }
        });
        if (list == null || list.length == 0)
            return null;
        //list = this.sortFileList(list);
        return (list);

    }

    public boolean writeFileInfosSer(FileInfoSer[][][] infos, String path)
    {
        try
        {
            FileOutputStream fout = new FileOutputStream(path, true);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(infos);
            oos.flush();
            oos.close();
            fout.close();
            logger.info("Wrote: " + path);
            return true;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            logger.error("Writing failed: " + path);
            return false;
        }

    }

    public FileInfoSer[][][] readFileInfosSer(String path)
    {
        try
        {
            FileInputStream fis = new FileInputStream(path);
            ObjectInputStream ois = new ObjectInputStream(fis);
            FileInfoSer infos[][][] = (FileInfoSer[][][]) ois.readObject();
            ois.close();
            fis.close();
            return (infos);
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }

    }

    public static ImagePlus getCroppedVS2(ImagePlus imp, Roi roi, int zMin, int zMax, int tMin, int tMax)
    {

        int nt = tMax - tMin + 1;

        Point3D[] po = new Point3D[ nt ];
        Point3D ps = null;

        if (roi != null && roi.isArea())
        {
            for ( int t = 0; t < nt; t++ )
            {
                po[t] = new Point3D(roi.getBounds().getX(), roi.getBounds().getY(), zMin);
            }
            ps = new Point3D(roi.getBounds().getWidth(), roi.getBounds().getHeight(), zMax - zMin + 1);
        }
        else
        {
            logger.warning("No area ROI provided => no cropping in XY.");

            for ( int t = 0; t < nt; t++ )
            {
                po[t] = new Point3D(0, 0, zMin - 1);
            }
            ps = new Point3D(imp.getWidth(), imp.getHeight(), zMax - zMin + 1);

        }

        // Crop
        //
        ImagePlus impCropped = getCroppedVS2( imp, po, ps, tMin, tMax );
        impCropped.setTitle( imp.getTitle() + "-crop" );
        return impCropped;

    }

    public static ImagePlus getCroppedVS2( ImagePlus imp, Point3D[] po, Point3D ps, int tMin, int tMax )
    {

        VirtualStack2 vs2 = (VirtualStack2 ) imp.getStack();

        FileInfoSer[][][] infos = vs2.getFileInfosSer();

        FileInfoSer[][][] croppedInfos = getCroppedFileInfos( po, ps, tMin, tMax, vs2, infos );

        VirtualStack2 croppedStack = createNewVS2FromCroppingInfos( imp, vs2, croppedInfos );

        return createImagePlusFromVS2( croppedStack );

    }

    private static FileInfoSer[][][] getCroppedFileInfos( Point3D[] po, Point3D ps, int tMin, int tMax, VirtualStack2 vs2, FileInfoSer[][][] infos )
    {
        int nC = infos.length;
        int nT = tMax - tMin + 1;
        int nZ = infos[0][0].length;

        FileInfoSer[][][] croppedInfos = new FileInfoSer[nC][nT][nZ];

        if (logger.isShowDebug())
        {
            logger.info("# DataStreamingTools.openCroppedFromInfos");
            logger.info("tMin: " + tMin);
            logger.info("tMax: " + tMax);
        }

        for (int c = 0; c < nC; c++)
        {
            for (int t = tMin; t <= tMax; t++)
            {
                for (int z = 0; z < nZ; z++)
                {
                    if ( infos[ c ][ t ][ z ] == null )
                    {
                        // for streaming from single tiffs it can be
                        // that some planes are not parsed yet
                        vs2.setInfoFromFile( c, t, z );
                        logger.progress("Parsing file header: ", "c" + c + " t" + t + " z" + z);
                    }
                    croppedInfos[c][t-tMin][z] = new FileInfoSer( infos[c][t][z] );
                    if (croppedInfos[c][t-tMin][z].isCropped)
                    {
                        Point3D originalCropOffset = croppedInfos[c][t-tMin][z].getCropOffset();
                        Point3D additionalOffset = po[ t - tMin ];
                        Point3D adaptedCropOffset = originalCropOffset.add( additionalOffset );
                        croppedInfos[c][t-tMin][z].setCropOffset( adaptedCropOffset );
                    }
                    else
                    {
                        croppedInfos[c][t-tMin][z].isCropped = true;
                        croppedInfos[c][t-tMin][z].setCropOffset( po[ t - tMin ] );
                    }
                    croppedInfos[c][t-tMin][z].setCropSize(ps);
                    //info("channel "+channel);
                    //info("t "+t);
                    //info("z "+z);
                    //info("offset "+croppedInfos[channel][t-tMin][z].pCropOffset.toString());
                }
            }
        }
        return croppedInfos;
    }

    private static VirtualStack2 createNewVS2FromCroppingInfos( ImagePlus imp, VirtualStack2 vs2, FileInfoSer[][][] croppedInfos )
    {
        VirtualStack2 parentStack = (VirtualStack2 ) imp.getStack();
        VirtualStack2 stack = new VirtualStack2( parentStack.getDirectory(), croppedInfos );
        stack.setChromaticShifts( vs2.getChromaticShiftsCopy() );
        // NN: add stack.setShearingSetting( vs2.getShearingSettings() );
        return stack;
    }

    private static ImagePlus createImagePlusFromVS2( VirtualStack2 stack )
    {
        int nC = stack.getChannels();
        int nZ = stack.getDepth();
        int nT = stack.getFrames();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        ImagePlus imp = new ImagePlus( new File( stack.getDirectory() ).getName() , stack);

        // todo: what does this do?
        //if (imp.getType() == ImagePlus.GRAY16 || imp.getType() == ImagePlus.GRAY32)
        //    imp.getProcessor().setMinAndMax(min, max);

        // TODO: this needs be checked!
        //imp.setFileInfo(fi.getFileInfo()); // saves FileInfo of the first image

        if (logger.isShowDebug())
        {
            logger.info("# DataStreamingTools.createImagePlusFromVS2");
            logger.info("nC: " + nC);
            logger.info("nZ: " + nZ);
            logger.info("nT: " + nT);
        }

        imp.setDimensions(nC, nZ, nT);
        imp.setOpenAsHyperStack(true);
        return (imp);
    }

    /**
     * @param imp
     * @param nIOthreads
     * @return
     */
    public static ImagePlus loadVS2FullyIntoRAM(ImagePlus imp, int nIOthreads)
    {

        // Initialize RAM image
        //
        ImageStack stack = imp.getStack();
        ImageStack stackRAM = ImageStack.create(stack.getWidth(), stack.getHeight(), stack.getSize(),
                stack.getBitDepth());
        ImagePlus impRAM = new ImagePlus("RAM", stackRAM);
        int[] dim = imp.getDimensions();
        impRAM.setDimensions(dim[2], dim[3], dim[4]);

        // Multi-threaded loading into RAM (increases speed if SSDs are available)
        //
        ExecutorService es = Executors.newFixedThreadPool(nIOthreads);
        List<Future> futures = new ArrayList<>();
        for (int t = 0; t < imp.getNFrames(); t++)
        {
            futures.add(es.submit(new LoadFrameFromVS2IntoRAM(imp, t, impRAM, nIOthreads)));
        }


        Thread thread = new Thread(new Runnable() {
            public void run()
            {
                MonitorThreadPoolStatus.showProgressAndWaitUntilDone(
                        futures,
                        "Loaded into RAM: ",
                        2000);
            }
        });
        thread.start();


        String info = (String) imp.getProperty("Info");
        if (info != null)
            impRAM.setProperty("Info", info);
        if (imp.isHyperStack())
            impRAM.setOpenAsHyperStack(true);

        return (impRAM);

    }

    public void saveAsTiffPlanes( SavingSettings savingSettings )
    {

        interruptSavingThreads = false;

        // Do the jobs
        //
        ExecutorService es = Executors.newFixedThreadPool( savingSettings.nThreads );
        List<Future> futures = new ArrayList<>();
        for ( int c = 0; c < savingSettings.imp.getNChannels(); c++)
        {
            for (int t = 0; t < savingSettings.imp.getNFrames(); t++)
            {
                for (int z = 0; z < savingSettings.imp.getNSlices(); z++)
                {
                    futures.add( es.submit( new SaveVS2TiffPlane(this, c, t, z, savingSettings) ) );
                }
            }
        }

        // Monitor the progress
        //
        Thread thread = new Thread(new Runnable() {
            public void run()
            {
                MonitorThreadPoolStatus.showProgressAndWaitUntilDone(
                        futures,
                        "Saved to disk: ",
                        2000);
            }
        });
        thread.start();


    }

    public void saveAsStacks( SavingSettings savingSettings )
    {
        Utils.createFilePathParentDirectories( savingSettings.filePath );

        interruptSavingThreads = false;

        int numSavingThreads = getNumSavingThreads( savingSettings );

        if ( savingSettings.fileType.equals( Utils.FileType.IMARIS ) )
            saveFilesForEachChannelAndTimePoint(
                    savingSettings,
                    numSavingThreads,
                    getImarisDataSet( savingSettings ) );
        else
            saveFilesForEachChannelAndTimePoint(
                    savingSettings,
                    numSavingThreads,
                    null );

    }

    private void saveFilesForEachChannelAndTimePoint(
            SavingSettings savingSettings,
            int numSavingThreads,
            ImarisDataSet imarisDataSet )
    {
        // Save individual files for each channel and time-point
        //
        ExecutorService es = Executors.newFixedThreadPool( numSavingThreads );
        List<Future> futures = new ArrayList<>();

        AtomicInteger counter = new AtomicInteger(0);
        final long startTime = System.currentTimeMillis();

        for (int t = 0; t < savingSettings.imp.getNFrames(); t++)
        {
            futures.add(
                    es.submit(
                        new SaveVS2Frame( this,
                            t,
                            savingSettings,
                            imarisDataSet,
                            counter,
                            startTime) ) );
        }
    }

    private ImarisDataSet getImarisDataSet( SavingSettings savingSettings )
    {
        String[] binnings = savingSettings.bin.split(";");
        int[] binning = Utils.delimitedStringToIntegerArray( binnings[0], ",");

        ImarisDataSet imarisDataSet = new ImarisDataSet( savingSettings.imp,
                    binning,
                    savingSettings.directory,
                    savingSettings.fileBaseName );

        imarisDataSet.setLogger( new de.embl.cba.logging.IJLazySwingLogger() );

        ImarisWriter.writeHeaderFile( imarisDataSet,
                savingSettings.directory,
                savingSettings.fileBaseName + ".ims"
                );

        ArrayList< File > imarisFiles = ImarisUtils.getImarisFiles( savingSettings.directory );

        if ( imarisFiles.size() > 1 )
        {
            ImarisWriter.writeCombinedHeaderFile( imarisFiles, "meta.ims" );
        }

        logger.info("Image sizes at different resolutions:");
        Utils.logArrayList( imarisDataSet.getDimensions() );

        logger.info("Image chunking:");
        Utils.logArrayList( imarisDataSet.getChunks() );

        return imarisDataSet;

    }

    private int getNumSavingThreads( SavingSettings savingSettings )
    {
        int nSavingThreads = savingSettings.nThreads;

        if ( savingSettings.fileType.equals( Utils.FileType.HDF5 ) || savingSettings.fileType.equals( Utils.FileType.IMARIS ) )
        {
            nSavingThreads = 1; // H5 is not multi-threaded anyway.
        }
        return nSavingThreads;
    }

    public void cancelSaving()
    {
         logger.info("Stopping all saving threads...");
         interruptSavingThreads = true;
    }

    /**
     * for stack-based files this will parse the information
     * of all z-planes even if only z = 0 is specified
      */
    class ParseFilesIntoVirtualStack implements Runnable {
        ImagePlus imp;
        private final int t, z;
        private final boolean showImage;
        private boolean logFileNotExistsError;

        ParseFilesIntoVirtualStack(ImagePlus imp, int t, int z,
                                   boolean isShowImage, boolean logFileNotExistsError)
        {
            this.imp = imp;
            this.t = t;
            this.z = z;
            this.showImage = isShowImage;
            this.logFileNotExistsError = logFileNotExistsError;

        }

        public void run()
        {

            VirtualStack2 vs2 = (VirtualStack2 ) imp.getStack();

            int nC = vs2.getChannels();

            for ( int c = 0; c < nC; c++ )
            {
                vs2.setInfoFromFile( c, t, z, logFileNotExistsError );
            }

            if ( t == 0 && z == 0 && showImage)
            {
                showImageAndInfo( vs2 );
            }

        }

        public void showImageAndInfo(VirtualStack2 vs2)
        {
            // show image
            //
            if (vs2 != null && vs2.getSize() > 0)
            {
                imp = createImagePlusFromVS2( vs2 );
            }
            else
            {
                logger.error("Something went wrong loading the first image stack!");
                return;
            }

            Utils.show( imp );
            imp.setTitle("stream"); // TODO: get the selected directory as image name

            // show compression info
            //
            FileInfoSer[][][] infos = vs2.getFileInfosSer();
            if ( infos != null && infos[0][0][0] != null )
            {
                FileInfoSer fi0 = infos[0][0][0];
                if (fi0.compression == 0)
                    logger.info("Compression = Unknown");
                else if (fi0.compression == 1)
                    logger.info("Compression = None");
                else if (fi0.compression == 2)
                    logger.info("Compression = LZW");
                else if (fi0.compression == 6)
                    logger.info("Compression = ZIP");
                else
                    logger.info("Compression = " + fi0.compression);

                logger.info("Bit depth = " + (fi0.bytesPerPixel * 8));
                logger.info("File type = " + (fi0.fileType));

                if (fi0.stripLengths != null)
                {
                    logger.info("Tiff: Number of strips  = " + (fi0.stripLengths.length));
                    logger.info("Tiff: Rows per strip  = " + ( fi0.rowsPerStrip ));
                }
            }
        }
    }

    static int assignHDF5TypeToImagePlusBitdepth(HDF5DataSetInformation dsInfo)
    {

        String type = dsInfoToTypeString( dsInfo );

        int nBits = 0;
        if (type.equals("uint8"))
        {
            nBits = 8;

        }
        else if (type.equals("uint16") || type.equals("int16"))
        {
            nBits = 16;
        }
        else if (type.equals("float32") || type.equals("float64"))
        {
            nBits = 32;
        }
        else
        {
            IJ.error("Type '" + type + "' Not handled yet!");
        }
        return nBits;
    }


    static String dsInfoToTypeString(HDF5DataSetInformation dsInfo)
    {
        HDF5DataTypeInformation dsType = dsInfo.getTypeInformation();
        String typeText = "";

        if (dsType.isSigned() == false)
        {
            typeText += "u";
        }

        switch (dsType.getDataClass())
        {
            case INTEGER:
                typeText += "int" + 8 * dsType.getElementSize();
                break;
            case FLOAT:
                typeText += "float" + 8 * dsType.getElementSize();
                break;
            default:
                typeText += dsInfo.toString();
        }
        return typeText;
    }

}

    /*
    public < T extends RealType< T > & NativeType< T > >
            void openUsingSCIFIO(String path)
            throws ImgIOException
    {
        // Mouse over: intensity?

        //
        // Test SCIFIO and BIGDATAVIEWER
        //

        ImgOpener imgOpener = new ImgOpener();

        // Open as ArrayImg
        java.util.List<SCIFIOImgPlus<?>> imgs = imgOpener.openImgs( path );
        Img<T> img = (Img<T>) imgs.get(0);
        BdvSource bdv = BdvFunctions.show(img, "RAM");
        bdv.setDisplayRange(0,1000);

        // Open as CellImg
        SCIFIOConfig config = new SCIFIOConfig();
        config.imgOpenerSetImgModes( SCIFIOConfig.ImgMode.CELL );
        java.util.List<SCIFIOImgPlus<?>> cellImgs = imgOpener.openImgs( path, config );
        Img<T> cellImg = (Img<T>) cellImgs.get(0);
        BdvSource bdv2 = BdvFunctions.show(cellImg, "STREAM");
        bdv2.setDisplayRange(0,1000);

        /*
        First of all it is awesome that all of that works!
        As to be expected the visualisation of the of the cellImg is much slower.
        To be honest I was a bit surprised as to how slow it is given that it is only 2.8 MB and
        was stored on SSD on my MacBook Air.
        I was wondering about a really simple caching strategy for the BDV, in pseudo code:

        t = timePointToBeDisplayed
        taskRender(cellImg(t)).start()
        if(cellImg(t) fits in RAM):
          arrayImgThisTimePoint = cellImg(t).loadAsArrayImg()
          taskRender(cellImg(t)).end()
          taskRender(arrayImgThisTimePoint).start()

        Would that make any sense?
    }
       */

