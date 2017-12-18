package de.embl.cba.bigDataTools.VirtualStackOfStacks;

import de.embl.cba.bigDataTools.Hdf5DataCubeWriter;
import de.embl.cba.bigDataTools.ImarisDataSet;
import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.dataStreamingTools.SavingSettings;
import de.embl.cba.bigDataTools.logging.IJLazySwingLogger;
import de.embl.cba.bigDataTools.logging.Logger;
import de.embl.cba.bigDataTools.ProjectionXYZ;
import de.embl.cba.bigDataTools.utils.Utils;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.HDF5IntStorageFeatures;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.plugin.Duplicator;
import loci.common.services.ServiceFactory;
import loci.formats.ImageWriter;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

import ij.plugin.Binner;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tischi on 11/04/17.
 */

public class SaveVSSFrame implements Runnable {
    int t;
    AtomicInteger counter;
    DataStreamingTools dataStreamingTools;
    SavingSettings savingSettings;
    ImarisDataSet imarisDataSetProperties;
    final long startTime;

    Logger logger = new IJLazySwingLogger();

    public SaveVSSFrame(DataStreamingTools dataStreamingTools,
                        int t,
                        SavingSettings savingSettings,
                        ImarisDataSet imarisDataSetProperties,
                        AtomicInteger counter,
                        final long startTime )
    {
        this.dataStreamingTools = dataStreamingTools;
        this.t = t;
        this.savingSettings = savingSettings;
        this.imarisDataSetProperties = imarisDataSetProperties;
        this.counter = counter;
        this.startTime = startTime;
    }

    public void run()
    {

        // TODO:
        // - check whether enough RAM is available to execute current thread
        // - if not, run GC and wait until there is enough
        // - estimate 3x more RAM then actually necessary
        // - if waiting takes to long somehoe terminate in a nice way

        long freeMemoryInBytes = IJ.maxMemory() - IJ.currentMemory();
        ImagePlus imp = savingSettings.imp;
        long numBytesOfImage = (long) imp.getWidth() *
                imp.getHeight() *
                imp.getNSlices() *
                imp.getNChannels() *
                imp.getBitDepth() / 8;

        if ( numBytesOfImage > 1.5 * freeMemoryInBytes )
        {
            // TODO: do something...
        }

        for (int c = 0; c < savingSettings.imp.getNChannels(); c++)
        {

            if ( dataStreamingTools.interruptSavingThreads )
            {
                logger.progress("Stopped saving thread: ", "" + t);
                return;
            }

            // Load
            //

            ImagePlus impChannelTime = null;
            VirtualStackOfStacks vss = (VirtualStackOfStacks) savingSettings.imp.getStack();
            if ( vss.fileType.equals( Utils.FileType.SINGLE_PLANE_TIFF.toString()) )
            {
                // load all frames individually, the Duplicator
                // will also handle missing frames by returning black images
                Duplicator duplicator = new Duplicator();
                impChannelTime = duplicator.run( savingSettings.imp, c+1, c+1, 1, savingSettings.imp.getNSlices(), t+1, t+1 );
            }
            else
            {
                impChannelTime = vss.getFullFrame( t, c, savingSettings.nThreads );
            }

            // Gate
            //
            if ( savingSettings.gate )
            {
                gate( impChannelTime, savingSettings.gateMin, savingSettings.gateMax );
            }

            // Convert
            //
            if ( savingSettings.convertTo8Bit )
            {
                IJ.setMinAndMax(impChannelTime, savingSettings.mapTo0, savingSettings.mapTo255);
                IJ.run( impChannelTime, "8-bit", "" );
            }

            if ( savingSettings.convertTo16Bit )
            {
                IJ.run( impChannelTime, "16-bit", "" );
            }

            // Bin, project and save
            //
            String[] binnings = savingSettings.bin.split(";");

            for ( String binning : binnings )
            {

                if ( dataStreamingTools.interruptSavingThreads )
                {
                    logger.progress("Stopped saving thread: ", "" + t);
                    return;
                }

                String newPath = savingSettings.filePath;

                // Binning
                // - not for imarisH5 saving format as there will be a resolution pyramid anyway
                //
                ImagePlus impBinned = impChannelTime;

                int[] binningA = Utils.delimitedStringToIntegerArray(binning, ",");

                if ( binningA[0] > 1 || binningA[1] > 1 || binningA[2] > 1 )
                {
                    Binner binner = new Binner();
                    impBinned = binner.shrink( impChannelTime, binningA[0], binningA[1], binningA[2], binner.AVERAGE );
                    newPath = savingSettings.filePath + "--bin-" + binningA[0] + "-" + binningA[1] + "-" + binningA[2];
                }


                // Save volume
                //
                if ( savingSettings.saveVolume )
                {
                    // Save
                    //
                    if ( savingSettings.fileType.equals( Utils.FileType.TIFF ) )
                    {
                        saveAsTiff(impBinned, c, t, savingSettings.compression,
                                savingSettings.rowsPerStrip, newPath);
                    }
                    else if ( savingSettings.fileType.equals( Utils.FileType.HDF5 ) )
                    {
                        int compressionLevel = 0;
                        saveAsHDF5( impBinned, c, t, compressionLevel, newPath );
                    }
                    else if ( savingSettings.fileType.equals( Utils.FileType.HDF5_IMARIS_BDV ) )
                    {
                        Hdf5DataCubeWriter writer = new Hdf5DataCubeWriter();

                        writer.writeImarisCompatibleResolutionPyramid(
                                impBinned,
                                imarisDataSetProperties,
                                c, t );

                    }

                }

                // Save projections
                // TODO: save into one single file
                if ( savingSettings.saveProjection )
                {
                    saveAsTiffXYZMaxProjection( impBinned, c, t, newPath );
                }

            }

            documentProgress( imp.getNFrames() );

        }

    }

    private void documentProgress( int total )
    {
        counter.incrementAndGet();

        double minutesSpent = (int) (1.0 * System.currentTimeMillis() - startTime ) / (1000 * 60);
        double minutesPerStack = minutesSpent / counter.get();
        double minutesLeft = (total - counter.get()) * minutesPerStack;

        logger.progress("Saved time point",
                "" + counter.get() + "/" + total
                        + "; time (spent, left) [min]: " + (int) minutesSpent + ", " + (int) minutesLeft
                        + "; memory: "
                        + IJ.freeMemory());
    }

    public void saveAsTiffXYZMaxProjection(ImagePlus imp, int c, int t, String path)
    {
        ProjectionXYZ projectionXYZ = new ProjectionXYZ( imp );
        projectionXYZ.setDoscale( false );
        ImagePlus impProjection = projectionXYZ.getXYZProject();

        FileSaver fileSaver = new FileSaver( impProjection );
        String sC = String.format("%1$02d", c);
        String sT = String.format("%1$05d", t);
        String pathCT = path + "--xyz-max-projection" + "--C" + sC + "--T" + sT + ".tif";
        fileSaver.saveAsTiff(pathCT);
    }

    public void saveAsHDF5( ImagePlus imp, int c, int t, int compressionLevel, String path )
    {
        int nZ     = imp.getNSlices();
        int nY     = imp.getHeight();
        int nX     = imp.getWidth();

        //
        //  Open output file
        //


        try
        {
            String sC = String.format("%1$02d", c);
            String sT = String.format("%1$05d", t);
            String pathCT = path + "--C" + sC + "--T" + sT + ".h5";

            IHDF5Writer writer;
            writer = HDF5Factory.configure(pathCT).useSimpleDataSpaceForAttributes().overwrite().writer();

            //  get element_size_um
            //
            // todo: this is never used...?
            ij.measure.Calibration cal = imp.getCalibration();
            float[] element_size_um = new float[3];
            element_size_um[0] = (float) cal.pixelDepth;
            element_size_um[1] = (float) cal.pixelHeight;
            element_size_um[2] = (float) cal.pixelWidth;

            //  create channelDims vector for MDxxxArray
            //
            int[] channelDims = null;
            if (nZ > 1)
            {
                channelDims = new int[3];
                channelDims[0] = nZ;
                channelDims[1] = nY;
                channelDims[2] = nX;
            }
            else
            {
                channelDims = new int[2];
                channelDims[0] = nY;
                channelDims[1] = nX;
            }

            // take care of data sets with more than 2^31 elements
            //
            long   maxSaveBlockSize = (1L<<31) - 1;
            long[] saveBlockDimensions = new long[channelDims.length];
            long[] saveBlockOffset = new long[channelDims.length];
            int    nSaveBlocks = 1;
            long   levelsPerWriteOperation = nZ;

            for( int d = 0; d < channelDims.length; ++d) {
                saveBlockDimensions[d] = channelDims[d];
                saveBlockOffset[d] = 0;
            }

            long channelSize = (long)nZ * (long)nY * (long)nX;
            if( channelSize >= maxSaveBlockSize) {
                if( nZ == 1) {
                    IJ.error( "maxSaveBlockSize must not be smaller than a single slice!");
                } else {
                    long minBlockSize = nY * nX;
                    levelsPerWriteOperation = maxSaveBlockSize / minBlockSize;
                    saveBlockDimensions[0] = (int)levelsPerWriteOperation;
                    nSaveBlocks = (int)((nZ - 1) / levelsPerWriteOperation + 1); // integer version for ceil(a/b)
                    IJ.log("Data set has " + channelSize + " elements (more than 2^31). Saving in " + nSaveBlocks + " blocks with maximum of " + levelsPerWriteOperation + " levels");
                }
            }


            String dsetName = "Data";

            for( int block = 0; block < nSaveBlocks; ++block) {
                // compute offset and size of next block, that is saved
                //
                saveBlockOffset[0] = (long)block * levelsPerWriteOperation;
                int remainingLevels = (int)(nZ - saveBlockOffset[0]);
                if( remainingLevels < saveBlockDimensions[0] ) {
                    // last block is smaller
                    saveBlockDimensions[0] = remainingLevels;
                }
                // compute start level in image processor
                int srcLevel = (int)saveBlockOffset[0];
                //IJ.info( "source level = " +srcLevel);
                // writeHeader Stack according to data type
                //
                int imgColorType = imp.getType();

                if (imgColorType == ImagePlus.GRAY16)
                {
                    // Save as Short Array
                    //
                    MDShortArray arr = new MDShortArray( channelDims);

                    // copy data
                    //
                    ImageStack stack = imp.getStack();
                    short[] flatArr   = arr.getAsFlatArray();
                    int sliceSize    = nY*nX;

                    for(int lev = 0; lev < nZ; ++lev)
                    {
                        int stackIndex = imp.getStackIndex(c + 1,
                                lev + 1,
                                t + 1);
                        System.arraycopy( stack.getPixels(stackIndex), 0,
                                flatArr, lev*sliceSize,
                                sliceSize);
                    }

                    // save it
                    //
                    writer.uint16().writeMDArray( dsetName, arr, HDF5IntStorageFeatures.createDeflationDelete(compressionLevel));

                }
                else if (imgColorType == ImagePlus.GRAY8 )
                {

                    // Save as Byte Array
                    //
                    MDByteArray arr = new MDByteArray( saveBlockDimensions );
                    // copy data
                    //
                    ImageStack stack = imp.getStack();
                    byte[] flatArr   = arr.getAsFlatArray();
                    int sliceSize    = nY*nX;

                    for(int lev = 0; lev < saveBlockDimensions[0]; ++lev)
                    {
                        int stackIndex = imp.getStackIndex(c + 1,
                                srcLevel + lev + 1,
                                t + 1);
                        System.arraycopy( stack.getPixels(stackIndex), 0,
                                flatArr, lev*sliceSize,
                                sliceSize);
                    }

                    // save it
                    //
                    writer.uint8().writeMDArray( dsetName, arr, HDF5IntStorageFeatures.createDeflationDelete(compressionLevel));

                }

                //  add element_size_um attribute
                //
                writer.float32().setArrayAttr( dsetName, "element_size_um",
                        element_size_um);

            }
            writer.close();
        }
        catch (HDF5Exception err)
        {
            IJ.error("Error while saving '" + path + "':\n"
                    + err);
        }
        catch (Exception err)
        {
            IJ.error("Error while saving '" + path + "':\n"
                    + err);
        }
        catch (OutOfMemoryError o)
        {
            IJ.outOfMemory("Error while saving '" + path + "'");
        }

    }

    public void saveAsTiff(ImagePlus imp, int c, int t, String compression, int rowsPerStrip, String path)
    {

        if( compression.equals("LZW") ) // Use BioFormats
        {

            String sC = String.format("%1$02d", c);
            String sT = String.format("%1$05d", t);
            String pathCT = path + "--C" + sC + "--T" + sT + ".ome.tif";

            try
            {
                ServiceFactory factory = new ServiceFactory();
                OMEXMLService service = factory.getInstance(OMEXMLService.class);
                IMetadata omexml = service.createOMEXMLMetadata();
                omexml.setImageID("Image:0", 0);
                omexml.setPixelsID("Pixels:0", 0);
                omexml.setPixelsBinDataBigEndian(Boolean.TRUE, 0, 0);
                omexml.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);
                if ( imp.getBytesPerPixel() == 2)
                {
                    omexml.setPixelsType(PixelType.UINT16, 0);
                }
                else if ( imp.getBytesPerPixel() == 1 )
                {
                    omexml.setPixelsType(PixelType.UINT8, 0);
                }
                omexml.setPixelsSizeX(new PositiveInteger(imp.getWidth()), 0);
                omexml.setPixelsSizeY(new PositiveInteger(imp.getHeight()), 0);
                omexml.setPixelsSizeZ(new PositiveInteger(imp.getNSlices()), 0);
                omexml.setPixelsSizeC(new PositiveInteger(1), 0);
                omexml.setPixelsSizeT(new PositiveInteger(1), 0);

                int channel = 0;
                omexml.setChannelID("Channel:0:" + channel, 0, channel);
                omexml.setChannelSamplesPerPixel(new PositiveInteger(1), 0, channel);

                ImageWriter writer = new ImageWriter();
                writer.setCompression(TiffWriter.COMPRESSION_LZW);
                writer.setValidBitsPerPixel(imp.getBytesPerPixel()*8);
                writer.setMetadataRetrieve(omexml);
                writer.setId(pathCT);
                writer.setWriteSequentially(true); // ? is this necessary
                TiffWriter tiffWriter = (TiffWriter) writer.getWriter();
                long[] rowsPerStripArray = new long[1];
                rowsPerStripArray[0] = rowsPerStrip;

                for (int z = 0; z < imp.getNSlices(); z++) {
                    IFD ifd = new IFD();
                    ifd.put(IFD.ROWS_PER_STRIP, rowsPerStripArray);
                    //tiffWriter.saveBytes(z, Bytes.fromShorts((short[])imp.getStack().getProcessor(z+1).getPixels(), false), ifd);
                    if ( imp.getBytesPerPixel() == 2 )
                    {
                        tiffWriter.saveBytes(z, ShortToByteBigEndian((short[]) imp.getStack().getProcessor(z + 1).getPixels()), ifd);
                    }
                    else if ( imp.getBytesPerPixel() == 1 )
                    {
                        tiffWriter.saveBytes(z, (byte[])(imp.getStack().getProcessor(z + 1).getPixels()), ifd);

                    }
                }

                writer.close();

            }
            catch (Exception e)
            {
                logger.error(e.toString());
            }
        }
        else  // no compression: use ImageJ's FileSaver, as it is faster than BioFormats
        {
            FileSaver fileSaver = new FileSaver(imp);
            String sC = String.format("%1$02d", c);
            String sT = String.format("%1$05d", t);
            String pathCT = path + "--C" + sC + "--T" + sT + ".tif";
            //logger.info("Saving " + pathCT);
            fileSaver.saveAsTiffStack(pathCT);
        }
    }

    byte [] ShortToByteBigEndian(short [] input)
    {
        int short_index, byte_index;
        int iterations = input.length;

        byte [] buffer = new byte[input.length * 2];

        short_index = byte_index = 0;

        for(/*NOP*/; short_index != iterations; /*NOP*/)
        {
            // Big Endian: store higher byte first
            buffer[byte_index] = (byte) ((input[short_index] & 0xFF00) >> 8);
            buffer[byte_index + 1]     = (byte) (input[short_index] & 0x00FF);

            ++short_index; byte_index += 2;
        }

        return buffer;
    }

    public void gate( ImagePlus imp, int min, int max )
    {
        ImageStack stack = imp.getStack();

        for ( int i = 1; i < stack.size(); ++i )
        {
            if ( imp.getBitDepth() == 8 )
            {
                byte[] pixels = (byte[]) stack.getPixels( i );
                for ( int j = 0; j < pixels.length; j++ )
                {
                    int v = pixels[j] & 0xff;
                    pixels[j] = ( (v < min) || (v > max) ) ? 0 : pixels[j];
                }
            }

            if ( imp.getBitDepth() == 16 )
            {
                short[] pixels = (short[]) stack.getPixels( i );
                for ( int j = 0; j < pixels.length; j++ )
                {
                    int v = pixels[j] & 0xffff;
                    pixels[j] = ( (v < min) || (v > max) ) ? 0 : pixels[j];
                }
            }

        }

    }

}
