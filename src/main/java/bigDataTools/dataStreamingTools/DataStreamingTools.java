/*
 * #%L
 * Data streaming, tracking and cropping tools
 * %%
 * Copyright (C) 2017 Christian Tischer
 *
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */



/*
- This code contains modified parts of the HDF5 plugin from the Ronneberger group in Freiburg
 */

package bigDataTools.dataStreamingTools;

//import bdv.util.BdvFunctions;
//import bdv.util.BdvSource;
//import io.scif.config.SCIFIOConfig;
//import io.scif.img.ImgIOException;
//import io.scif.img.SCIFIOImgPlus;
//import net.imglib2.img.Img;
//import net.imglib2.type.NativeType;
//import net.imglib2.type.numeric.RealType;
//import net.imglib2.type.numeric.integer.UnsignedShortType;
//import net.imglib2.util.Pair;
//import io.scif.img.ImgOpener;
//import net.imglib2.img.display.imagej.ImageJFunctions;
//import org.scijava.util.Bytes;


import bigDataTools.Hdf55ImarisBdvWriter;
import bigDataTools.VirtualStackOfStacks.*;
import bigDataTools.bigDataTracker.BigDataTrackerPlugIn_;
import bigDataTools.logging.IJLazySwingLogger;
import bigDataTools.logging.Logger;
import bigDataTools.utils.ImageDataInfo;
import bigDataTools.utils.MonitorThreadPoolStatus;
import bigDataTools.utils.Utils;
import ch.systemsx.cisd.hdf5.*;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import javafx.geometry.Point3D;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



//import net.imagej.ImageJ;

// todo: put filename as slice label!!
// todo: test
// todo: brightness and contrast adjustment upon loading does not work
// todo: can only some combobox fields be editable?
// todo: - find out why loading and saving info file is so slow
// todo: - save smaller info files
// todo: saving as tiff stacks does not always work, e.g. after object tracking
// todo: check if all files are parsed before allowing to "crop as new stream"
// todo: rearrange the GUI
// todo: consistency check the list lengths with different folders
// todo: stop loading thread upon closing of image
// todo: increase speed of Leica tif parsing, possible?
// todo: make 8 bit work

/**
 * Opens a folder of stacks as a virtual stack.
 *
 * */
public class DataStreamingTools {

    private static Logger logger = new IJLazySwingLogger();
    public boolean interruptSavingThreads = false;

    public DataStreamingTools()
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

        if ( namingScheme.contains("<Z") )
        {
            // TODO: change below logic somehow (maybe via GUI?)
            partialDataSet = true;

            if ( ! setMissingInfos(
                    imageDataInfo,
                    directory,
                    namingScheme))
            {
                return null;
            }
        }
        else
        {
            imageDataInfo = new ImageDataInfo();
            imageDataInfo.h5DataSetName = h5DataSetName;

            setAllInfosByParsingFilesAndFolders(
                    imageDataInfo,
                    directory,
                    namingScheme,
                    filterPattern
            );
        }


        //
        // init the virtual stack
        //
        VirtualStackOfStacks stack = new VirtualStackOfStacks(
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

        ImagePlus imp = new ImagePlus("stream", stack);


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
                if ( imageDataInfo.fileType.equals( Utils.FileType.SINGLE_PLANE_TIFF.toString() ) )
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
                            "Parsed time-points: ",
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

        logger.info(
                "Importing/creating file information from pre-defined naming scheme."
        );

        // channels
        matcher = Pattern.compile(".*<C(\\d+)-(\\d+)>.*").matcher( namingPattern );
        if ( matcher.matches() )
        {
            hasC = true;
            ctzMin[0] = Integer.parseInt( matcher.group(1) );
            ctzMax[0] = Integer.parseInt(matcher.group(2));
            ctzPad[0] = matcher.group(1).length();
        }
        else
        {
            ctzMin[0] = ctzMax[0] = ctzPad[0] = 0;
        }

        imageDataInfo.channelFolders = new String[ctzMax[0] - ctzMin[0] +1];
        Arrays.fill(imageDataInfo.channelFolders, "");

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
            imageDataInfo.fileType = Utils.FileType.SINGLE_PLANE_TIFF.toString();
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

                    if (imageDataInfo.fileType.equals( Utils.FileType.SINGLE_PLANE_TIFF.toString()) )
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

                            if ( imageDataInfo.fileType.equals( Utils.FileType.SINGLE_PLANE_TIFF.toString() ))
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



    public void setAllInfosByParsingFilesAndFolders(
            ImageDataInfo imageDataInfo,
            String directory,
            String channelTimePattern,
            String filterPattern
    )
    {

        String[][] fileLists; // files in sub-folders
        int t = 0, z = 0, c = 0;
        String fileType = "not determined";
        FileInfoSer[] info;
        FileInfoSer fi0;
        List<String> channels = null, timepoints = null;

        int nC = 0, nT = 0, nZ = 0, nX = 0, nY = 0, bitDepth = 16;

        if (channelTimePattern.equals(Utils.LOAD_CHANNELS_FROM_FOLDERS))
        {
            //
            // Check for sub-folders
            //
            logger.info("checking for sub-folders...");
            imageDataInfo.channelFolders = getFoldersInFolder(directory);
            if (imageDataInfo.channelFolders != null)
            {
                fileLists = new String[imageDataInfo.channelFolders.length][];
                for (int i = 0; i < imageDataInfo.channelFolders.length; i++)
                {
                    fileLists[i] = getFilesInFolder(directory + imageDataInfo.channelFolders[i], filterPattern);
                    if (fileLists[i] == null)
                    {
                        logger.info("no files found in folder: " + directory + imageDataInfo.channelFolders[i]);
                        return;
                    }
                }
                logger.info("found sub-folders => interpreting as channel folders.");
            }
            else
            {
                logger.info("no sub-folders found.");
                IJ.showMessage("No sub-folders found; please specify a different options for loading " +
                        "the channels");
                return;
            }

        }
        else
        {

            //
            // Get files in main directory
            //

            logger.info("Searching files in folder: " + directory);
            fileLists = new String[1][];
            fileLists[0] = getFilesInFolder(directory, filterPattern);

            if (fileLists[0] != null)
            {

                //
                // check if it is Leica single tiff SPIM files
                //

                Pattern patternLeica = Pattern.compile("LightSheet 2.*");
                for (String fileName : fileLists[0])
                {
                    if (patternLeica.matcher(fileName).matches())
                    {
                        fileType = Utils.FileType.SINGLE_PLANE_TIFF.toString();
                        logger.info("detected fileType: " + fileType);
                        break;
                    }
                }
            }

            if (fileLists[0] == null || fileLists[0].length == 0)
            {
                IJ.showMessage("No files matching this pattern were found: " + filterPattern);
                return;
            }

        }

        //
        // generate a nC,nT,nZ fileList
        //

        if ( fileType.equals( Utils.FileType.SINGLE_PLANE_TIFF.toString() ) )
        {
            imageDataInfo.fileType = Utils.FileType.SINGLE_PLANE_TIFF.toString();

            //
            // Do special stuff related to leica single files
            //

            Matcher matcherZ, matcherC, matcherT, matcherID;
            Pattern patternC = Pattern.compile(".*--C(.*).tif");
            Pattern patternZnoC = Pattern.compile(".*--Z(.*).tif");
            Pattern patternZwithC = Pattern.compile(".*--Z(.*)--C.*");
            Pattern patternT = Pattern.compile(".*--t(.*)--Z.*");
            Pattern patternID = Pattern.compile(".*?_(\\d+).*"); // is this correct?

            if (fileLists[0].length == 0)
            {
                IJ.showMessage("No files matching this pattern were found: " + filterPattern);
                return;
            }

            // check which different fileIDs there are
            // those are three numbers after the first _
            // this happens due to restarting the imaging
            Set<String> fileIDset = new HashSet();
            for (String fileName : fileLists[0])
            {
                matcherID = patternID.matcher(fileName);
                if (matcherID.matches())
                {
                    fileIDset.add(matcherID.group(1));
                }
            }
            String[] fileIDs = fileIDset.toArray(new String[fileIDset.size()]);

            // check which different C, T and Z there are for each FileID

            ArrayList<HashSet<String>> channelsHS = new ArrayList();
            ArrayList<HashSet<String>> timepointsHS = new ArrayList();
            ArrayList<HashSet<String>> slicesHS = new ArrayList();

            //
            // Deal with different file-names (fileIDs) due to
            // series being restarted during the imaging
            //

            for (String fileID : fileIDs)
            {
                channelsHS.add(new HashSet());
                timepointsHS.add(new HashSet());
                slicesHS.add(new HashSet());
            }

            for (int iFileID = 0; iFileID < fileIDs.length; iFileID++)
            {

                Pattern patternFileID = Pattern.compile(".*?_" + fileIDs[iFileID] + ".*");

                for (String fileName : fileLists[0])
                {

                    if (patternFileID.matcher(fileName).matches())
                    {

                        matcherC = patternC.matcher(fileName);
                        if (matcherC.matches())
                        {
                            // has multi-channels
                            channelsHS.get(iFileID).add(matcherC.group(1));
                            matcherZ = patternZwithC.matcher(fileName);
                            if (matcherZ.matches())
                            {
                                slicesHS.get(iFileID).add(matcherZ.group(1));
                            }
                        }
                        else
                        {
                            // has only one channel
                            matcherZ = patternZnoC.matcher(fileName);
                            if (matcherZ.matches())
                            {
                                slicesHS.get(iFileID).add(matcherZ.group(1));
                            }
                        }

                        matcherT = patternT.matcher(fileName);
                        if (matcherT.matches())
                        {
                            timepointsHS.get(iFileID).add(matcherT.group(1));
                        }
                    }
                }

            }

            nT = 0;
            int[] tOffsets = new int[fileIDs.length + 1]; // last offset is not used, but added anyway
            tOffsets[0] = 0;

            for (int iFileID = 0; iFileID < fileIDs.length; iFileID++)
            {

                nC = Math.max(1, channelsHS.get(iFileID).size());
                nZ = slicesHS.get(iFileID).size(); // must be the same for all fileIDs

                logger.info("FileID: " + fileIDs[iFileID]);
                logger.info("  Channels: " + nC);
                logger.info("  TimePoints: " + timepointsHS.get(iFileID).size());
                logger.info("  Slices: " + nZ);

                nT += timepointsHS.get(iFileID).size();
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

                for (String fileName : fileLists[0])
                {

                    if (patternFileID.matcher(fileName).matches())
                    {

                        // figure out which C,Z,T the file is
                        matcherC = patternC.matcher(fileName);
                        matcherT = patternT.matcher(fileName);
                        if (nC > 1) matcherZ = patternZwithC.matcher(fileName);
                        else matcherZ = patternZnoC.matcher(fileName);

                        if (matcherZ.matches())
                        {
                            z = Integer.parseInt(matcherZ.group(1).toString());
                        }
                        if (matcherT.matches())
                        {
                            t = Integer.parseInt(matcherT.group(1).toString());
                            t += tOffsets[iFileID];
                        }
                        if (matcherC.matches())
                        {
                            c = Integer.parseInt(matcherC.group(1).toString());
                        }
                        else
                        {
                            c = 0;
                        }

                        imageDataInfo.ctzFileList[c][t][z] = fileName;

                    }
                }
            }

            setImageDataInfoFromTiff(imageDataInfo, directory + imageDataInfo.channelFolders[0], imageDataInfo.ctzFileList[0][0][0]);
            imageDataInfo.nZ = nZ;
            imageDataInfo.nC = nC;
            imageDataInfo.nT = nT;
        }
        else // tif stacks or h5 stacks
        {
            boolean hasCTPattern = false;

            if (channelTimePattern.equals(Utils.LOAD_CHANNELS_FROM_FOLDERS))
            {

                nC = imageDataInfo.channelFolders.length;
                nT = fileLists[0].length;

            }
            else if (channelTimePattern.equals("None"))
            {

                nC = 1;
                nT = fileLists[0].length;

            }
            else
            {

                hasCTPattern = true;

                if (!(channelTimePattern.contains("<c>") && channelTimePattern.contains("<t>")))
                {
                    IJ.showMessage("The pattern for multi-channel loading must" +
                            "contain <c> and <t> to match channels and time in the filenames.");
                    return;
                }

                // replace shortcuts by actual regexp
                channelTimePattern = channelTimePattern.replace("<c>", "(?<C>.*)");
                channelTimePattern = channelTimePattern.replace("<t>", "(?<T>.*)");

                imageDataInfo.channelFolders = new String[]{""};

                HashSet<String> channelsHS = new HashSet();
                HashSet<String> timepointsHS = new HashSet();

                Pattern patternCT = Pattern.compile(channelTimePattern);

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
                Collections.sort(channels);
                nC = channels.size();

                timepoints = new ArrayList<String>(timepointsHS);
                Collections.sort(timepoints);
                nT = timepoints.size();

            }

            //
            // Create dummy channel folders, if no real ones exist
            //
            if (!channelTimePattern.equals(Utils.LOAD_CHANNELS_FROM_FOLDERS))
            {
                imageDataInfo.channelFolders = new String[nC];
                for (int ic = 0; ic < nC; ic++) imageDataInfo.channelFolders[ic] = "";
            }


            // read nX,nY,nZ and bitdepth from first stack
            //

            if (fileLists[0][0].endsWith(".tif"))
            {
                setImageDataInfoFromTiff(imageDataInfo, directory + imageDataInfo.channelFolders[0], fileLists[0][0]);
                imageDataInfo.fileType = Utils.FileType.TIFF_STACKS.toString();
            }
            else if (fileLists[0][0].endsWith(".h5"))
            {
                setImageDataInfoFromH5(imageDataInfo, directory + imageDataInfo.channelFolders[0], fileLists[0][0], imageDataInfo.h5DataSetName);
                imageDataInfo.fileType = Utils.FileType.HDF5.toString();
            }
            else
            {
                IJ.showMessage("Unsupported file type: " + fileLists[0][0]);
                return;
            }


            imageDataInfo.nT = nT;
            imageDataInfo.nC = nC;

            logger.info("File type: " + imageDataInfo.fileType );

            //
            // create the final file list
            //

            imageDataInfo.ctzFileList = new String[imageDataInfo.nC][imageDataInfo.nT][imageDataInfo.nZ];

            if (hasCTPattern)
            {

                // no sub-folders
                // channel and t determined by pattern matching

                Pattern patternCT = Pattern.compile(channelTimePattern);

                for (String fileName : fileLists[0])
                {

                    Matcher matcherCT = patternCT.matcher(fileName);
                    if (matcherCT.matches())
                    {
                        try
                        {
                            c = channels.indexOf(matcherCT.group("C"));
                            t = timepoints.indexOf(matcherCT.group("T"));
                        } catch (Exception e)
                        {
                            IJ.showMessage("The multi-channel loading did not match the filenames.\n" +
                                    "Please change the pattern.\n\n" +
                                    "The Java error message was:\n" +
                                    e.toString());
                            return;
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

                for (c = 0; c < imageDataInfo.nC; c++)
                {
                    for (t = 0; t < imageDataInfo.nT; t++)
                    {
                        for (z = 0; z < imageDataInfo.nZ; z++)
                        {
                            imageDataInfo.ctzFileList[c][t][z] = fileLists[c][t]; // all z with same file-name, because it is stacks
                        }
                    }
                }

            }

        }

    }


    public void setImageDataInfoFromH5(
            ImageDataInfo imageDataInfo,
            String directory,
            String fileName,
            String hdf5DataSet)
    {

        IHDF5Reader reader = HDF5Factory.openForReading(directory + "/" + fileName);

        if (!hdf5DataSetExists(reader, hdf5DataSet)) return;

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

        if (info[0].nImages > 1)
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

            // init the VSS
            VirtualStackOfStacks stack = new VirtualStackOfStacks(directory, infos);
            ImagePlus imp = createImagePlusFromVSS(stack);
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
        // todo: can getting the file-list be faster?
        String[] list = new File(directory).list();
        if (list == null || list.length == 0)
            return null;
        list = this.sortAndFilterFileList(list, filterPattern);
        if (list == null) return null;
        else return (list);
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

    public static ImagePlus getCroppedVSS(ImagePlus imp, Roi roi, int zMin, int zMax, int tMin, int tMax)
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
        ImagePlus impCropped = getCroppedVSS(imp, po, ps, tMin, tMax);
        impCropped.setTitle(imp.getTitle() + "-crop");
        return impCropped;

    }

    public static ImagePlus getCroppedVSS(ImagePlus imp, Point3D[] po, Point3D ps, int tMin, int tMax)
    {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();

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
                    if ( infos[c][t][z] == null )
                    {
                        // for streaming from single tiffs it can be
                        // that some planes are not parsed yet
                        vss.setInfoFromFile(c, t, z);
                        logger.progress("Parsing file header: ", "c"+c+" t"+t+" z"+z);
                    }
                    croppedInfos[c][t-tMin][z] = new FileInfoSer( infos[c][t][z] );
                    if (croppedInfos[c][t-tMin][z].isCropped)
                    {
                        Point3D originalCropOffset = croppedInfos[c][t-tMin][z].getCropOffset();
                        Point3D additionalOffset = po[t-tMin];
                        Point3D adaptedCropOffset = originalCropOffset.add( additionalOffset );
                        croppedInfos[c][t-tMin][z].setCropOffset( adaptedCropOffset );
                    }
                    else
                    {
                        croppedInfos[c][t-tMin][z].isCropped = true;
                        croppedInfos[c][t-tMin][z].setCropOffset(po[t-tMin]);
                    }
                    croppedInfos[c][t-tMin][z].setCropSize(ps);
                    //info("channel "+channel);
                    //info("t "+t);
                    //info("z "+z);
                    //info("offset "+croppedInfos[channel][t-tMin][z].pCropOffset.toString());

                }

            }

        }

        VirtualStackOfStacks parentStack = (VirtualStackOfStacks) imp.getStack();
        VirtualStackOfStacks stack = new VirtualStackOfStacks( parentStack.getDirectory(), croppedInfos );
        return createImagePlusFromVSS( stack );

    }

    // TODO: is this method needed?
    private static ImagePlus createImagePlusFromVSS(VirtualStackOfStacks stack)
    {
        int nC = stack.getChannels();
        int nZ = stack.getDepth();
        int nT = stack.getFrames();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        // For an only partly existing stack we don't have this info
        // TODO: maybe set up on reference FileInfoSer?
        //FileInfoSer[][][] infos = stack.getFileInfosSer();
        //FileInfoSer fi = infos[0][0][0];

        ImagePlus imp = new ImagePlus("", stack);

        // todo: what does this do?
        //if (imp.getType() == ImagePlus.GRAY16 || imp.getType() == ImagePlus.GRAY32)
        //    imp.getProcessor().setMinAndMax(min, max);

        // TODO: this needs be checked!
        //imp.setFileInfo(fi.getFileInfo()); // saves FileInfo of the first image

        if (logger.isShowDebug())
        {
            logger.info("# DataStreamingTools.createImagePlusFromVSS");
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
    public static ImagePlus loadVSSFullyIntoRAM(ImagePlus imp, int nIOthreads)
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
            futures.add(es.submit(new LoadFrameFromVSSIntoRAM(imp, t, impRAM, nIOthreads)));
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

    public void saveVSSAsPlanes(SavingSettings savingSettings)
    {

        interruptSavingThreads = false;

        // Do the jobs
        //
        ExecutorService es = Executors.newFixedThreadPool(savingSettings.nThreads);
        List<Future> futures = new ArrayList<>();
        for ( int c = 0; c < savingSettings.imp.getNChannels(); c++)
        {
            for (int t = 0; t < savingSettings.imp.getNFrames(); t++)
            {
                for (int z = 0; z < savingSettings.imp.getNSlices(); z++)
                {
                    futures.add( es.submit( new SaveVSSPlane(this, c, t, z, savingSettings) ) );
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

    public void saveVSSAsStacks(SavingSettings savingSettings)
    {

        interruptSavingThreads = false;

        int nSavingThreads = savingSettings.nThreads;

        if ( savingSettings.fileType.equals( Utils.FileType.HDF5 ) )
        {
            nSavingThreads = 1; // H5 is not multi-threaded anyway.
        }


        Hdf55ImarisBdvWriter.ImarisH5Settings imarisH5Settings = null;

        if ( savingSettings.fileType.equals( Utils.FileType.HDF5_IMARIS_BDV) )
        {
            nSavingThreads = 1; // H5 is not multi-threaded anyway.

            Hdf55ImarisBdvWriter writer = new Hdf55ImarisBdvWriter();

            imarisH5Settings = new Hdf55ImarisBdvWriter.ImarisH5Settings();

            writer.saveImarisAndBdvMasterFiles(
                    savingSettings.imp,
                    savingSettings.directory,
                    savingSettings.fileBaseName,
                    imarisH5Settings);

            logger.info("Image sizes at different resolutions:");
            Utils.logArrayList(imarisH5Settings.sizes);

            logger.info("Image chunking:");
            Utils.logArrayList(imarisH5Settings.chunks);


        }

        // Save individual files for each channel and time-point
        //
        ExecutorService es = Executors.newFixedThreadPool( nSavingThreads );
        List<Future> futures = new ArrayList<>();

        AtomicInteger counter = new AtomicInteger(0);
        for (int t = 0; t < savingSettings.imp.getNFrames(); t++)
        {
            futures.add( es.submit( new SaveVSSFrame( this,
                    t,
                    savingSettings,
                    imarisH5Settings,
                    counter ) ) );
        }

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
        private int t, z;
        private boolean showImage;
        private boolean throwFileNotExistsError;

        ParseFilesIntoVirtualStack(ImagePlus imp, int t, int z,
                                   boolean isShowImage, boolean throwFileNotExistsError)
        {
            this.imp = imp;
            this.t = t;
            this.z = z;
            this.showImage = isShowImage;
            this.throwFileNotExistsError = throwFileNotExistsError;

        }

        public void run()
        {

            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

            for ( int c = 0; c < vss.getChannels(); c++ )
            {
                vss.setInfoFromFile(t, c, z, throwFileNotExistsError);
            }

            if ( t == 0 && z == 0 && showImage)
            {
                showImageAndInfo(vss);
            }

        }

        public void showImageAndInfo(VirtualStackOfStacks vss)
        {
            // show image
            //
            if (vss != null && vss.getSize() > 0)
            {
                imp = createImagePlusFromVSS(vss);
            }
            else
            {
                logger.error("Something went wrong loading the first image stack!");
                return;
            }

            Utils.show(imp);
            imp.setTitle("stream"); // TODO: get the selected directory as image name

            // show compression info
            //
            FileInfoSer[][][] infos = vss.getFileInfosSer();
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

    // main method for debugging
    // throws ImgIOException
    public static void main(String[] args)
    {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
        Class<?> clazz = DataStreamingTools.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length
                ());
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ij.ImageJ();


        //IJ.run("Memory & Threads...", "maximum=3000 parallel=4 run");
        //String path = "/Users/tischi/Desktop/example-data/T88200-OMEtiff/T0006.ome.tif";
        //ovs.openUsingSCIFIO(path);

        // todo: remove the initialisation from the constructor and put it into openFromDirectory

        /*if(interactive) {
            ovs = new DataStreamingTools();
            ovs.run("");
            BigDataTracker register = new BigDataTracker();
            register.run("");
        }*/


        //final String directory = "/Users/tischi/Desktop/Gustavo_Crop/";
        //final String directory = "/Users/tischi/Desktop/example-data/iSPIM tif stacks/";
        //final String directory = "/Users/tischi/Desktop/example-data/Leica single tif files/";
        //final String directory = "/Users/tischi/Desktop/example-data/Leica single tif files 2channels/";

        //final String directory = "/Volumes/almf/group/ALMFstuff/ALMF_Data/ALMF_testData/EM/GalNac_HPF/";

        //final String directory = "/Users/tischi/Desktop/example-data/3d-embryo/";

        //final String directory = "/Volumes/almf/tischer/browsing/";
        //final String directory = "/Volumes/almf/tischer/browsing/";

        //final String directory = "/Users/tischi/Desktop/BIAS2017-Registration/Cell45/";


        //final String directory = "/Users/tischi/Desktop/example-data/Nils--MATLAB--Compressed/";

        // final String directory = "/Volumes/USB DISK/Ashna -test/";
        // final String directory = "/Users/tischi/Desktop/example-data/Ashna-Leica-Target-LSEA/";


        //final String directory = "/Volumes/My Passport/Res_13/";
        //final String directory = "/Users/tischi/Desktop/example-data/tracking_test/";
        //final String directory = "/Volumes/almfspim/tischi/SPIM-example-data/Nils-MATLAB--tif-stacks--1channel--lzw-compressed/";
        //String filter = null;

        //String openingMethod = "tiffLoadAllIFDs";

        //OpenHdf5Test oh5 = new OpenHdf5Test();
        //oh5.openOneFileAsImp("/Users/tischi/Desktop/example-data/luxendo/ch0/fused_t00000_c0.h5");
        // logger.isShowDebug()  = true;


        //ImagePlus imp3 = IJ.openImage("/Users/tischi/Desktop/BIAS2017-Registration/Experiment-40_s5.tif");
        //imp3.show();



        final DataStreamingTools dataStreamingTools = new DataStreamingTools();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                int nIOthreads = 10;
                final String directory = "/Users/tischi/Downloads/BDTracker_test_data/";
                ///Volumes/almf/group/ALMFstuff/ALMF_Data/ALMF_testData/EM/GalNac_HPF--10x10x10nm--Classification
                String namingPattern = null; ImageDataInfo imageDataInfo = null;
                /*
                String namingPattern = "classified--C<c>--T<t>--Z<z>.tif";
                ImageDataInfo imageDataInfo = new ImageDataInfo();
                imageDataInfo.nZ = 900;
                imageDataInfo.nC = 1;
                imageDataInfo.nT = 1;
                imageDataInfo.channelFolders = new String[] {""};
                */
                dataStreamingTools.openFromDirectory(
                        directory,
                        "None",
                        ".*",
                        "Data",
                        imageDataInfo,
                        nIOthreads,
                        true,
                        false);
            }
        });
        t1.start();
        IJ.wait(1000);

        DataStreamingToolsGUI dataStreamingToolsGUI = new DataStreamingToolsGUI();
        dataStreamingToolsGUI.showDialog();

        BigDataTrackerPlugIn_ bigDataTrackerPlugIn = new BigDataTrackerPlugIn_();
        bigDataTrackerPlugIn.run("");



        /*
        if (Mitosis_ome) {
            ovs = new DataStreamingTools("/Users/tischi/Desktop/example-data/Mitosis-ome/", null, 1, 1, -1, 2, "tiffUseIFDsFirstFile", "tc");
            ImagePlus imp = ovs.openFromDirectory();
            imp.show();
            BigDataTracker register = new BigDataTracker(imp);
            register.showDialog();
        }

        if (MATLAB) {
            ovs = new DataStreamingTools("/Users/tischi/Desktop/example-data/MATLABtiff/", null, 1, 1, -1, 1, "tiffUseIFDsFirstFile", "tc");
            ImagePlus imp = ovs.openFromDirectory();
            imp.show();
            BigDataTracker register = new BigDataTracker(imp);
            register.showDialog();
		}
        */

        /*
        if (MATLAB_EXTERNAL) {
            ImagePlus imp = ovs.open("/Volumes/My Passport/Res_13/", "Tiff: Use IFDs of first file for all", 1);
            imp.show();
            BigDataTracker register = new BigDataTracker(imp);
            register.showDialog();

        }
        if(OME) {
            // intialise whole data set
            ImagePlus imp = ovs.open("/Users/tischi/Desktop/example-data/T88200-OMEtiff/", "Tiff: Use IFDs of first file for all", 1);
            imp.show();

            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

            // open virtual subset
            FileInfo[][] infos = vss.getFileInfos();
            int t=0,nt=2,nz=10,ny=70,nx=70;
            int[] z = {30,29};
            int[] x = {50,55};
            int[] y = {34,34};
            //ImagePlus impVirtualCropSeries = ovs.openCropped(infos, t, nt, nz, nx, ny, z, x, y);
            //ImagePlus impVirtualCropSeries = ovs.openCropped(infos, nz, nx, ny, positions);

            //impVirtualCropSeries.show();

        }

        if (OME_drift) {
            ImagePlus imp = ovs.open("/Users/tischi/Desktop/example-data/T88200-OMEtiff-registration-test/", "Tiff: Use IFDs of first file for all", 1);
            imp.setTitle("AAAA");
            imp.show();
            //VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

            // compute drift
            BigDataTracker register = new BigDataTracker(imp);

            //Positions3D positions = register.computeDrifts3D(0,3,24,69-24,45,80,27,80, "center_of_mass", 200);
            //positions.printPositions();

            // open drift corrected as virtual stack
            //FileInfo[][] infos = vss.getFileInfos();
            //ImagePlus impVirtualCropSeries = ovs.openCropped(infos, 69-24, 70, 70, positions);
            //impVirtualCropSeries.show();

        }

		if (OME_MIP) {
			ImagePlus imp = ovs.open("/Users/tischi/Desktop/example-data/OME_MIPs/", "Tiff: Use IFDs of first file for all", 1);
            imp.show();
            BigDataTracker register = new BigDataTracker(imp);
        }
    */

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

