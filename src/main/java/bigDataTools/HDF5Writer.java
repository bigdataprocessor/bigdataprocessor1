package bigDataTools;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by tischi on 13/07/17.
 */
public class HDF5Writer {

    public HDF5Writer()
    {

    }

    public void saveAsImarisAndBdv(ImagePlus imp)
    {

        ArrayList<int[]> binnings = new ArrayList<>();
        ArrayList<long[]> sizes = new ArrayList<>();
        ArrayList<long[]> chunks = new ArrayList<>();
        ArrayList<String> datasets = null;


        String directory = "/Users/tischi/Desktop/example-data/imaris-out/";
        String baseFileName = "aaa";

        double[] calibration = new double[]{0.5,0.5,0.5};

        //
        // IMARIS
        //

        // Determine resolution levels that are compatible with Imaris
        //
        setImarisResolutionLevelsAndChunking(imp, binnings, sizes, chunks, datasets);

        IJ.log("sizes:");
        logArrayList( sizes );

        IJ.log("chunks:");
        logArrayList( chunks );

        // Write imaris master file
        //
        writeImarisMasterFile(imp, sizes, calibration, baseFileName, directory);


        // Write imaris master file
        //
        writeBdvMasterFiles(imp, sizes, chunks, calibration, baseFileName, directory);



        //
        // Write data files, one per channel and time-point
        //
        for ( int c = 0; c < imp.getNChannels(); c++ )
        {
            for (int t = 0; t < imp.getNFrames(); t++)
            {
                writeChannelTimeH5File(imp, sizes, binnings, chunks, c, t, baseFileName, directory);
            }
        }

        IJ.log("Files are written to: " + directory);

    }

    public void setImarisResolutionLevelsAndChunking(ImagePlus imp,
                                                     ArrayList<int[]> binnings,
                                                     ArrayList<long[]> sizes,
                                                     ArrayList<long[]> chunks,
                                                     ArrayList<String> datasets)
    {
        long minVoxelVolume = 1024 * 1024;

        long[] size = new long[3];
        size[0] = imp.getWidth();
        size[1] = imp.getHeight();
        size[2] = imp.getNSlices();

        sizes.add( size );

        binnings.add( new int[]{1,1,1} );

        chunks.add(new long[]{4, 32, 32});

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
            chunks.add( new long[]{16,16,16} );

            voxelVolume = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        } while ( 2 * voxelVolume > minVoxelVolume );

    }


    /**
     * Write *.xml and *.h5 master files for BigDataViewer
     */
    public void writeBdvMasterFiles(ImagePlus imp,
                                    ArrayList<long[]> sizes,
                                    ArrayList<long[]> chunks,
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
            int group_id = H5.H5Gcreate(file_id, group,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

            h5WriteLongArrayListAsDoubleArray(group_id, sizes, "resolutions");

            h5WriteLongArrayListAs32IntArray(group_id, chunks, "subdivisions");

            H5.H5Gclose( group_id );

        }

        for ( int t = 0; t < imp.getNFrames(); t++ )
        {
            String time_group = String.format("t%05d", t);
            int time_group_id = H5.H5Gcreate(file_id, time_group,
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

                    // create external link, called "cells" to data

                    H5.H5Gclose( resolution_group_id );
                }
                H5.H5Gclose( channel_group_id );
            }
            H5.H5Gclose( time_group_id );
        }


        // write xml master file
        //

        String filePathXML = directory + File.separator + fileName + "--bdv.xml";
        File fileMasterXML = new File( filePathXML );
        if (fileMasterXML.exists()) fileMasterXML.delete();

        StringBuilder sb = new StringBuilder();

    }


    private void h5WriteLongArrayListAs32IntArray( int group_id, ArrayList < long[] > list, String name )
    {

        double[][] data = new double[list.size()][list.get(0).length];
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[i][j] = list.get(i)[j];
            }
        }

        long[] data_dims = { data.length, data[0].length };
        int dataspace_id = H5.H5Screate_simple(data_dims.length, data_dims, null);
        int dataset_id = H5.H5Dcreate(group_id, name,
                HDF5Constants.H5T_STD_I32BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);
        H5.H5Dwrite(dataspace_id, HDF5Constants.H5T_NATIVE_INT32,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data);

        H5.H5Dclose(dataset_id);
        H5.H5Sclose(dataspace_id);
    }

    private void h5WriteLongArrayListAsDoubleArray( int group_id, ArrayList < long[] > list, String name )
    {

        double[][] data = new double[list.size()][list.get(0).length];
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[i][j] = list.get(i)[j];
            }
        }

        long[] data_dims = {data.length, data[0].length};
        int dataspace_id = H5.H5Screate_simple(data_dims.length, data_dims, null);
        int dataset_id = H5.H5Dcreate(group_id, name,
                HDF5Constants.H5T_STD_I64LE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);
        H5.H5Dwrite(dataspace_id, HDF5Constants.H5T_NATIVE_DOUBLE,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data);

        H5.H5Dclose(dataset_id);
        H5.H5Sclose(dataspace_id);
    }

    private String createViewSetupString ( String viewID, String spacing, String channel, String angle )
    {
        String s = String.format("<ViewSetup>\n"
                        + "<id>%s</id>\n"
                        + "<size>%s</size>\n"
                        + "<voxelSize>\n"
                        + "<unit>um</unit>"
                        + "<size>%s</size>\n"
                        + "</voxelSize>\n"
                        + "<illumination>0</illumination>\n"
                        + "<channel>%s</channel>\n"
                        + "<angle>%s</angle>\n"
                        + "</attributes>\n"
                        + "</ViewSetup>\n",
                        viewID,
                        spacing,
                        channel,
                        angle);

        return ( s );
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
        String filePathMaster = directory + File.separator + fileName + "--master.h5";
        File fileMaster = new File( filePathMaster );
        if (fileMaster.exists()) fileMaster.delete();

        int file_id = H5.H5Fcreate(filePathMaster, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        setH5StringAttribute(file_id, "DataSetDirectoryName", "DataSet");
        setH5StringAttribute(file_id, "DataSetInfoDirectoryName", "DataSetInfo");
        setH5StringAttribute(file_id, "ImarisDataSet", "ImarisDataSet");
        setH5StringAttribute(file_id, "ImarisVersion", "9.0.0");
        setH5StringAttribute(file_id, "NumberOfDataSets", "9.0.0");
        setH5StringAttribute(file_id, "ThumbnailDirectoryName", "Thumbnail");

        int dataset_group_id = H5.H5Gcreate(file_id, "/DataSet",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        String group;

        for (int r = 0; r < sizes.size(); r++)
        {
            group = "/DataSet" + "/ResolutionLevel" + r;
            int resolution_group_id = H5.H5Gcreate(dataset_group_id, group,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

            for (int t = 0; t < imp.getNFrames(); t++)
            {
                group += "/TimePoint " + t;
                int resolution_time_group_id = H5.H5Gcreate(resolution_group_id, group ,
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

                for (int c = 0; c < imp.getNChannels(); c++)
                {
                    group += "/Channel " + c;
                    int resolution_time_channel_group_id = H5.H5Gcreate(resolution_time_group_id, group ,
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

                    //
                    // Create link for the actual Data to an external data file
                    //
                    String fileNameData = fileName + "--C" + c + "--T" + t + ".h5";
                    H5.H5Lcreate_external(
                            fileNameData, "/ResolutionLevel " + r + "/Data",
                            resolution_time_channel_group_id, "Data",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

                    //
                    // Create histogram
                    //
                    int[] histo_data = {1,1,1};
                    long[] histo_dims = { histo_data.length };
                    int histo_dataspace_id = H5.H5Screate_simple(histo_dims.length, histo_dims, null);

                    /*
                    imaris expects 64bit unsigned int values:
                    - http://open.bitplane.com/Default.aspx?tabid=268
                    thus, we are using as memory type: H5T_NATIVE_ULLONG
                    and as the corresponding file type: H5T_STD_I64LE
                    - https://support.hdfgroup.org/HDF5/release/dttable.html
                    */
                    int histo_dataset_id = H5.H5Dcreate(resolution_time_channel_group_id, "Histogram",
                            HDF5Constants.H5T_STD_I64LE, histo_dataspace_id,
                            HDF5Constants.H5P_DEFAULT,
                            HDF5Constants.H5P_DEFAULT,
                            HDF5Constants.H5P_DEFAULT);
                    H5.H5Dwrite(histo_dataset_id, HDF5Constants.H5T_NATIVE_ULLONG, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                            HDF5Constants.H5P_DEFAULT, histo_data);

                    H5.H5Dclose(histo_dataset_id);
                    H5.H5Sclose(histo_dataspace_id);

                    //
                    // Set data set group attributes
                    //

                    /*
                    setH5IntegerAttribute(resolution_time_channel_group_id, "ImageSizeX", new int[]{sizes.get(r)[0]});
                    setH5IntegerAttribute(resolution_time_channel_group_id, "ImageSizeY", new int[]{sizes.get(r)[1]});
                    setH5IntegerAttribute(resolution_time_channel_group_id, "ImageSizeZ", new int[]{sizes.get(r)[2]});
                    setH5IntegerAttribute(resolution_time_channel_group_id, "HistogramMin", new int[]{0});
                    setH5IntegerAttribute(resolution_time_channel_group_id, "HistogramMax", new int[]{255});
                    */

                    H5.H5Gclose(resolution_time_channel_group_id);
                }
                H5.H5Gclose(resolution_time_group_id);
            }
            H5.H5Gclose(resolution_group_id);
        }
        H5.H5Gclose(dataset_group_id);


        /*
        tmpHierarchy = '.'

        setAttribute(outFile,tmpHierarchy,'DataSetDirectoryName','DataSet')
        setAttribute(outFile,tmpHierarchy,'DataSetInfoDirectoryName','DataSetInfo')
        setAttribute(outFile,tmpHierarchy,'ImarisDataSet','ImarisDataSet')
        # setAttribute(outFile,tmpHierarchy,'ImarisVersion','8.4.0') # renders file unrecognizable
        setAttribute(outFile,tmpHierarchy,'ImarisVersion','5.5.0')
        setAttribute(outFile,tmpHierarchy,'NumberOfDataSets',1)
        setAttribute(outFile,tmpHierarchy,'ThumbnailDirectoryName','Thumbnail')
        */


        //
        // Create DataSetInfo group
        //

        int dataSetInfo_group_id = H5.H5Gcreate(file_id, "/DataSetInfo",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        //
        // Create DataSetInfo/Image group and add attributes
        //

        int dataSetInfo_image_group_id = H5.H5Gcreate(dataSetInfo_group_id, "Image",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);


        setH5StringAttribute(dataSetInfo_image_group_id, "Description", "description");
        /*
        setH5DoubleAttribute(dataSetInfo_image_group_id, "ExtMax1", new double[]{sizes.get(0)[0] * calibration[0]});
        setH5DoubleAttribute(dataSetInfo_image_group_id, "ExtMax0", new double[]{sizes.get(0)[1] * calibration[1]});
        setH5DoubleAttribute(dataSetInfo_image_group_id, "ExtMax2", new double[]{sizes.get(0)[2] * calibration[2]});
        setH5DoubleAttribute(dataSetInfo_image_group_id, "ExtMin1", new double[]{0.0});
        setH5DoubleAttribute(dataSetInfo_image_group_id, "ExtMin0", new double[]{0.0});
        setH5DoubleAttribute(dataSetInfo_image_group_id, "ExtMin2", new double[]{0.0});
        setH5StringAttribute(dataSetInfo_image_group_id, "Unit", "um");
        setH5IntegerAttribute(dataSetInfo_image_group_id, "X", new int[]{sizes.get(0)[0]});
        setH5IntegerAttribute(dataSetInfo_image_group_id, "Y", new int[]{sizes.get(0)[1]});
        setH5IntegerAttribute(dataSetInfo_image_group_id, "Z", new int[]{sizes.get(0)[2]});
        */
        H5.H5Gclose( dataSetInfo_image_group_id );


        //
        // Create DataSetInfo/Channel groups and add attributes
        //

        for (int c = 0; c < imp.getNChannels(); c++)
        {
            int dataSetInfo_channel_group_id = H5.H5Gcreate(dataSetInfo_group_id, "Channel " + c,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

            setH5StringAttribute(dataSetInfo_channel_group_id, "ColorMode", "BaseColor");
            setH5DoubleAttribute(dataSetInfo_channel_group_id, "ColorOpacity", new double[]{1.0});
            setH5DoubleAttribute(dataSetInfo_channel_group_id, "Color", new double[]{1.0, 0.0, 0.0});

            H5.H5Gclose(dataSetInfo_channel_group_id);
        }

        //
        // Create group DataSetInfo/TimeInfo and add attributes
        //

        int dataSetInfo_time_group_id = H5.H5Gcreate(dataSetInfo_group_id, "TimeInfo",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        setH5IntegerAttribute(dataSetInfo_time_group_id, "DataSetTimePoints", new int[]{imp.getNFrames()});
        setH5IntegerAttribute(dataSetInfo_time_group_id, "FileTimePoints", new int[]{imp.getNFrames()});
        setH5StringAttribute(dataSetInfo_time_group_id, "TimePoint1", "2000-01-01 00:00:00");

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
     * H5T_NATIVE_UINT32    32 bit unsigned integer
     * H5T_NATIVE_FLOAT	    32 bit floating point
     *
     *
     */
    public void writeChannelTimeH5File(ImagePlus imp,
                                       ArrayList<long[]> sizes,
                                       ArrayList<int[]> binnings,
                                       ArrayList<long[]> chunks,
                                       int c, // zero-based
                                       int t, // zero-based
                                       String baseFileName,
                                       String directory)
    {


        //
        // determine data type
        //

        int imageData_type_id = 0;

        if ( imp.getBitDepth() == 8)
        {
            imageData_type_id = H5.H5Tcopy( HDF5Constants.H5T_NATIVE_UCHAR );
        }
        else if ( imp.getBitDepth() == 16)
        {
            imageData_type_id = H5.H5Tcopy( HDF5Constants.H5T_NATIVE_USHORT );
        }
        else if ( imp.getBitDepth() == 32)
        {
            imageData_type_id = H5.H5Tcopy( HDF5Constants.H5T_NATIVE_FLOAT );
        }
        else
        {
            IJ.showMessage("Image data type is not supported, " +
                    "only 8-bit, 16-bit and 32-bit floating point are possible.");
        }


        //
        // Prepare file
        //
        String fileName = baseFileName + "--C" + c + "--T" + t + ".h5";
        String filePath = directory + File.separator + fileName;
        File file = new File(filePath);
        if (file.exists()) file.delete();


        int file_id = H5.H5Fcreate(filePath, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants
                .H5P_DEFAULT);

        for ( int r = 0; r < binnings.size(); r++ )
        {

            //
            // Create resolution group
            //
            int resolution_group_id = H5.H5Gcreate(file_id, "Resolution " + r,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

            //
            // Create data
            //

            // create data space
            int space_id = H5.H5Screate_simple( sizes.get(r).length, sizes.get(r), null );

            // create "dataset creation property list" (dcpl)
            int dcpl_id = H5.H5Pcreate( HDF5Constants.H5P_DATASET_CREATE );

            // set chunking
            H5.H5Pset_chunk( dcpl_id, chunks.get(r).length, chunks.get(r) );

            // create dataset
            int dataset_id = H5.H5Dcreate( resolution_group_id, "Data", imageData_type_id, space_id, 0, dcpl_id, 0);

            //
            // write actual voxel data
            //
            if ( imp.getBitDepth() == 8)
            {

            }
            else if ( imp.getBitDepth() == 16)
            {
                short[] data = getShortData(imp, c, t);

                H5.H5Dwrite(dataset_id,
                        imageData_type_id,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data);

            }
            else if ( imp.getBitDepth() == 32)
            {

            }
            else
            {
                IJ.showMessage("Image data type is not supported, " +
                        "only 8-bit, 16-bit and 32-bit floating point are possible.");
            }


            H5.H5Dclose( dataset_id );
            H5.H5Gclose( resolution_group_id );

        }


        H5.H5Fclose( file_id );

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

            System.arraycopy(stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1]);

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

    private void logArrayList( ArrayList<long[]> arrayList )
    {
        for ( long[] entry : arrayList )
        {
            IJ.log( "" + entry[0] + "," + entry[1] + "," + entry[2]);
        }
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

