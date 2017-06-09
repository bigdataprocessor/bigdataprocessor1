package bigDataTools.tests;

import bigDataTools.VirtualStackOfStacks.FileInfoSer;
import bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import ij.ImagePlus;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Created by tischi on 04/06/17.
 */
public class CreateEmptyVSS {

    public static void main(String[] args)
    {
        String directory = "/Users/tischi/Desktop/vss-test/";
        int nC = 1;
        int nT = 1;
        int nZ = 11;
        int nX = 200;
        int nY = 200;
        int bitDepth = 8;
        String fileType = "single tif";
        String[] channelFolders = null;
        String fileList[][][] = new String[nC][nT][nZ];
        String h5DataSet = "";

        VirtualStackOfStacks vss = new VirtualStackOfStacks(
                directory,
                channelFolders,
                fileList,
                nC, nT, nX, nY, nZ, bitDepth,
                fileType, h5DataSet);

        vss.setImageBaseName("image");

        byte[] pixels = new byte[nX*nY];
        Arrays.fill(pixels, (byte) 255);
        //vss.setAndSavePixels(pixels, 5);

        ImagePlus imp = new ImagePlus("", vss);

        // start ImageJ
        new ij.ImageJ();

        imp.show();
    }

}
