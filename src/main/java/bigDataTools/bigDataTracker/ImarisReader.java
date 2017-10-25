package bigDataTools.bigDataTracker;

import ncsa.hdf.hdf5lib.H5;

import java.util.ArrayList;

import static bigDataTools.Hdf5Utils.*;
import static bigDataTools.bigDataTracker.ImarisUtils.*;

public class ImarisReader {

    int file_id;

    public ImarisReader( String directory, String filename )
    {
        file_id = openFile( directory, filename );
    }

    public void closeFile()
    {
        H5.H5Fclose( file_id );
    }

    public ArrayList< String > readChannels( )
    {
        ArrayList < String > channelColors = new ArrayList<>();

        for ( int c = 0; ; ++c )
        {

            String color = readStringAttribute( file_id,
                    DATA_SET_INFO
                            + "/" + CHANNEL + c,
                    COLOR );

            if ( color == null ) break;

            channelColors.add( color );

        }

        return ( channelColors ) ;
    }

    public ArrayList< String > readTimePoints( )
    {
        ArrayList < String > timePoints = new ArrayList<>();

        for ( int t = 0; ; ++t )
        {

            String timePoint = readStringAttribute( file_id,
                    DATA_SET_INFO
                    + "/" + TIME_INFO ,
                    TIMEPOINT_ATTRIBUTE + t );

            if ( timePoint == null ) break;

            timePoints.add( timePoint );

        }

        return ( timePoints ) ;
    }

    public ArrayList< long[] > readDimensions( )
    {
        ArrayList < long[] > dimensions = new ArrayList<>();

        for ( int r = 0; ; ++r )
        {


            String dataSetName = DATA_SET
                    + RESOLUTION_LEVEL + r
                    + TIMEPOINT + 0
                    + CHANNEL + 0
                    + DATA;

            long[] dimension = getDataDimensions( file_id, dataSetName );

            if ( dimension == null ) break;

            dimensions.add( dimension );
        }

        return ( dimensions ) ;
    }

    /*
    public ArrayList< ArrayList < String[] > > readDataSets( int nr, int nc, int nt )
    {
        ArrayList< ArrayList < String[] > > dataSets = new ArrayList<>();

        for ( int r = 0; r < nr  ; ++r )
        {
            for ( int c = 0; c < nc; ++c )
            {
                ArrayList < String[] > timePoints = new ArrayList<>();

                for ( int t = 0; t < nt; ++t )
                {
                    String[] dataSet = new String[3];
                    timePoints.add( dataSet );
                }
            }
        }

            for ( int t )
            String dataSetName = DATA_SET
                    + RESOLUTION_LEVEL + r
                    + TIMEPOINT + 0
                    + CHANNEL + 0
                    + DATA;

            long[] dimension = getDataDimensions( file_id, dataSetName );

            if ( dimension == null ) break;

            dimensions.add( dimension );
        }

        return ( dimensions ) ;
    }
    */



}


