package bigDataTools;

import ij.ImagePlus;

import java.util.ArrayList;

/**
 * Created by tischi on 13/07/17.
 */
public class HDF5Writer {




    public void run(){

        // setImarisResolutionLevels();
        // for loop time and channel: writeH5fileWithPyramid
        //

    }

    /**
     * Write *.ims master files for Imaris
     */
    public void writeH5ImarisMaster(ArrayList<String> datasets,
                                    ArrayList<String> fileNames)
    {

    }

    /**
     * Write *.xml and *.h5 master files for BigDataViewer
     */
    public void writeH5BdvMaster()
    {

    }


    /**
     * Writes an hdf5 file with resolution pyramid for the
     * given frame and channel
     */
    public void writeH5fileWithPyramid(ImagePlus imp,
                                       int frame,
                                       int channel,
                                       ArrayList<int[]> binning,
                                       ArrayList<String> datasets,
                                       String fileName,
                                       String directory)
    {



    }


    public void setImarisResolutionLevels(ImagePlus imp,
                                     ArrayList<int[]> binning,
                                     ArrayList<String> datasets)
    {

        // *2 = byteDepth
    }

}
