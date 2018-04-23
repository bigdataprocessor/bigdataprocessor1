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

package de.embl.cba.bigDataTools.VirtualStackOfStacks;

/**
 * Created by tischi on 27/10/16.
 */

import de.embl.cba.bigDataTools.Region5D;
import de.embl.cba.bigDataTools.logging.IJLazySwingLogger;
import de.embl.cba.bigDataTools.logging.Logger;
import de.embl.cba.bigDataTools.utils.Utils;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.gui.NewImage;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import net.imglib2.FinalInterval;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;

// todo: replace all == with "equals"
// TODO: extend VirtualStack rather than ImageStack ?

/**
 This class represents an array of disk-resident image stacks.
 */
public class VirtualStackOfStacks extends VirtualStack {
    int nSlices;
    private int nX, nY, nZ, nC, nT;
    int bitDepth = 0;
    FileInfoSer[][][] infos;  // channel, t, z
    String fileType = Utils.FileType.TIFF_STACKS.toString();
    String directory = "";
    String[] channelFolders;
    String[][][] ctzFileList;
    String h5DataSet;
    String namingScheme;
    String filterPattern;
    ArrayList< Point3D > chromaticShifts;
    int currentStackPosition = 0;

    private ArrayList < String > lockedFiles = new  ArrayList<>();

    Logger logger = new IJLazySwingLogger();

    /** Creates a new, empty virtual stack of required size */
    public VirtualStackOfStacks( String directory, String[] channelFolders, String[][][] fileList, int nC, int nT, int nX, int nY, int nZ, int bitDepth, String fileType, String h5DataSet) {
        super();

        this.directory = directory;
        this.nC = nC;
        this.nT = nT;
        this.nZ = nZ;
        this.nX = nX;
        this.nY = nY;
        this.bitDepth = bitDepth;
        this.nSlices = nC*nT*nZ;
        this.fileType = fileType;
        this.channelFolders = channelFolders;
        this.ctzFileList = fileList;
        this.infos = new FileInfoSer[nC][nT][nZ];
        this.h5DataSet = h5DataSet;

        initialiseChromaticShifts( nC );

        if( logger.isShowDebug() ) {
            logStatus();
        }

    }


    public VirtualStackOfStacks( String directory, FileInfoSer[][][] infos )
    {
        super();

        this.infos = infos;
        this.directory = directory;
        nC = infos.length;
        nT = infos[0].length;
        bitDepth = infos[0][0][0].bytesPerPixel*8;

        if(infos[0][0][0].isCropped)
        {
            nX = (int) infos[0][0][0].pCropSize[0];
            nY = (int) infos[0][0][0].pCropSize[1];
            nZ = (int) infos[0][0][0].pCropSize[2];
        } else {
            nX = (int) infos[0][0][0].width;
            nY = (int) infos[0][0][0].height;
            nZ = (int) infos[0][0].length;
        }

        nSlices = nC*nT*nZ;

        initialiseChromaticShifts( nC );

        if(infos[0][0][0].fileName.endsWith(".h5"))
            this.fileType = Utils.FileType.HDF5.toString();
        if(infos[0][0][0].fileName.endsWith(".tif"))
            this.fileType = Utils.FileType.TIFF_STACKS.toString(); // TODO: could be sinlge tif?!

        if( logger.isShowDebug() )
        {
            logStatus();
        }

    }

    private void initialiseChromaticShifts( int nC )
    {
        chromaticShifts = new ArrayList<>();
        for ( int c = 0; c < nC; ++c )
        {
            chromaticShifts.add( new Point3D( 0,0,0 ) );
        }
    }

    public void setChromaticShift( int c, Point3D shift )
    {
        chromaticShifts.set( c, shift );
    }


    public void setChromaticShifts( ArrayList< Point3D > shifts )
    {
        chromaticShifts = shifts;
    }

    public ArrayList< Point3D > getChromaticShiftsCopy(  )
    {
        ArrayList< Point3D > chromaticShiftCopy = new ArrayList<>( chromaticShifts );
        return chromaticShiftCopy;
    }


    public void setDirectory( String directory )
    {
        this.directory = directory;
    }

    public String getH5DataSet()
    {
        return h5DataSet;
    }

    public void setH5DataSet( String h5DataSet )
    {
        this.h5DataSet = h5DataSet;
    }

    public String getNamingScheme()
    {
        return namingScheme;
    }

    public void setNamingScheme( String namingScheme )
    {
        this.namingScheme = namingScheme;
    }

    public String getFilterPattern()
    {
        return filterPattern;
    }

    public void setFilterPattern( String filterPattern )
    {
        this.filterPattern = filterPattern;
    }


    public void logStatus() {
              logger.info("# VirtualStackOfStacks");
              logger.info("fileType: " + fileType);
              logger.info("x: " + nX);
              logger.info("y: " + nY);
              logger.info("z: " + nZ);
              logger.info("channel: " + nC);
              logger.info("t: " + nT);
    }

    public FileInfoSer[][][] getFileInfosSer() {
        return( infos );
    }

    public String getDirectory() {
        return directory;
    }

    public int numberOfUnparsedFiles()
    {
        int numberOfUnparsedFiles = 0;
        for(int c = 0; c < nC; c++ )
            for(int t = 0; t < nT; t++)
                if (infos[c][t] == null)
                    numberOfUnparsedFiles++;

        return numberOfUnparsedFiles;
    }

    public void setInfoFromFile( int c, int t, int z )
    {
        setInfoFromFile( c, t, z, true);
    }

    /** Adds an image stack from file infos */
    public void setInfoFromFile( int c, int t, int z, boolean throwError )
    {
        FileInfoSer[] info = null;
        FileInfoSer[] infoCT = null;
        FastTiffDecoder ftd;

        long startTime = System.currentTimeMillis();

        File f = new File(directory + channelFolders[c] + "/" + ctzFileList[c][t][z]);

        if ( f.exists() )
        {
            if (fileType.equals(Utils.FileType.TIFF_STACKS.toString()))
            {
                ftd = new FastTiffDecoder(directory + channelFolders[c], ctzFileList[c][t][0]);
                try
                {
                    info = ftd.getTiffInfo();
                }
                catch (Exception e)
                {
                    logger.error("Error parsing: "+ directory + channelFolders[c] + "/" + ctzFileList[c][t][z]);
                    logger.warning("setInfoFromFile: " + e.toString());
                }

                if( info.length != nZ )
                {
                    logger.error("Inconsistent number of z-planes in: "+ directory + channelFolders[c] + "/" + ctzFileList[c][t][z]);
                }

                // add missing information to first IFD
                info[0].fileName = ctzFileList[c][t][0];
                info[0].directory = channelFolders[c] + "/"; // relative path to main directory
                info[0].fileTypeString = fileType;

                infoCT = new FileInfoSer[nZ];
                for (z = 0; z < nZ; z++)
                {
                    infoCT[z] = new FileInfoSer( info[0] ); // copy first IFD for general info
                    // adapt information related to where the data is stored in this plane
                    infoCT[z].offset = info[z].offset;
                    infoCT[z].stripLengths = info[z].stripLengths;
                    infoCT[z].stripOffsets = info[z].stripOffsets;
                    //infoCT[z].rowsPerStrip = info[z].rowsPerStrip; // only read for first IFD!
                }

                infos[c][t] = infoCT;

            }
            else if (fileType.equals(Utils.FileType.HDF5.toString()))
            {
                //
                // construct a FileInfoSer
                // todo: this could be much leaner
                // e.g. the nX, nY and bit depth
                //

                int bytesPerPixel = 0;

                IHDF5Reader reader = HDF5Factory.openForReading(directory + channelFolders[c] + "/" + ctzFileList[c][t][0]);
                HDF5DataSetInformation dsInfo = reader.getDataSetInformation( h5DataSet );
                String dsTypeString = OpenerExtension.hdf5InfoToString(dsInfo);
                if ( dsTypeString.equals("int16") || dsTypeString.equals("uint16") )
                {
                    bytesPerPixel = 2;
                }
                else if ( dsTypeString.equals("int8") || dsTypeString.equals("uint8") )
                {
                    bytesPerPixel = 1;
                }
                else
                {
                    logger.error( "Unsupported bit depth " + dsTypeString );
                }

                infoCT = new FileInfoSer[nZ];
                for (z = 0; z < nZ; z++)
                {
                    infoCT[z] = new FileInfoSer();
                    infoCT[z].fileName = ctzFileList[c][t][z];
                    infoCT[z].directory = channelFolders[c] + "/";
                    infoCT[z].width = nX;
                    infoCT[z].height = nY;
                    infoCT[z].bytesPerPixel = bytesPerPixel; // todo: how to get the bit-depth from the info?
                    infoCT[z].h5DataSet = h5DataSet;
                    infoCT[z].fileTypeString = fileType;
                }

                infos[c][t] = infoCT;
            }
            else if ( fileType.equals(Utils.FileType.SINGLE_PLANE_TIFF.toString() ) )
            {
                ftd = new FastTiffDecoder(directory + channelFolders[c], ctzFileList[c][t][z]);

                try
                {
                    infos[c][t][z] = ftd.getTiffInfo()[0];
                }
                catch ( IOException e )
                {
                    System.out.print( e.toString() );
                }

                infos[c][t][z].directory = channelFolders[c] + "/"; // relative path to main directory
                infos[c][t][z].fileName = ctzFileList[c][t][z];
                infos[c][t][z].fileTypeString = fileType;
            }

        }
        else
        {
            if( throwError )
            {
                logger.error("Error opening: " + directory + channelFolders[c] + "/" + ctzFileList[c][t][z]);
            }
        }

    }

    /** Does nothing. */
    public void addSlice(String sliceLabel, Object pixels) {
    }

    /** Does nothing.. */
    public void addSlice(String sliceLabel, ImageProcessor ip) {
    }

    /** Does noting. */
    public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
    }

    /** Overrides the super method */
    public int getBitDepth()
    {
        return( bitDepth );
    }

    /** Does noting. */
    public void deleteSlice(int n) {
        /*
        if (n<1 || n>nSlices)
            throw new IllegalArgumentException("Argument out of range: "+n);
        if (nSlices<1)
            return;
        for (int i=n; i<nSlices; i++)
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
        */
    }

    /** Deletes the last slice in the stack. */
    public void deleteLastSlice() {
        /*if (nSlices>0)
            deleteSlice(nSlices);
            */
    }

    /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
    public Object getPixels( int n )
    {
        ImageProcessor ip = getProcessor(n);
        if (ip!=null)
            return ip.getPixels();
        else
            return null;
    }

    /** Assigns a pixel array to the specified slice,
     were 1<=n<=nslices. */
    public void setPixels(Object pixels, int n) {

    }

    /**
     *  Assigns and saves a pixel array to the specified slice,
     were 1<=n<=nslices.
     The method is synchronized to avoid that two threads try to writeinto the same file.
     */
    public void saveByteCube( byte[][][] pixelsZYX, FinalInterval interval )
    {
        final int X = 0, Y = 1, C = 2, Z = 3, T = 4;

        int c = (int) interval.min(C);
        int t = (int) interval.min(T);

        for ( int z = (int) interval.min(Z); z <= interval.max(Z); ++z )
        {
            String pathCTZ = directory + channelFolders[ c ] + "/" + ctzFileList[ c ][ t ][ z ];

            while ( lockedFiles.contains( pathCTZ ) )
            {
                sleep( 100 );
            }

            synchronized ( this )
            {
                lockedFiles.add( pathCTZ );
            }

            if ( infos[ c ][ t ][ z ] == null )
            {

                File f = new File( pathCTZ );

                if ( ! f.exists() )
                {
                    // file does not exist yet => create a black one
                    ImagePlus imp = NewImage.createByteImage( "title", nX, nY, 1, NewImage.FILL_BLACK );
                    FileSaver fileSaver = new FileSaver( imp );
                    fileSaver.saveAsTiff( pathCTZ );
                    setInfoFromFile( c, t, z );
                }
                else
                {
                    setInfoFromFile( c, t, z );
                }

            }

            boolean allPixelsSaved = false;
            boolean ioException = false;
            int ioErrors = 0;
            int MAX_ERRORS = 20;

            FileLock lock = null;

            while( ! allPixelsSaved  && ioErrors < MAX_ERRORS )
            {
                // replace pixels in existing file
                try
                {

                    RandomAccessFile raf = new RandomAccessFile( pathCTZ, "rw") ;


                    long offsetToImageData = infos[ c ][ t ][ z ].offset;

                    for ( int y = ( int ) interval.min( Y ); y <= interval.max( Y ); y++ )
                    {
                        int yChunk = y - ( int ) interval.min( Y );
                        int zChunk = z - ( int ) interval.min( Z );
                        int nx = pixelsZYX[ zChunk ][ yChunk ].length;

                        raf.seek( offsetToImageData + y * nX + interval.min( X ) );
                        raf.write( pixelsZYX[ zChunk ][ yChunk ], 0, nx );

                    }

                    raf.close();

                    allPixelsSaved = true;

                }
                catch ( Exception e )
                {
                    //IJ.log( e.toString() );
                    //e.printStackTrace();
                    System.out.println( "#################################### CAUGHT EXCEPTION:" );
                    System.out.print( e.toString() );
                    sleep( 1000 );
                    ioException = true;
                    ioErrors++;
                }
            }

            if ( ioException == true && ioErrors < MAX_ERRORS )
            {
                System.out.print( "#################################### RECOVERED FROM IO EXCEPTION AFTER ATTEMPTS:\n" );
                System.out.print( ioErrors );
            }
            else if ( ioException == true && ioErrors == MAX_ERRORS )
            {
                System.out.print( "#################################### DID NOT RECOVER FROM IO ERRORS.\n" );
                System.out.print( ioErrors );
            }


            synchronized ( this )
            {
                lockedFiles.remove( pathCTZ );
            }

        } // z

    }

    private void sleep( int milliSeconds )
    {
        try { Thread.sleep( milliSeconds ); } catch ( InterruptedException e ) { e.printStackTrace(); }
    }

    public int getCurrentStackPosition( )
    {
        return currentStackPosition;
    }

    /** Returns an ImageProcessor for the specified slice,
     were 1<=n<=nslices. Returns null if the stack is empty.
    N is computed by IJ assuming the czt ordering, with
    n = ( channel + z*nC + t*nZ*nC ) + 1
    */
    public ImageProcessor getProcessor( int n )
    {
        currentStackPosition = n;

        // recompute channel,z,t
        n -= 1;
        int c = (n % nC);
        int z = ((n-c)%(nZ*nC))/nC;
        int t = (n-c-z*nC)/(nZ*nC);

        ImagePlus imp;

        if( logger.isShowDebug() ) {
              logger.info("# VirtualStackOfStacks.getProcessor");
              logger.info("requested slice [one-based]: " + (n + 1));
              logger.info("channel [one-based]: " + (c + 1));
              logger.info("z [one-based]: " + (z + 1));
              logger.info("t [one-based]: " + (t + 1));
              logger.info("opening file: " + directory + infos[c][t][z].directory + infos[c][t][z].fileName);
        }

        if( infos[c][t][z] == null )
        {
            File f = new File(directory + channelFolders[c] + "/" + ctzFileList[c][t][z]);
            if ( ! f.exists() )
            {
                ImageStack stack = ImageStack.create(nX, nY, 1, bitDepth);
                return stack.getProcessor(1);
            }
            else
            {
                setInfoFromFile(c, t, z);
            }
        }

        FileInfoSer fi = infos[ c ][ t ][ z ];

        Point3D po, ps;
        po = new Point3D(0,0,z);
        if( fi.isCropped )
        {
            // offset for cropping is added in  getDataCube
            ps = new Point3D( fi.pCropSize[0], fi.pCropSize[1], 1);
        }
        else
        {
            ps = new Point3D( fi.width, fi.height, 1);
        }

        Region5D region5D = new Region5D();
        region5D.t = t;
        region5D.c = c;
        region5D.offset = po;
        region5D.size = ps;
        region5D.subSampling = new Point3D(1, 1, 1);
        int[] intensityGate = new int[]{-1,-1};
        imp = getDataCube( region5D, intensityGate, 1 );

        return imp.getProcessor();

    }

    public boolean isCropped() {
        return(infos[0][0][0].isCropped);
    }

    public Point3D getCropOffset() {
        return(new Point3D(infos[0][0][0].pCropOffset[0], infos[0][0][0].pCropOffset[1], infos[0][0][0].pCropOffset[2]));
    }

    public Point3D getCropSize() {
        return(new Point3D(infos[0][0][0].pCropSize[0], infos[0][0][0].pCropSize[1], infos[0][0][0].pCropSize[2]));
    }

    public ImagePlus getFullFrame(int t, int c, int nThreads)
    {
        return( getFullFrame(t, c, new Point3D(1,1,1), nThreads) );
    }

    public ImagePlus getFullFrame( int t, int c, Point3D pSubSample, int nThreads ) {

        Point3D po, ps;

        po = new Point3D(0, 0, 0);

        if(infos[0][0][0].isCropped)
        {
            // offset is added by getDataCube
            ps = infos[0][0][0].getCropSize();
        } else {
            ps = new Point3D(nX, nY, nZ);
        }

        Region5D region5D = new Region5D();
        region5D.t = t;
        region5D.c = c;
        region5D.offset = po;
        region5D.size = ps;
        region5D.subSampling = pSubSample;
        int[] intensityGate = new int[]{-1,-1};

        ImagePlus imp = getDataCube( region5D, intensityGate, nThreads );

        if( (int)pSubSample.getX()>1 || (int)pSubSample.getY()>1)
        {
            return( resizeWidthAndHeight(imp,(int)pSubSample.getX(),(int)pSubSample.getY() ) );
        }
        else
        {
            return(imp);
        }

    }


    public ImagePlus getDataCube( Region5D region5D, int[] intensityGate, int nThreads )   {

        if ( logger.isShowDebug() )
        {
              logger.info("# VirtualStackOfStacks.getDataCube");
              logger.info("t: " + region5D.t);
              logger.info("channel: " + region5D.c);
        }

        // check stuff
        if ( region5D.c < 0 ) logger.error("Selected channel is negative: " + region5D.c );
        if ( region5D.t < 0 ) logger.error("Selected time-point is negative: " + region5D.t );

        FileInfoSer fi = getFileInfo( region5D );

        adjustRegionOffsetForCropping( region5D, fi );

        adjustRegionOffsetForChromaticShifts( region5D );


        int dz = (int) region5D.subSampling.getZ();

        // compute ranges to be loaded
        int ox = (int) (region5D.offset.getX() + 0.5);
        int oy = (int) (region5D.offset.getY() + 0.5);
        int oz = (int) (region5D.offset.getZ() + 0.5);
        int sx = (int) (region5D.size.getX() + 0.5);
        int sy = (int) (region5D.size.getY() + 0.5);
        int sz = (int) (region5D.size.getZ() + 0.5);

        // adjust ranges for loading to stay within the image bounds

        //
        // adjust for negative offsets
        //

        // set negative offsets to zero
        int ox2 = ( ox < 0 ) ? 0 : ox;
        int oy2 = ( oy < 0 ) ? 0 : oy;
        int oz2 = ( oz < 0 ) ? 0 : oz;

        // adjust the loaded sizes accordingly
        int sx2 = sx - ( ox2 - ox );
        int sy2 = sy - ( oy2 - oy );
        int sz2 = sz - ( oz2 - oz );

        // adjust for too large loading ranges due to high offsets
        // - note: ox2=ox and sx2=sx if ox was positive
        int nX = fi.width;
        int nY = fi.height;
        int nZ = infos[ region5D.c ][ region5D.t ].length;

        sx2 = (ox2+sx2 > nX) ? nX-ox2 : sx2;
        sy2 = (oy2+sy2 > nY) ? nY-oy2 : sy2;
        sz2 = (oz2+sz2 > nZ) ? nZ-oz2 : sz2;

        // check memory requirements
        //
        long numPixels = (long) sx2 * sy2 * sz2;
        int numStacks = 1;
        int bitDepth = this.getBitDepth();

        if( ! Utils.checkMemoryRequirements( numPixels, bitDepth, numStacks) ) return(null);

        // load requested data into RAM
        //
        ImagePlus impLoaded = getRawLoaded( region5D, nThreads, dz, ox2, oy2, oz2, sx2, sy2, sz2 );

        if (impLoaded == null)
        {
            logger.info("Error: loading failed!");
            return null;
        }

        ImagePlus finalImp = getProcessedOfRightSize( intensityGate, fi, dz, ox, oy, oz, sx, sy, sz, ox2, oy2, oz2, sx2, sy2, sz2, impLoaded );

        ImagePlus subSampled = getSubSampled( region5D, finalImp );

        return subSampled;

    }

    private ImagePlus getSubSampled( Region5D region5D, ImagePlus finalImp )
    {
        // subsample in x and y
        ImagePlus subSampled;

        if ((int) region5D.subSampling.getX() > 1 || (int) region5D.subSampling.getY() > 1) {
            subSampled = resizeWidthAndHeight(finalImp, (int) region5D.subSampling.getX(), (int) region5D.subSampling.getY());
        } else {
            subSampled = finalImp;
        }
        return subSampled;
    }

    private ImagePlus getProcessedOfRightSize( int[] intensityGate, FileInfoSer fi, int dz, int ox, int oy, int oz, int sx, int sy, int sz, int ox2, int oy2, int oz2, int sx2, int sy2, int sz2, ImagePlus impLoaded )
    {
        // put the potentially smaller loaded stack into the full stack

        int finalStackOffsetX = (ox2 - ox);
        int finalStackOffsetY = (oy2 - oy);
        int finalStackOffsetZ = (oz2 - oz);

        if (dz > 1) { // adapt for sub-sampling in z
            sz = (int) (1.0 * sz / dz + 0.5); // final stack size
            finalStackOffsetZ = (int) (1.0 * finalStackOffsetZ / dz); // final stack offset
        }

        ImageStack finalStack = ImageStack.create(sx, sy, sz, fi.bytesPerPixel*8);

        if( sx2>0 && sy2>0 && sz2>0 ) // something was actually loaded
        {
            //
            // apply intensityGate
            // - this helps both the center of mass and the correlation
            if( (intensityGate[0] != -1) || (intensityGate[1] != -1) )
            {
                Utils.applyIntensityGate( impLoaded, intensityGate );
            }

            //
            // put the loaded stack into larger requested stack
            // - this can be necessary when requested stack was at image boundary
            ImageStack loadedStack = impLoaded.getStack();
            for ( int z = 1; z <= loadedStack.size(); z++ ) // getProcessor is one-based
            {
                ImageProcessor ip = loadedStack.getProcessor(z);
                ImageProcessor ip2 = ip.createProcessor(sx, sy);
                ip2.insert(ip, finalStackOffsetX, finalStackOffsetY);
                if( z + finalStackOffsetZ > finalStack.size() )
                {
                     logger.error("Error due to z-subsampling");
                }
                finalStack.setProcessor(ip2, z + finalStackOffsetZ);
            }
        }

        // todo: if this is called from "getProcessor" I need different logic because
        // it should only return one plane

        return new ImagePlus("", finalStack);
    }

    private ImagePlus getRawLoaded( Region5D region5D, int nThreads, int dz, int ox2, int oy2, int oz2, int sx2, int sy2, int sz2 )
    {
        ImagePlus impLoaded = null;

        if( sx2>0 && sy2>0 && sz2>0 )
        {
            Point3D po2 = new Point3D(ox2, oy2, oz2);
            Point3D ps2 = new Point3D(sx2, sy2, sz2);
            long duration = System.currentTimeMillis();

            impLoaded = new OpenerExtension().readDataCube(directory, infos[region5D.c][region5D.t], dz, po2, ps2, nThreads);
            duration = System.currentTimeMillis() - duration;
        }
        return impLoaded;
    }

    private void adjustRegionOffsetForCropping( Region5D region5D, FileInfoSer fi )
    {
        if ( fi.isCropped )
        {
            region5D.offset = region5D.offset.add( fi.getCropOffset() );
        }
    }

    private void adjustRegionOffsetForChromaticShifts( Region5D region5D )
    {
        region5D.offset = region5D.offset.add( chromaticShifts.get( region5D.c ) );
    }

    private FileInfoSer getFileInfo( Region5D region5D )
    {
        ensureExistenceOfFileInfo( region5D );

        FileInfoSer fi;

        if ( (int) region5D.offset.getZ() >= 0 )
        {
            fi = infos[ region5D.c ][ region5D.t ][ (int) region5D.offset.getZ() ];
        }
        else // requesting negative z can happen during object tracking
        {
            fi = infos[ region5D.c ][ region5D.t ][ 0 ];
        }

        return fi;
    }

    private void ensureExistenceOfFileInfo( Region5D region5D )
    {
        // make sure we have all the file-info data
        if ( infos[ region5D.c ][ region5D.t ] == null )
        {
            // stack info not yet loaded => get it!
            setInfoFromFile( region5D.c, region5D.t, 0 );
        }

        // make sure we have all the file-info data
        for ( int z = (int)region5D.offset.getZ() ; z < (int)region5D.offset.getZ() + (int)region5D.size.getZ(); ++z )
        {
            if ( (z > -1) && (z < nZ ) ) // because during tracking one could ask for out-of-bounds z-planes
            {
                if ( infos[ region5D.c ][ region5D.t ][ z ] == null)
                {
                    File f = new File(directory + channelFolders[region5D.c] + "/" + ctzFileList[region5D.c][region5D.t][z]);
                    // file info not yet loaded => get it!
                    setInfoFromFile( region5D.c, region5D.t, z );
                }
            }
        }
    }

    public int computeMean16bit(ImageStack stack) {

        //long startTime = System.currentTimeMillis();
        double sum = 0.0;
        int i;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xMin = 0;
        int xMax = (width-1);
        int yMin = 0;
        int yMax = (height-1);
        int zMin = 0;
        int zMax = (depth-1);

        for( int z=zMin; z<=zMax; z++ )
        {
            short[] pixels = (short[]) stack.getProcessor(z+1).getPixels();
            for ( int y = yMin; y<=yMax; y++ )
            {
                i = y * width + xMin;
                for ( int x = xMin; x <= xMax; x++ )
                {
                    sum += (pixels[i] & 0xffff);
                    i++;
                }
            }
        }

        return((int) sum/(width*height*depth));

    }

    public ImagePlus resizeWidthAndHeight(ImagePlus imp, int dx, int dy) {
        int nSlices = imp.getStackSize();
        int nx = imp.getWidth(), ny = imp.getHeight();
        ImagePlus imp2 = imp.createImagePlus();
        ImageStack stack1 = imp.getStack();
        ImageStack stack2 = new ImageStack(nx/dx, ny/dy);
        ImageProcessor ip1, ip2;
        int method = ImageProcessor.BILINEAR; // ImageProcessor.NEAREST_NEIGHBOR;
        if (nx == 1 || ny == 1)
            method = ImageProcessor.NONE;
        for (int i = 1; i <= nSlices; i++) {
            ip1 = stack1.getProcessor(i);
            ip1.setInterpolationMethod(method);
            ip2 = ip1.resize(nx/dx, ny/dy, false);
            if (ip2 != null)
                stack2.addSlice("", ip2);
        }
        imp2.setStack("", stack2);
        return(imp2);
    }

    public int getSize() {
        return nSlices;
    }

    public int getWidth() {
        return nX;
    }

    public int getHeight() {
        return nY;
    }

    public int getDepth() {
        return nZ;
    }

    public int getChannels() {
        return nC;
    }

    public int getFrames() {
        return nT;
    }


    /** Returns the file name of the Nth image. */
    public String getSliceLabel(int n) {
        //int nFile;
        //nFile = (n-1) / nZ;
        //return infos[nFile][0].fileName;
        return "slice label";
    }

    /** Returns null. */
    public Object[] getImageArray() {
        return null;
    }

    /** Does nothing. */
    public void setSliceLabel(String label, int n) {
    }

    /** Always return true. */
    public boolean isVirtual() {
        return true; // do we need this?
    }

    /** Does nothing. */
    public void trim() {
    }

}


/*
    public void deleteSlice(int n) {
        if (n<1 || n>nSlices)
            throw new IllegalArgumentException("Argument out of range: "+n);
        if (nSlices<1)
            return;
        for (int i=n; i<nSlices; i++)
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
    }

    /** Deletes the last slice in the stack.
    public void deleteLastSlice() {
        if (nSlices>0)
            deleteSlice(nSlices);
    }*/

/*
// todo: put the conversion from centerRadii to offsetSize into this function
    public ImagePlus getCubeByTimeCenterAndRadii(int t, int channel, Point3D psub, Point3D pc, Point3D pr) {

        if( logger.isShowDebug() ) {
            logger.info("# VirtualStackOfStacks.getCroppedFrameCenterRadii");
            logger.info("t: "+t);
            logger.info("channel: "+channel);
            }

        FileInfoSer fi = infos[0][0][0];

        if(fi.isCropped) {
            // load cropped slice
            pc = pc.add(fi.getCropOffset());
        }

        if(infos[channel][t] == null) {
            // file info not yet loaded => get it!
            setInfoFromFile(t, channel);
        }

        ImagePlus imp = new OpenerExtension().openCroppedStackCenterRadii(directory, infos[channel][t], (int) psub.getZ(), pc, pr);

        if (imp==null) {
            logger.info("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
    }
    */