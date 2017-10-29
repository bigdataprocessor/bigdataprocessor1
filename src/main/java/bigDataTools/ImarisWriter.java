
package bigDataTools;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import net.imglib2.RealInterval;

import java.io.File;
import java.util.ArrayList;

import static bigDataTools.Hdf5Utils.createGroup;
import static bigDataTools.Hdf5Utils.createFile;
import static bigDataTools.Hdf5Utils.writeStringAttribute;

public abstract class ImarisWriter {


    public static void writeCombinedHeader( ArrayList < File > masterFiles, String filename )
    {

        ImarisDataSet imarisDataSet = new ImarisDataSet();

        for ( int f = 0; f < masterFiles.size(); ++f )
        {
            if ( f == 0 )
            {
                imarisDataSet.setFromImaris( masterFiles.get( f ) );
            }
            else
            {
                imarisDataSet.addChannelsFromImaris( masterFiles.get( f ) );
            }
        }

        writeHeader( imarisDataSet, masterFiles.get( 0 ).getParent(), filename );

    }


    public static void writeHeader( ImarisDataSet idp,
                                    String directory,
                                    String filename )
    {

        int file_id = createMasterFile( directory, filename );

        writeDataSetInfoImage( file_id, idp.getDimensions(), idp.getInterval() );
        writeDataSetInfoTimeInfo( file_id, idp.getTimePoints() );
        writeDataSetInfoChannels( file_id, idp.getChannels() );
        writeDataSets( file_id, idp );

        H5.H5Fclose(file_id);
    }

    private static int createMasterFile( String directory, String filename )
    {

        int file_id = createFile( directory, filename );

        writeStringAttribute(file_id, "DataSetDirectoryName", ImarisUtils.DATA_SET );
        writeStringAttribute(file_id, "DataSetInfoDirectoryName", ImarisUtils.DATA_SET_INFO );
        writeStringAttribute(file_id, "ImarisDataSet", "ImarisDataSet");
        writeStringAttribute(file_id, "ImarisVersion", "5.5.0");  // file-format version
        writeStringAttribute(file_id, "NumberOfDataSets", "1");
        writeStringAttribute(file_id, "ThumbnailDirectoryName", "Thumbnail");

        return ( file_id );

    }


    private static void writeDataSets( int file_id,
                                       ImarisDataSet idp)
    {
        for ( int t = 0; t < idp.getTimePoints().size(); ++t )
        {
            for ( int c = 0; c < idp.getChannels().size(); ++c )
            {
                writeDataSet( file_id, t, c, idp );
            }
        }
    }

    private static void writeDataSet ( int file_id,
                                       int t,
                                       int c,
                                       ImarisDataSet imarisDataSet )
    {

        for (int r = 0; r < imarisDataSet.getDimensions().size(); ++r )
        {
            int group_id = createGroup( file_id,
                    ImarisUtils.DATA_SET
                            + "/" + ImarisUtils.RESOLUTION_LEVEL + r
                            + "/" + ImarisUtils.TIME_POINT + t );

            H5.H5Lcreate_external(
                    "./" + imarisDataSet.getDataSetFilename( c, t, r ),
                    imarisDataSet.getDataSetGroupName( c, t, r ),
                    group_id,
                    ImarisUtils.CHANNEL + c,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT );

            H5.H5Gclose( group_id );
        }

    }


    private static void writeDataSetInfoImage( int file_id,
                                               ArrayList< long [] > dimensions,
                                               RealInterval interval )
    {

        int group_id = createGroup( file_id, ImarisUtils.DATA_SET_INFO + "/" +  ImarisUtils.IMAGE );

        // set attributes
        //
        writeStringAttribute(group_id, "Description", "description");

        writeStringAttribute(group_id, "Unit", "um");

        for ( int d = 0; d < 3; ++d )
        {
            // physical interval
            writeStringAttribute( group_id, "ExtMax" + d,
                    String.valueOf( interval.realMax( d ) ) );
            writeStringAttribute( group_id, "ExtMin" + d,
                    String.valueOf( interval.realMin( d ) ) );

            // number of pixels
            writeStringAttribute( group_id, ImarisUtils.XYZ[d],
                    String.valueOf( dimensions.get(0)[d] ) );

        }


        for ( int r = 0; r < dimensions.size(); ++r )
        {
            for ( int d = 0; d < 3; ++d )
            {
                // number of pixels at different resolutions
                writeStringAttribute( group_id, ImarisUtils.XYZ[d] + d,
                        String.valueOf( dimensions.get(0)[d] ) );
            }
        }

        writeStringAttribute( group_id, ImarisUtils.RESOLUTION_LEVELS_ATTRIBUTE,
                              String.valueOf( dimensions.size() ));


        H5.H5Gclose( group_id );

    }

    private static void writeDataSetInfoTimeInfo( int file_id, ArrayList < String > times)
    {

        int group_id = createGroup( file_id, ImarisUtils.DATA_SET_INFO + "/" + ImarisUtils.TIME_INFO );

        // Set attributes
        //
        writeStringAttribute(group_id, "DataSetTimePoints",
                String.valueOf( times.size() ) );

        writeStringAttribute(group_id, "FileTimePoints",
                String.valueOf( times.size() ) );

        for ( int t = 0; t < times.size(); ++t )
        {
            writeStringAttribute(group_id, "TimePoint"+(t+1),
                    times.get( t ));
        }

        H5.H5Gclose( group_id );

    }

    private static void writeDataSetInfoChannel( int file_id, int c, String color )
    {

        int group_id = createGroup( file_id, ImarisUtils.DATA_SET_INFO + "/" + ImarisUtils.CHANNEL + c );

        writeStringAttribute(group_id,
                "ColorMode", "BaseColor");

        writeStringAttribute(group_id,
                "ColorOpacity", "1");

        writeStringAttribute(group_id,
                "Color", color);

        H5.H5Gclose( group_id );
    }

    private static void writeDataSetInfoChannels( int file_id, ArrayList<String> channels )
    {
        for ( int c = 0; c < channels.size(); ++c )
        {
            writeDataSetInfoChannel( file_id, c, channels.get( c ) );
        }
    }

}
