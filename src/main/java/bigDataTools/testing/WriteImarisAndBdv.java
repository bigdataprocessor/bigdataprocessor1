package bigDataTools.testing;

import bigDataTools.HDF5Writer;
import ij.IJ;
import ij.ImagePlus;

/**
 * Created by tischi on 11/09/17.
 */
public class WriteImarisAndBdv {

    public static void main(String args[]) throws Exception {

        new ij.ImageJ();

        //IJ.open("/Users/tischi/Desktop/example-data/embryo-1ch-2tp.tif");
        IJ.open("/Users/tischi/Desktop/example-data/3d-embryo/15-08-19_H2BLuVeLuE65-2views_1_G1_Average_E1_ch0_t000.tif");
        ImagePlus imp = IJ.getImage();
        createImarisOutput(imp);

    }

    public static void createImarisOutput(ImagePlus imp)
    {
        HDF5Writer hdf5Writer = new HDF5Writer();
        hdf5Writer.saveAsImarisAndBdv( imp );
    }

}
