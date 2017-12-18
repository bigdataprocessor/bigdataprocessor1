package de.embl.cba.bigDataTools.testing;

import ij.ImagePlus;

/**
 * Created by tischi on 11/09/17.
 */
public class testHDF5 {

    public static void main(String args[]) throws Exception {

        //Hdf55BdvImarisReaderWriter hdf5Writer = new Hdf55BdvImarisReaderWriter();
        //hdf5Writer.analyzeImarisFile( "/Users/tischi/Desktop/Ashna-Seg-Test/ashna-tmp/saved-from-imaris",
         //       "im--bin-2-2-1.h5");

        //hdf5Writer.analyzeImarisFile("/Users/tischi/Desktop/Ashna-Seg-Test/ashna-tmp/imaris",
        //        "im--C00--T00001.h5");

        //readImarisFileHeader();
        /*
        new ij.ImageJ();

        IJ.open("/Users/tischi/Desktop/example-data/embryo-1ch-2tp.tif");
        //IJ.open("/Users/tischi/Desktop/example-data/3d-embryo/15-08-19_H2BLuVeLuE65-2views_1_G1_Average_E1_ch0_t000.tif");
        ImagePlus imp = IJ.getImage();
        createImarisOutput( imp );
        */

    }



    public static void createImarisOutput(ImagePlus imp)
    {
        String fileBaseName = "test";
        String directory = "/Users/tischi/Desktop/example-data/imaris-out/";

    }



}
