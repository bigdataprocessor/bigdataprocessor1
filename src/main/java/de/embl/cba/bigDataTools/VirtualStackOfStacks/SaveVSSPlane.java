package de.embl.cba.bigDataTools.VirtualStackOfStacks;

import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.dataStreamingTools.SavingSettings;
import de.embl.cba.bigDataTools.logging.IJLazySwingLogger;
import de.embl.cba.bigDataTools.logging.Logger;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageProcessor;

/**
 * Created by tischi on 17/07/17.
 */
public class SaveVSSPlane implements Runnable {
    int c,t,z;
    DataStreamingTools dataStreamingTools;
    SavingSettings savingSettings;

    Logger logger = new IJLazySwingLogger();

    public SaveVSSPlane(DataStreamingTools dataStreamingTools,
                        int c,
                        int t,
                        int z,
                        SavingSettings savingSettings)
    {
        this.dataStreamingTools = dataStreamingTools;
        this.c = c;
        this.z = z;
        this.t = t;
        this.savingSettings = savingSettings;
    }

    public void run()
    {

        if ( dataStreamingTools.interruptSavingThreads )
        {
            return;
        }

        ImagePlus imp = savingSettings.imp;
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

        int n = z + 1;
        n += t * (imp.getNSlices() * imp.getNChannels() );
        n += c * imp.getNSlices();

        ImageProcessor ip = vss.getProcessor( n );
        ImagePlus impCTZ = new ImagePlus("",ip);

        FileSaver fileSaver = new FileSaver( impCTZ );

        String sC = String.format("%1$02d", c);
        String sT = String.format("%1$05d", t);
        String sZ = String.format("%1$05d", z);
        String pathCTZ = null;

        if ( imp.getNChannels() > 1 || imp.getNFrames() > 1)
        {
            pathCTZ = savingSettings.filePath + "--C" + sC + "--T" + sT + "--Z" + sZ +".tif";
        }
        else
        {
            pathCTZ = savingSettings.filePath + "--Z" + sZ +".tif";
        }
        //logger.info("Saving " + pathCT);
        fileSaver.saveAsTiff(pathCTZ);
    }

}
