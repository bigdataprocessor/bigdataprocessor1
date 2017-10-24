
package bigDataTools;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import net.imglib2.RealInterval;

import java.util.ArrayList;
import java.util.Arrays;

import static bigDataTools.Hdf5Utils.getGroup;
import static bigDataTools.Hdf5Utils.createFile;
import static bigDataTools.Hdf5Utils.setH5StringAttribute;

public abstract class ImarisHeaderWriter {

    final static String G_DATA_SET_INFO = "DataSetInfo";
    final static String G_DATA_SET = "DataSet";
    final static String G_IMAGE = "Image";
    final static String G_TIME_INFO = "TimeInfo";
    final static String G_CHANNEL = "Channel";
    final static String G_TIMEPOINT = "TimePoint";

    final static String G_RESOLUTION_LEVEL = "ResolutionLevel";

    final String[] XYZ = new String[]{"X","Y","Z"};


    public static void write( ImarisDataSetProperties idp,
                              String directory,
                              String filename )
    {

        int file_id = createMasterFile( directory, filename );

        writeDataSetInfoImage( file_id, idp.getDimensions().get(0), idp.getInterval() );
        writeDataSetInfoTimeInfo( file_id, idp.getTimePoints() );
        writeDataSetInfoChannels( file_id, idp.getChannels() );
        writeDataSets( file_id,
                idp.getDimensions().size(),
                idp.getChannels() );

        H5.H5Fclose(file_id);
    }

    private static int createMasterFile( String directory, String filename )
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


    private static void writeDataSets( int file_id,
                                       int numResolutions,
                                       ArrayList < ArrayList < String[] > > dataSets )
    {
        for ( int t = 0; t < dataSets.size(); ++t )
        {
            for ( int c = 0; c < dataSets.size(); ++c )
            {
                writeDataSet( file_id, t, c, numResolutions, dataSets.get( t ).get( c ) );
            }
        }
    }

    private static void writeDataSet ( int file_id,
                                       int t,
                                       int c,
                                       int numResolutions,
                                       String[] dirFileGroup )
    {

        for (int r = 0; r < numResolutions; ++r )
        {
            int group_id = getGroup( file_id, G_DATA_SET
                    + "/" + G_RESOLUTION_LEVEL
                    + "/" + G_TIMEPOINT + " " + t );

            H5.H5Lcreate_external(
                    "./" + dirFileGroup[ 1 ],
                    dirFileGroup[ 2 ],
                    group_id, G_CHANNEL + " " + c,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT );

            H5.H5Gclose( group_id );
        }

    }


    private static void writeDataSetInfoImage( int file_id, long[] dimensions, RealInterval interval )
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

    private static void writeDataSetInfoTimeInfo( int file_id, ArrayList < String > times)
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

    private static void addDataSetInfoChannel( int file_id, int id, String color )
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

    private static void writeDataSetInfoChannels( int file_id, ArrayList<String> channels )
    {
        for ( int c = 0; c < channels.size(); ++c )
        {
            addDataSetInfoChannel( c, channels.get( c ) );
        }
    }

}
