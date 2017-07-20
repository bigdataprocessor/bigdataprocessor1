package bigDataTools.testing;

import bigDataTools.VirtualStackOfStacks.FileInfoSer;
import bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import bigDataTools.dataStreamingTools.DataStreamingTools;
import bigDataTools.utils.ImageDataInfo;
import ij.IJ;
import ij.ImagePlus;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by tischi on 04/06/17.
 */
public class TestStuff {

    public static void main(String[] args)
    {

        DataStreamingTools dst = new DataStreamingTools();
        String namingPattern = "classified--C<C00-00>--T<T00000-00000>--Z<Z00000-00900>.tif";
        bigDataTools.utils.ImageDataInfo imageDataInfo = new ImageDataInfo();
        String directory = "/Users/tischi/Desktop/vss-test/";
        ImagePlus classifiedImage = dst.openFromDirectory(directory, namingPattern, "None", "data", imageDataInfo, 10, false, true);
        new ij.ImageJ();
        classifiedImage.show();
    }

}
