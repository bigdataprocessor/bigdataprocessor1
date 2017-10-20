package bigDataTools;

import bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import bigDataTools.utils.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by tischi on 13/07/17.
 */
public class Hdf55ImarisBdvWriter {

    public Hdf55ImarisBdvWriter()
    {

    }

    public static class ImarisH5Settings {

        public ArrayList<int[]> binnings = new ArrayList<>();
        public ArrayList<long[]> sizes = new ArrayList<>();
        public ArrayList<long[]> chunks = new ArrayList<>();

    }

    public void saveAsImarisAndBdv ( ImagePlus imp,
                                     String directory,
                                     String baseFileName )
    {

        ImarisH5Settings imarisH5Settings = new ImarisH5Settings();

        saveImarisAndBdvMasterFiles( imp,
                 directory,
                 baseFileName,
                 imarisH5Settings);

        //
        // Write data files, one per channel and time-point
        //

        for ( int c = 0; c < imp.getNChannels(); c++ )
        {
            for (int t = 0; t < imp.getNFrames(); t++)
            {
                writeChannelTimeH5File(imp, imarisH5Settings, c, t, baseFileName, directory);
            }
        }



    }

    public void saveImarisAndBdvMasterFiles(ImagePlus imp,
                                            String directory,
                                            String baseFileName,
                                            ImarisH5Settings imarisH5Settings)
    {

        ArrayList<int[]> binnings = imarisH5Settings.binnings;
        ArrayList<long[]> sizes = imarisH5Settings.sizes;
        ArrayList<long[]> chunks = imarisH5Settings.chunks;

        double[] calibration = new double[3];
        calibration[0] = imp.getCalibration().pixelWidth;
        calibration[1] = imp.getCalibration().pixelHeight;
        calibration[2] = imp.getCalibration().pixelDepth;

        setImarisResolutionLevelsAndChunking(imp, binnings, sizes, chunks);

        writeImarisMasterFile(imp, sizes, calibration, baseFileName, directory);

        writeBdvMasterFiles(imp, sizes, chunks, binnings, calibration, baseFileName, directory);

    }

    public void setImarisResolutionLevelsAndChunking(ImagePlus imp,
                                                     ArrayList<int[]> binnings,
                                                     ArrayList<long[]> sizes,
                                                     ArrayList<long[]> chunks)
    {
        long minVoxelVolume = 1024 * 1024;

        long[] size = new long[3];
        size[0] = imp.getWidth();
        size[1] = imp.getHeight();
        size[2] = imp.getNSlices();
        int impByteDepth = imp.getBitDepth() / 8;

        sizes.add( size );

        binnings.add( new int[]{ 1, 1, 1 } );

        chunks.add(new long[]{32, 32, 4});

        long voxelVolume = 0;
        int iResolution = 0;

        do
        {
            long[] lastSize = sizes.get( iResolution );
            int[] lastBinning = binnings.get( iResolution );

            long[] newSize = new long[3];
            int[] newBinning = new int[3];

            long lastVolume = lastSize[0] * lastSize[1] * lastSize[2];

            for ( int d = 0; d < 3; d++)
            {
                long lastSizeThisDimensionSquared = lastSize[d] * lastSize[d];
                long lastPerpendicularPlaneSize = lastVolume / lastSize[d];

                if ( 100 * lastSizeThisDimensionSquared > lastPerpendicularPlaneSize )
                {
                    newSize[d] = lastSize[d] / 2;
                    newBinning[d] = lastBinning[d] * 2;
                }
                else
                {
                    newSize[d] = lastSize[d];
                    newBinning[d] = lastBinning[d];
                }

                newSize[d] = Math.max( 1, newSize[d] );

            }

            sizes.add( newSize );
            binnings.add( newBinning );

            long[] newChunk = new long[] {16, 16, 16};
            for ( int i = 0; i < 3; i++ )
            {
                if( newChunk[i] > newSize[i] )
                {
                    newChunk[i] = newSize[i];
                }

            }
            chunks.add( newChunk );

            voxelVolume = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        } while ( impByteDepth * voxelVolume > minVoxelVolume );

    }

    /**
     * Write *.xml and *.h5 master files for BigDataViewer
     */
    public void writeBdvMasterFiles(ImagePlus imp,
                                    ArrayList<long[]> sizes,
                                    ArrayList<long[]> chunks,
                                    ArrayList<int[]> binnings,
                                    double[] calibration,
                                    String fileName,
                                    String directory)
    {

        // write h5 master file
        //
        String filePathMaster = directory + File.separator + fileName + "--bdv.h5";
        File fileMaster = new File( filePathMaster );
        if (fileMaster.exists()) fileMaster.delete();

        int file_id = H5.H5Fcreate(filePathMaster,
                HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        for ( int c = 0; c < imp.getNChannels(); c++ )
        {
            String group = String.format("s%02d", c);

            int group_id = H5.H5Gcreate( file_id, group,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT );

            h5WriteLongArrayListAs32IntArray( group_id, chunks, "subdivisions" );

            h5WriteIntArrayListAsDoubleArray( group_id, binnings, "resolutions" );

            H5.H5Gclose( group_id );

        }

        for ( int t = 0; t < imp.getNFrames(); t++ )
        {
            String time_group = String.format("t%05d", t);

            int time_group_id = H5.H5Gcreate( file_id, time_group,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

            for (int c = 0; c < imp.getNChannels(); c++)
            {
                String channel_group = String.format("s%02d", c);
                int channel_group_id = H5.H5Gcreate(time_group_id, channel_group,
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

                for (int r = 0; r < sizes.size(); r++)
                {

                    String resolution_group = String.format("%01d", r);
                    int resolution_group_id = H5.H5Gcreate(channel_group_id, resolution_group,
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

                    // create link, called "cells", to Data in external file
                    String relativePath = "./";
                    String filePathData = relativePath + fileName + getChannelTimeString( c , t ) + ".h5";
                    H5.H5Lcreate_external(
                            filePathData, getExternalH5DataPath( r ),
                            resolution_group_id, "cells",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);


                    H5.H5Gclose( resolution_group_id );
                }
                H5.H5Gclose( channel_group_id );
            }
            H5.H5Gclose( time_group_id );
        }
        H5.H5Fclose( file_id );

        //
        // write xml master file
        //

        // prepare file
        //
        String filePathXML = directory + File.separator + fileName + "--bdv.xml";
        File fileMasterXML = new File( filePathXML );
        if (fileMasterXML.exists()) fileMasterXML.delete();

        String viewSetups = "";
        String angleAttributes = "";
        String registrationString = "";
        String channelAttributes = "";

        for (int c = 0; c < imp.getNChannels(); c++)
        {
            String viewID = ""+c;
            String spacing = String.format("%s %s %s", calibration[0], calibration[1], calibration[2] );
            String size = String.format("%s %s %s", sizes.get(0)[0], sizes.get(0)[1], sizes.get(0)[2] );

            String registrationParameters = String.format(
                    "%s 0.0 0.0 0.0 " + "0.0 %s 0.0 0.0 " + "0.0 0.0 %s 0.0",
                    calibration[0], calibration[1], calibration[2]);

            String channelID = ""+(c+1);

            //String angle = "0";
            //String angleID = "0";
            //String angleName = "0";

            viewSetups += createViewSetupString( viewID, size, spacing, channelID, "channel "+(c+1) );
            //angleAttributes += createSingleAttributeString("Angle", angleID, angleName );
            channelAttributes += createSingleAttributeString("Channel", channelID, ""+(c+1));

            for ( int t = 0; t < imp.getNFrames(); t++ )
            {
                String time = ""+t; // this could be an actual formatted time
                String setup = ""+0;
                registrationString += createRegistrationString( time, setup, registrationParameters );
            }

        }

        String firstT = "" + 0;
        String lastT = "" + ( imp.getNFrames() - 1 );

        // put everything into one string
        //
        String completeXmlString = createBdvXMLString(
                fileName + "--bdv.h5", viewSetups, channelAttributes,
                firstT, lastT, registrationString );

        // write file
        //
        try {
            BufferedWriter out = new BufferedWriter( new FileWriter( filePathXML ) );
            out.write( completeXmlString );
            out.close();
        }
        catch (IOException e)
        {
            IJ.showMessage( "Exception " + e );
        }

    }


    /**
     * Writes the Imaris master file
     * - https://support.hdfgroup.org/HDF5/doc/RM/RM_H5L.html#Link-CreateExternal
     * - https://support.hdfgroup.org/ftp/HDF5/current/src/unpacked/examples/h5_extlink.c
     */
    public void writeImarisMasterFile(ImagePlus imp,
                                      ArrayList<long[]> sizes,
                                      double[] calibration,
                                      String fileName,
                                      String directory)
    {

        //
        // Create master file
        //
        String filePathMaster = directory + File.separator + fileName + "--imaris.ims";
        File fileMaster = new File( filePathMaster );
        if (fileMaster.exists()) fileMaster.delete();

        int file_id = H5.H5Fcreate(filePathMaster, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        setH5StringAttribute(file_id, "DataSetDirectoryName", "DataSet");
        setH5StringAttribute(file_id, "DataSetInfoDirectoryName", "DataSetInfo");
        setH5StringAttribute(file_id, "ImarisDataSet", "ImarisDataSet");
        setH5StringAttribute(file_id, "ImarisVersion", "5.5.0");  // file-format version
        setH5StringAttribute(file_id, "NumberOfDataSets", "1");
        setH5StringAttribute(file_id, "ThumbnailDirectoryName", "Thumbnail");

        int dataset_group_id = H5.H5Gcreate(file_id, "/DataSet",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        for (int r = 0; r < sizes.size(); r++)
        {
            int r_group_id = H5.H5Gcreate(dataset_group_id,
                    "/DataSet" + "/ResolutionLevel " + r,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
                    HDF5Constants.H5P_DEFAULT);

            for (int t = 0; t < imp.getNFrames(); t++)
            {
                int r_t_group_id = H5.H5Gcreate(r_group_id,
                        "TimePoint " + t ,
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
                        HDF5Constants.H5P_DEFAULT);

                for (int c = 0; c < imp.getNChannels(); c++)
                {
                    int r_t_c_group_id = H5.H5Gcreate(r_t_group_id,
                            "Channel " + c ,
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
                            HDF5Constants.H5P_DEFAULT);

                    //
                    // Create link to the actual data in an external data file
                    //
                    String relativePath = "./";
                    String pathNameData = relativePath + fileName
                            + getChannelTimeString( c , t ) + ".h5";

                    H5.H5Lcreate_external(
                            pathNameData, getExternalH5DataPath( r ),
                            r_t_c_group_id, "Data",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

                    //
                    // Create histogram
                    //
                    long[] histo_data = new long[255];
                    Arrays.fill( histo_data, 100 );
                    long[] histo_dims = { histo_data.length };
                    int histo_dataspace_id = H5.H5Screate_simple(
                            histo_dims.length, histo_dims, null);

                    /*
                    imaris expects 64bit unsigned int values:
                    - http://open.bitplane.com/Default.aspx?tabid=268
                    thus, we are using as memory type: H5T_NATIVE_ULLONG
                    and as the corresponding dataset type: H5T_STD_U64LE
                    - https://support.hdfgroup.org/HDF5/release/dttable.html
                    */
                    int histo_dataset_id = H5.H5Dcreate(r_t_c_group_id, "Histogram",
                            HDF5Constants.H5T_STD_U64LE, histo_dataspace_id,
                            HDF5Constants.H5P_DEFAULT,
                            HDF5Constants.H5P_DEFAULT,
                            HDF5Constants.H5P_DEFAULT);

                    H5.H5Dwrite(histo_dataset_id,
                            HDF5Constants.H5T_NATIVE_ULLONG,
                            HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                            HDF5Constants.H5P_DEFAULT, histo_data);

                    H5.H5Dclose(histo_dataset_id);
                    H5.H5Sclose(histo_dataspace_id);

                    // Set channel group attributes
                    //
                    setH5StringAttribute(r_t_c_group_id, "ImageSizeX",
                            String.valueOf(sizes.get(r)[0]) );

                    setH5StringAttribute(r_t_c_group_id, "ImageSizeY",
                            String.valueOf(sizes.get(r)[1]) );

                    setH5StringAttribute(r_t_c_group_id, "ImageSizeZ",
                            String.valueOf(sizes.get(r)[2]) );

                    setH5StringAttribute(r_t_c_group_id, "HistogramMin",
                            String.valueOf( 0.0 ) );

                    setH5StringAttribute(r_t_c_group_id, "HistogramMax",
                            String.valueOf( 65535.0 ) );

                    H5.H5Gclose(r_t_c_group_id);
                }
                H5.H5Gclose(r_t_group_id);
            }
            H5.H5Gclose(r_group_id);
        }
        H5.H5Gclose(dataset_group_id);



        //
        // Create DataSetInfo group
        //

        int dataSetInfo_group_id = H5.H5Gcreate(file_id, "/DataSetInfo",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);


        // Create DataSetInfo/Image group and add attributes
        //
        int dataSetInfo_image_group_id = H5.H5Gcreate(dataSetInfo_group_id, "Image",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);


        // set attributes
        //
        setH5StringAttribute(dataSetInfo_image_group_id, "Description", "description");

        setH5StringAttribute(dataSetInfo_image_group_id, "ExtMax0",
                String.valueOf(1.0 * sizes.get(0)[0] * calibration[0]));

        setH5StringAttribute(dataSetInfo_image_group_id, "ExtMax1",
                String.valueOf(1.0 * sizes.get(0)[1] * calibration[1]));

        setH5StringAttribute(dataSetInfo_image_group_id, "ExtMax2",
                String.valueOf( 1.0 * sizes.get(0)[2] * calibration[2] ) );

        setH5StringAttribute(dataSetInfo_image_group_id, "ExtMin0", "0.0");

        setH5StringAttribute(dataSetInfo_image_group_id, "ExtMin1", "0.0");

        setH5StringAttribute(dataSetInfo_image_group_id, "ExtMin2", "0.0");

        setH5StringAttribute(dataSetInfo_image_group_id, "Unit", "um");

        setH5StringAttribute(dataSetInfo_image_group_id, "X",
                String.valueOf(sizes.get(0)[0]) );

        setH5StringAttribute(dataSetInfo_image_group_id, "Y",
                String.valueOf(sizes.get(0)[1]) );

        setH5StringAttribute(dataSetInfo_image_group_id, "Z",
                String.valueOf(sizes.get(0)[2]) );

        H5.H5Gclose( dataSetInfo_image_group_id );

        //
        // Create DataSetInfo/Channel groups and add attributes
        //

        for (int c = 0; c < imp.getNChannels(); c++)
        {
            int dataSetInfo_channel_group_id = H5.H5Gcreate(dataSetInfo_group_id, "Channel " + c,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

            setH5StringAttribute(dataSetInfo_channel_group_id,
                    "ColorMode", "BaseColor");

            setH5StringAttribute(dataSetInfo_channel_group_id,
                    "ColorOpacity", "1");

            setH5StringAttribute(dataSetInfo_channel_group_id,
                    "Color", "'[1 0 0]'");

            H5.H5Gclose(dataSetInfo_channel_group_id);
        }

        //
        // Create group DataSetInfo/TimeInfo and set attributes
        //

        int dataSetInfo_time_group_id = H5.H5Gcreate(dataSetInfo_group_id,
                "TimeInfo",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        // Set attributes
        //
        setH5StringAttribute(dataSetInfo_time_group_id, "DataSetTimePoints",
                String.valueOf(imp.getNFrames()) );

        setH5StringAttribute(dataSetInfo_time_group_id, "FileTimePoints",
                String.valueOf(imp.getNFrames()) );

        for ( int t = 1; t <= imp.getNFrames(); ++t )
        {
            setH5StringAttribute(dataSetInfo_time_group_id, "TimePoint"+t,
                    "2000-01-01 00:00:00");
        }

        H5.H5Gclose( dataSetInfo_time_group_id );


        //
        // Finish up
        //

        H5.H5Gclose( dataSetInfo_group_id );

        H5.H5Fclose(file_id);


    }


    /**
     * Writes the multi-resolution data file for one channel and time-point
     *
     *
     * HDF5 Data type	    Imaris Image Data type
     * H5T_NATIVE_UCHAR	    8 bit unsigned integer (char)
     * H5T_NATIVE_USHORT    16 bit unsigned integer (short)
     * H5T_NATIVE_UINT32    32 bit unsigned integer (NOT SUPPORTED BY IMAGEJ)
     * H5T_NATIVE_FLOAT	    32 bit floating point
     *
     *
     */
    public void writeChannelTimeH5File(ImagePlus impCT,
                                       ImarisH5Settings imarisH5Settings,
                                       int c, // zero-based
                                       int t, // zero-based
                                       String baseFileName,
                                       String directory)
    {
        ArrayList<long[]> sizes = imarisH5Settings.sizes;
        ArrayList<int[]> binnings = imarisH5Settings.binnings;
        ArrayList<long[]> chunks = imarisH5Settings.chunks;

        //
        // determine data type
        //

        int image_memory_type = 0;
        int image_file_type = 0;

        if ( impCT.getBitDepth() == 8)
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_UCHAR ;
            image_file_type = HDF5Constants.H5T_STD_U8BE;
        }
        else if ( impCT.getBitDepth() == 16)
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_USHORT;
            // image_file_type = HDF5Constants.H5T_STD_U16BE;
            image_file_type = HDF5Constants.H5T_STD_U16LE;

        }
        else if ( impCT.getBitDepth() == 32)
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_FLOAT;
            image_file_type = HDF5Constants.H5T_IEEE_F32BE;
        }
        else
        {
            IJ.showMessage("Image data type is not supported, " +
                    "only 8-bit, 16-bit and 32-bit floating point are possible.");
        }

        //
        // Prepare file
        //
        String fileName = baseFileName + getChannelTimeString( c , t ) + ".h5";
        String filePath = directory + File.separator + fileName;
        File file = new File( filePath );
        if (file.exists()) file.delete();

        int file_id = H5.H5Fcreate(filePath, HDF5Constants.H5F_ACC_TRUNC,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        ImagePlus impBinned = null;

        for ( int r = 0; r < sizes.size(); r++ )
        {

            //
            // Create resolution group
            //
            int resolution_group_id = H5.H5Gcreate(file_id,
                    "Resolution " + r,
                    HDF5Constants.H5P_DEFAULT,
                    HDF5Constants.H5P_DEFAULT,
                    HDF5Constants.H5P_DEFAULT);

            //
            // Create data
            //

            // create data space
            long[] dims = new long[] {
                    sizes.get(r)[2],
                    sizes.get(r)[1],
                    sizes.get(r)[0]
                    };

            int space_id = H5.H5Screate_simple( dims.length, dims, null );

            // create "dataset creation property list" (dcpl)
            int dcpl_id = H5.H5Pcreate( HDF5Constants.H5P_DATASET_CREATE );

            // add chunking to dcpl
            long[] chunk = new long[] {
                    chunks.get(r)[2],
                    chunks.get(r)[1],
                    chunks.get(r)[0],
            };
            H5.H5Pset_chunk(dcpl_id, chunk.length, chunk);

            // create dataset
            int dataset_id = -1;
            try
            {
                dataset_id = H5.H5Dcreate(resolution_group_id,
                        "Data",
                        image_file_type,
                        space_id,
                        HDF5Constants.H5P_DEFAULT,
                        dcpl_id,
                        HDF5Constants.H5P_DEFAULT);
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }

            // do the binning
            //
            int[] binning = new int[3];

            if ( r > 0 )
            {
                for ( int i = 0; i < 3; i++ )
                {
                    binning[i] = binnings.get(r)[i] / binnings.get(r-1)[i] ;
                }
                impBinned  = Utils.bin( impBinned, binning , "binned", "AVERAGE");
            }
            else
            {
                impBinned = impCT;
            }

            // write data
            //
            if ( impCT.getBitDepth() == 8 )
            {
                byte[] data = getByteData( impBinned, c, t );

                H5.H5Dwrite(dataset_id,
                        image_memory_type,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data );

            }
            else if ( impCT.getBitDepth() == 16 )
            {

                short[] data = getShortData( impBinned, c, t );

                H5.H5Dwrite( dataset_id,
                        image_memory_type,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data );

            }
            else if ( impCT.getBitDepth() == 32 )
            {
                float[] data = getFloatData( impBinned, c, t );

                H5.H5Dwrite( dataset_id,
                        image_memory_type,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data );
            }
            else
            {
                IJ.showMessage("Image data type is not supported, " +
                        "only 8-bit, 16-bit and 32-bit are possible.");
            }


            H5.H5Sclose( space_id );
            H5.H5Dclose( dataset_id );
            H5.H5Pclose( dcpl_id );
            H5.H5Gclose( resolution_group_id );

        }


        H5.H5Fclose( file_id );

    }

    private String getExternalH5DataPath( int resolution )
    {
        String s = "Resolution " + resolution + "/Data";
        return ( s );
    }

    private String getChannelTimeString( int c, int t )
    {
        String s = String.format("--C%02d--T%05d", c, t);
        return ( s );
    }

    private short[] getShortData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        short[] data = new short[ size[0] * size[1] * size[2] ];

        int pos = 0;

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);

            System.arraycopy( stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1] );

            pos += size[0] * size[1];
        }

        return ( data );

    }

    private byte[] getByteData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        byte[] data = new byte[ size[0] * size[1] * size[2] ];

        int pos = 0;

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);

            System.arraycopy( stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1] );

            pos += size[0] * size[1];
        }

        return ( data );

    }

    private float[] getFloatData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        float[] data = new float[ size[0] * size[1] * size[2] ];

        int pos = 0;

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);

            System.arraycopy( stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1] );

            pos += size[0] * size[1];
        }

        return ( data );

    }

    private void setH5IntegerAttribute( int dataset_id, String attrName, int[] attrValue )
    {

        long[] attrDims = { attrValue.length };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(attrDims.length, attrDims, null);

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate(dataset_id, attrName,
                        HDF5Constants.H5T_STD_I32BE, dataspace_id,
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute data.
        H5.H5Awrite(attribute_id, HDF5Constants.H5T_NATIVE_INT, attrValue);

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    private void setH5DoubleAttribute( int dataset_id, String attrName, double[] attrValue )
    {

        long[] attrDims = { attrValue.length };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(attrDims.length, attrDims, null);

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate(dataset_id, attrName,
                HDF5Constants.H5T_IEEE_F64BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute data.
        H5.H5Awrite(attribute_id, HDF5Constants.H5T_NATIVE_DOUBLE, attrValue);

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    private void setH5StringAttribute( int dataset_id, String attrName, String attrValue )
    {

        long[] attrDims = { attrValue.getBytes().length };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(attrDims.length, attrDims, null);

        // Create the data space for the attribute.
        //int dataspace_id = H5.H5Screate( HDF5Constants.H5S_SCALAR );

        // Create attribute type
        //int type_id = H5.H5Tcopy( HDF5Constants.H5T_C_S1 );
        //H5.H5Tset_size(type_id, attrValue.length());

        int type_id = HDF5Constants.H5T_C_S1;

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate( dataset_id, attrName,
                type_id, dataspace_id,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute
        H5.H5Awrite(attribute_id, type_id, attrValue.getBytes());

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    private void h5WriteLongArrayListAs32IntArray( int group_id, ArrayList < long[] > list, String name )
    {

        int[][] data = new int[ list.size() ][ list.get(0).length ];

        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[i][j] = (int) list.get(i)[j];
            }
        }

        long[] data_dims = { data.length, data[0].length };

        int dataspace_id = H5.H5Screate_simple( data_dims.length, data_dims, null );

        int dataset_id = H5.H5Dcreate(group_id, name,
                HDF5Constants.H5T_STD_I32LE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite( dataset_id, HDF5Constants.H5T_NATIVE_INT,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data );

        H5.H5Dclose(dataset_id);

        H5.H5Sclose(dataspace_id);

    }

    private void h5WriteLongArrayListAsDoubleArray( int group_id, ArrayList < long[] > list, String name )
    {

        double[] data = new double[list.size() * list.get(0).length];

        int p = 0;
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[ p++ ] = list.get(i)[j];
            }
        }

        long[] data_dims = { list.size(), list.get(0).length };

        int dataspace_id = H5.H5Screate_simple( data_dims.length, data_dims, null );

        int dataset_id = H5.H5Dcreate( group_id, name,
                HDF5Constants.H5T_IEEE_F64BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite( dataset_id, HDF5Constants.H5T_NATIVE_DOUBLE,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data );

        H5.H5Dclose(dataset_id);

        H5.H5Sclose(dataspace_id);

    }

    private void h5WriteIntArrayListAsDoubleArray( int group_id, ArrayList < int[] > list, String name )
    {

        double[] data = new double[list.size() * list.get(0).length];

        int p = 0;
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[ p++ ] = list.get(i)[j];
            }
        }

        long[] data_dims = { list.size(), list.get(0).length };

        int dataspace_id = H5.H5Screate_simple( data_dims.length, data_dims, null );

        int dataset_id = H5.H5Dcreate( group_id, name,
                HDF5Constants.H5T_IEEE_F64BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite( dataset_id, HDF5Constants.H5T_NATIVE_DOUBLE,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data );

        H5.H5Dclose(dataset_id);

        H5.H5Sclose(dataspace_id);

    }

    private String createViewSetupString ( String viewID,
                                           String size,
                                           String spacing,
                                           String channelID,
                                           String channelName)
    {
        String s = String.format(" <ViewSetup>\n"
                        + "  <id>%s</id>\n"
                        + "  <name>%s</name>\n"
                        + "  <size>%s</size>\n"
                        + "  <voxelSize>\n"
                        + "   <unit>pixel</unit>\n"
                        + "   <size>%s</size>\n"
                        + "  </voxelSize>\n"
                        + "  <attributes>\n"
                        //+ "  <illumination>0</illumination>\n"
                        + "   <channel>%s</channel>\n"
                        //+ "  <angle>%s</angle>\n"
                        + "  </attributes>\n"
                        + " </ViewSetup>\n",
                viewID,
                channelName,
                size,
                spacing,
                channelID);

        return ( s );
    }

    private String createSingleAttributeString ( String attrName, String id, String name )
    {
        String s = String.format(" <%s>\n"
                        + "   <id>%s</id>\n"
                        + "   <name>%s</name>\n"
                        + "  </%s>\n",
                attrName,
                id,
                name,
                attrName);

        return ( s );
    }

    private String createRegistrationString ( String time, String id, String parameters )
    {
        String s = String.format("<ViewRegistration timepoint=\"%s\" setup=\"%s\">\n"
                        + " <ViewTransform type=\"affine\">\n"
                        + "  <Name>calibration</Name>\n"
                        + "  <affine>%s</affine>\n"
                        + " </ViewTransform>\n"
                        + "</ViewRegistration>\n",
                time,
                id,
                parameters);

        return ( s );
    }

    private String createBdvXMLString (
            String outFileH5,
            String viewSetups,
            String channels,
            String firstT,
            String lastT,
            String registrations
    )
    {
        String s = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<SpimData version=\"0.2\"> \n"
                        + "<BasePath type=\"relative\">.</BasePath> \n"
                        + "<SequenceDescription> \n"
                        + " <ImageLoader format=\"bdv.hdf5\"> \n"
                        + "  <hdf5 type=\"relative\">%s</hdf5> \n"
                        + " </ImageLoader> \n"
                        + "<ViewSetups> \n"
                        + "%s"
                        //+ " <Attributes name=\"illumination\"> \n"
                        //+ "  <Illumination> \n"
                        //+ "   <id>0</id> \n"
                        //+ "   <name>0</name> \n"
                        //+ "  </Illumination> \n"
                        //+ " </Attributes> \n"
                        + " <Attributes name=\"channel\"> \n"
                        + "%s"
                        + " </Attributes> \n"
                        //+ " <Attributes name=\"angle\"> \n"
                        //+ "   %s \n"
                        //+ " </Attributes> \n"
                        + "</ViewSetups> \n"
                        + "<Timepoints type=\"range\"> \n"
                        + " <first>%s</first> \n"
                        + " <last>%s</last> \n"
                        + "</Timepoints> \n"
                        + "</SequenceDescription> \n"
                        + "<ViewRegistrations> \n"
                        + "%s"
                        + "</ViewRegistrations> \n"
                        + "</SpimData>\n",
                outFileH5,
                viewSetups,
                channels,
                firstT,
                lastT,
                registrations
        );

        return ( s );
    }

    private void creatingExternalHdf5LinkTest()
    {
        String SOURCE_FILE = "/Users/tischi/Desktop/example-data/imaris-out/master-test.h5";
        String TARGET_FILE = "/Users/tischi/Desktop/example-data/imaris-out/data-test.h5";

        /* Create source file */
        int source_file_id = H5.H5Fcreate(SOURCE_FILE, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        /* Create an external link in the source file pointing to the target group. */
        H5.H5Lcreate_external(TARGET_FILE, "target_group", source_file_id, "ext_link",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        /* Create target file */
        int targ_file_id = H5.H5Fcreate(TARGET_FILE, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        /* Create a group in the target file for the external link to point to. */
        int group_id = H5.H5Gcreate(targ_file_id, "target_group",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        /* Close the group and the target file */
        H5.H5Gclose(group_id);


        /* Now we can use the external link to create a new group inside the
        * target group (even though the target file is closed!).  The external
        * link works just like a soft link.
        */
        group_id = H5.H5Gcreate(source_file_id, "ext_link/new_group",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        /* The group is inside the target file and we can access it normally.
         * Here, group_id and group2_id point to the same group inside the
         * target file.
         */
        int group2_id = H5.H5Gopen(targ_file_id, "target_group/new_group",
                HDF5Constants.H5P_DEFAULT);

        /* Don't forget to close the IDs we opened. */
        H5.H5Gclose(group2_id);
        H5.H5Gclose(group_id);

        H5.H5Fclose(targ_file_id);
        H5.H5Fclose(source_file_id);

        /* The link from the source file to the target file will work as long as
         * the target file can be found.  If the target file is moved, renamed,
         * or deleted in the filesystem, HDF5 won't be able to find it and the
         * external link will "dangle."
         */
    }



}

