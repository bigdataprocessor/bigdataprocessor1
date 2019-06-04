package de.embl.cba.bigdataprocessor.virtualstack2;

import de.embl.cba.bigdataprocessor.logging.IJLazySwingLogger;
import de.embl.cba.bigdataprocessor.logging.Logger;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import de.embl.cba.bigdataprocessor.utils.Point3D;


public class LoadFrameFromVS2IntoRAM implements Runnable
{
    ImagePlus imp;
    int t;
    ImagePlus impRAM;
    Logger logger = new IJLazySwingLogger();
    int nThreads;

    /**
     *
     * @param imp: imp.getStack() must return a virtualstack2
     * @param t
     * @param impRAM
     */

    public LoadFrameFromVS2IntoRAM(ImagePlus imp, int t, ImagePlus impRAM, int nThreads)
    {
        this.imp = imp;
        this.t = t;
        this.impRAM = impRAM;
        this.nThreads = nThreads;
    }

    public void run() {

        VirtualStack2 vs2 = (VirtualStack2 ) imp.getStack();

        for (int c = 0; c < imp.getNChannels(); c++) {

            // Load time-point and channel
            //logger.info("Loading time point " + t + ", channel " + c + "; memory: " + IJ.freeMemory());
            ImagePlus impChannelTime = vs2.getFullFrame(c, t, new Point3D(1, 1, 1), nThreads);

            // Copy time-point and channel at the right place into impRAM
            ImageStack stack = impChannelTime.getStack();
            ImageStack stackRAM = impRAM.getStack();

            int iStart = impRAM.getStackIndex(1,1,t+1);
            int iEnd = iStart + impRAM.getNSlices() - 1;

            for ( int i = iStart; i <= iEnd; i++ )
            {
                ImageProcessor ip = stack.getProcessor(i - iStart + 1);
                stackRAM.setPixels(ip.getPixels(), i);
            }

        }

    }

}
