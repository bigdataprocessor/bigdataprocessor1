package bigDataTools;

import bigDataTools.utils.Utils;

import java.util.HashMap;
import java.util.Map;

import static bigDataTools.ImarisUtils.*;

public class CTRDataSets {

    Map< String, DataSet > ctrDataSets = new HashMap<>();

    public void add( int c, int t, int r, DataSet dataSetReference )
    {
        ctrDataSets.put( getKey(c,t,r), dataSetReference );
    }

    public DataSet get( int c, int t, int r )
    {

        return ( ctrDataSets.get( getKey(c,t,r) ) );
    }

    private String getKey( int c, int t, int r )
    {
        String ctr = "c"+c+"t"+t+"r"+r;
        return ( ctr );
    }

    public void addImaris(  int cIntern, int cExtern, int t, int r,
                            String directory,
                            String filename )
    {
        DataSet dataSet = new DataSet(
                directory,
                filename,
                DATA_SET
                        + "/" + RESOLUTION_LEVEL + r
                        + "/" + TIME_POINT + t
                        + "/" + CHANNEL + cExtern);

        add( cIntern, t, r, dataSet );

    }

    public void addExternal( int c, int t, int r,
                             String directory,
                             String filename )
    {
        DataSet dataSet = new DataSet(
                directory,
                filename + Utils.getChannelTimeString( t, c ) + ".h5",
                 RESOLUTION_LEVEL + r );

        add( c, t, r, dataSet );

    }



}
