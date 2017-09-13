package bigDataTools;

import ij.IJ;
import ij.ImagePlus;
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

    public void saveAsImaris(ImagePlus imp){

        ArrayList<int[]> binnings = new ArrayList<>();
        ArrayList<int[]> sizes = new ArrayList<>();
        ArrayList<int[]> chunks = new ArrayList<>();
        ArrayList<String> datasets = null;


        creatingExternalHdf5LinkTest();

        //
        // Determine resolution levels to be compatible with Imaris
        //
        setImarisResolutionLevelsAndChunking(imp, binnings, sizes, chunks, datasets);

        IJ.log("sizes:");
        logArrayList(sizes);

        IJ.log("binnings:");
        logArrayList(binnings);

        IJ.log("chunks:");
        logArrayList(chunks);

        //
        // Save master files
        //
        String directory = "/Users/tischi/Desktop/example-data/imaris-out/";
        String fileName = "aaa";
        writeImarisMasterFile(imp, sizes, fileName, directory);


    }


    public void setImarisResolutionLevelsAndChunking(ImagePlus imp,
                                                     ArrayList<int[]> binnings,
                                                     ArrayList<int[]> sizes,
                                                     ArrayList<int[]> chunks,
                                                     ArrayList<String> datasets)
    {
        long minVoxelVolume = 1024 * 1024;

        int[] size = new int[3];
        size[0] = imp.getWidth();
        size[1] = imp.getHeight();
        size[2] = imp.getNSlices();

        sizes.add( size );

        binnings.add( new int[]{1,1,1} );

        chunks.add(new int[]{4, 32, 32});

        long voxelVolume = 0;
        int iResolution = 0;

        do
        {
            int[] lastSize = sizes.get( iResolution );
            int[] lastBinning = binnings.get( iResolution );

            int[] newSize = new int[3];
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
            chunks.add( new int[]{16,16,16} );

            voxelVolume = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        } while ( 2 * voxelVolume > minVoxelVolume );

    }


    /**
     * Write *.xml and *.h5 master files for BigDataViewer
     */
    public void writeBdvMasterH5()
    {

    }


    /**
     * Writes the Imaris master file
     * - https://support.hdfgroup.org/HDF5/doc/RM/RM_H5L.html#Link-CreateExternal
     * - https://support.hdfgroup.org/ftp/HDF5/current/src/unpacked/examples/h5_extlink.c
     */
    public void writeImarisMasterFile(ImagePlus imp,
                                      ArrayList<int[]> sizes,
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

                for (int c = 0; c < imp.getNFrames(); c++)
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
                    //setH5IntegerAttribute(group_id, "ImageSizeX", new int[]{sizes.get(r)[0]});
                    /*
                    setH5IntegerAttribute(group_id, "ImageSizeY", sizes.get(r)[1]);
                    setH5IntegerAttribute(group_id, "ImageSizeZ", sizes.get(r)[2]);
                    setH5IntegerAttribute(group_id, "HistogramMin", 0);
                    setH5IntegerAttribute(group_id, "HistogramMax", 255);
                    */

                    H5.H5Gclose(resolution_time_channel_group_id);
                }
                H5.H5Gclose(resolution_time_group_id);
            }
            H5.H5Gclose(resolution_group_id);
        }
        H5.H5Gclose(dataset_group_id);

        /*
        //
        // Configure group: DataSetInfo
        //

        int dataSetInfo_group_id = H5.H5Gcreate(file_id, "/DataSetInfo",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        //
        // Configure group: DataSetInfo/Image
        //

        H5.H5Gcreate(dataSetInfo_group_id, "/Image",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);


        //
        // Configure group: DataSetInfo/Channel
        //

        H5.H5Gcreate(dataSetInfo_group_id, "/Channel",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);


        //
        // Configure group: DataSetInfo/TimeInfo
        //

        H5.H5Gcreate(dataSetInfo_group_id, "/TimeInfo",
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        */

        //
        // Finish up
        //

        H5.H5Fclose(file_id);


    }



    /**
     * Writes the multi-resolution data file for one channel and time-point
     */
    public void writeChannelTimeH5File(ImagePlus imp,
                                       ArrayList<int[]> sizes,
                                       String fileName,
                                       String directory)
    {

        /*
        //
        // Prepare channel time file
        //
        String filePathCT = directory + File.separator + fileName + "-C01-T01.h5";
        File fileCT = new File(filePathCT);
        if (fileCT.exists()) fileCT.delete();
        int ctFileID = H5.H5Fcreate(filePathCT, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants
                .H5P_DEFAULT);

        //
        // Create corresponding group in data file
        //
        String groupData = "/ResolutionLevel " + r;
        int groupDataID = H5.H5Gcreate(dataFileIDsChannelTime[c][t], groupData,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        //
        // Create data file
        //
        String fileNameData = fileName + "--C" + c + "--T" + t + ".h5";
        String filePathData = directory + File.separator + fileNameData;

        File fileCT = new File(filePathData);
        if (fileCT.exists()) fileCT.delete();

        int dataFileID = H5.H5Fcreate(filePathData,
                HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        dataFileIDsChannelTime[c][t] = dataFileID;
        */

    }

    private void setH5IntegerAttribute( int dataset_id, String attrName, int[] attrValue )
    {

        long[] attrDims = { 1 };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(1, attrDims, null);

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate(dataset_id, attrName,
                        HDF5Constants.H5T_STD_I32BE, dataspace_id,
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute data.
        H5.H5Awrite(attribute_id, HDF5Constants.H5T_NATIVE_INT, attrValue);

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    private void setH5StringAttribute( int dataset_id, String attrName, String attrValue )
    {
        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate(HDF5Constants.H5S_SCALAR);

        // Create attribute type
        int type_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
        H5.H5Tset_size(type_id, attrValue.length());

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate(dataset_id, attrName,
                type_id, dataspace_id,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute
        byte[] byteArray = attrValue.getBytes();
        H5.H5Awrite(attribute_id, type_id, byteArray);

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }


    private void logArrayList( ArrayList<int[]> arrayList )
    {
        for ( int[] entry : arrayList )
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

