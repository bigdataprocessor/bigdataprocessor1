package de.embl.cba.bigDataTools.testing;

import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.utils.ImageDataInfo;
import ij.ImagePlus;

/**
 * Created by tischi on 04/06/17.
 */
public class TestStuff {

    public static void main(String[] args)
    {

        DataStreamingTools dst = new DataStreamingTools();
        String namingPattern = "classified--C<C00-00>--T<T00000-00000>--Z<Z00000-00900>.tif";
        ImageDataInfo imageDataInfo = new ImageDataInfo();
        String directory = "/Users/tischi/Desktop/vss-test/";
        ImagePlus classifiedImage = dst.openFromDirectory(directory, namingPattern, "None", "data", imageDataInfo, 10, false, true);
        new ij.ImageJ();
        classifiedImage.show();
    }

}
