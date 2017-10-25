package bigDataTools.bigDataTracker;

public abstract class ImarisUtils {

    public final static String IMAGE = "Image";
    public final static String DATA_SET = "DataSet";
    public final static String DATA = "Data";
    public final static String TIME_INFO = "TimeInfo";
    public final static String CHANNEL = "Channel ";
    public final static String TIMEPOINT = "TimePoint ";
    public final static String TIMEPOINT_ATTRIBUTE = "TimePoint";

    public final static String RESOLUTION_LEVEL = "ResolutionLevel ";
    public final static String[] XYZ = new String[]{"X","Y","Z"};
    public final static String DATA_SET_INFO = "DataSetInfo";
    public final static String COLOR = "Color";

    public final static int DIRECTORY = 0;
    public final static int FILENAME = 1;
    public final static int GROUP = 2;
    public final static long MIN_VOXELS = 1024 * 1024;


    public static String[] createExternalDataSet( String directory,
                                           String filename,
                                           int r, int t, int c)
    {
        String[] dataSet = new String[3];

        dataSet[ DIRECTORY ] = directory;
        dataSet[ FILENAME ] = filename;
        dataSet[ GROUP ] = DATA_SET
                + RESOLUTION_LEVEL + r
                + TIMEPOINT + t
                + CHANNEL + c
                + DATA;

        return dataSet;
    }

}
