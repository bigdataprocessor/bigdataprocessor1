package bigDataTools;

import bigDataTools.utils.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

public abstract class ImarisUtils {

    public final static String IMAGE = "Image";
    public final static String DATA_SET = "DataSet";
    public final static String DATA = "Data";
    public final static String TIME_INFO = "TimeInfo";
    public final static String CHANNEL = "Channel ";
    public final static String TIME_POINT = "TimePoint ";
    public final static String TIME_POINT_ATTRIBUTE = "TimePoint";
    public final static String HISTOGRAM = "Histogram";


    public final static String RESOLUTION_LEVEL = "ResolutionLevel ";
    public final static String[] XYZ = new String[]{"X","Y","Z"};
    public final static String DATA_SET_INFO = "DataSetInfo";
    public final static String COLOR = "Color";

    public final static int DIRECTORY = 0;
    public final static int FILENAME = 1;
    public final static int GROUP = 2;
    public final static long MIN_VOXELS = 1024 * 1024;



    public static ArrayList< File > getMasterFilesInFolder(
            String directory,
            int masterLevel)
    {

        File dir = new File(".");
        File [] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("master"+masterLevel+".ims");
            }
        });

        ArrayList < File > masterFiles = new ArrayList<>();
        for (File imsMasterFile : files)
        {
            masterFiles.add ( imsMasterFile );
        }

        return ( masterFiles );
    }
}
