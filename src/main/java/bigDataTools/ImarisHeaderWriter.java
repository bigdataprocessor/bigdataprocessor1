package bigDataTools;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import net.imglib2.RealInterval;

import java.util.ArrayList;
import java.util.Arrays;

import static bigDataTools.Hdf5Utils.getGroup;
import static bigDataTools.Hdf5Utils.createFile;
import static bigDataTools.Hdf5Utils.setH5StringAttribute;

public class ImarisHeaderWriter {

    private int file_id;
    final String G_DATA_SET_INFO = "DataSetInfo";
    final String G_DATA_SET = "DataSet";
    final String G_IMAGE = "Image";
    final String G_TIME_INFO = "TimeInfo";
    final String G_CHANNEL = "Channel";
    final String G_RESOLUTION_LEVEL = "ResolutionLevel";

    final String[] XYZ = new String[]{"X","Y","Z"};

    int file_id;

    public void write( ImarisDataSetProperties idp,
                       String directory,
                       String filename )
    {

        file_id = createMasterFile( directory, filename );

        writeDataSetInfoImage( idp.dimensions.get(0), idp.interval );
        writeDataSetInfoTimeInfo( idp.timePoints );
        writeDataSetInfoChannels( idp.channels);

        H5.H5Fclose(file_id);
    }

    private int createMasterFile( String directory, String filename )
    {

        int file_id = createFile( directory, filename );

        setH5StringAttribute(file_id, "DataSetDirectoryName", G_DATA_SET );
        setH5StringAttribute(file_id, "DataSetInfoDirectoryName", G_DATA_SET_INFO );
        setH5StringAttribute(file_id, "ImarisDataSet", "ImarisDataSet");
        setH5StringAttribute(file_id, "ImarisVersion", "5.5.0");  // file-format version
        setH5StringAttribute(file_id, "NumberOfDataSets", "1");
        setH5StringAttribute(file_id, "ThumbnailDirectoryName", "Thumbnail");

        return ( file_id );

    }



    private void addExternalDataSet ( int t, int c,
                                     String directory, String filename,
                                     String h5DataPathInExternalFile )
    {

    }


    public void writeDataSetInfoImage( long[] dimensions, RealInterval interval )
    {

        int group_id = getGroup( file_id, G_DATA_SET_INFO + "/" +  G_IMAGE );

        // set attributes
        //
        setH5StringAttribute(group_id, "Description", "description");

        setH5StringAttribute(group_id, "Unit", "um");

        for ( int d = 0; d < 3; ++d )
        {
            // physical interval
            setH5StringAttribute( group_id, "ExtMax" + d,
                    String.valueOf( interval.realMax( d ) ) );
            setH5StringAttribute( group_id, "ExtMin" + d,
                    String.valueOf( interval.realMin( d ) ) );

            // number of pixels
            setH5StringAttribute(group_id, XYZ[d],
                    String.valueOf( dimensions[d] ) );

        }

        H5.H5Gclose( group_id );

    }

    public void writeDataSetInfoTimeInfo( ArrayList < String > times)
    {

        int group_id = getGroup( file_id, G_DATA_SET_INFO + "/" + G_TIME_INFO );

        // Set attributes
        //
        setH5StringAttribute(group_id, "DataSetTimePoints",
                String.valueOf( times.size() ) );

        setH5StringAttribute(group_id, "FileTimePoints",
                String.valueOf( times.size() ) );

        for ( int t = 0; t < times.size(); ++t )
        {
            setH5StringAttribute(group_id, "TimePoint"+(t+1),
                    times.get( t ));
        }

        H5.H5Gclose( group_id );

    }

    public void addDataSetInfoChannel( int id, String color )
    {

        int group_id = getGroup( file_id, G_DATA_SET_INFO + "/" + G_CHANNEL + " " + id );

        setH5StringAttribute(group_id,
                "ColorMode", "BaseColor");

        setH5StringAttribute(group_id,
                "ColorOpacity", "1");

        setH5StringAttribute(group_id,
                "Color", String.format("'[%s]'", color) );

        H5.H5Gclose( group_id );
    }

    public void writeDataSetInfoChannels( ArrayList<String> channels )
    {
        for ( int c = 0; c < channels.size(); ++c )
        {
            addDataSetInfoChannel( c, channels.get( c ) );
        }
    }

    public void addDataSets( int file_id,  )
    {

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
                    String pathNameData = relativePath + filename
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
                            String.valueOf( histogramMax ) );

                    H5.H5Gclose(r_t_c_group_id);
                }
                H5.H5Gclose(r_t_group_id);
            }
            H5.H5Gclose(r_group_id);
        }
        H5.H5Gclose(dataset_group_id);


    }
}
