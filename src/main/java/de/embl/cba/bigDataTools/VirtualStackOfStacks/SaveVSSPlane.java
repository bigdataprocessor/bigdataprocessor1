package de.embl.cba.bigDataTools.VirtualStackOfStacks;

import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.saving.SavingSettings;
import de.embl.cba.bigDataTools.logging.IJLazySwingLogger;
import de.embl.cba.bigDataTools.logging.Logger;
import de.embl.cba.bigDataTools.utils.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.plugin.Binner;
import ij.process.ImageProcessor;

/**
 * Created by tischi on 17/07/17.
 */
public class SaveVSSPlane implements Runnable {
    int c,t,z;
    DataStreamingTools dataStreamingTools;
    SavingSettings savingSettings;

    Logger logger = new IJLazySwingLogger();

    public SaveVSSPlane( DataStreamingTools dataStreamingTools,
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
        VirtualStackOfStacks vss = ( VirtualStackOfStacks ) imp.getStack();

        int n = z + 1;
        n += t * ( imp.getNSlices() * imp.getNChannels() );
        n += c * imp.getNSlices();

        ImageProcessor ip = vss.getProcessor( n );
        ImagePlus impCTZ = new ImagePlus( "", ip );

        // Convert
        //
        if ( savingSettings.convertTo8Bit )
        {
            IJ.setMinAndMax( impCTZ, savingSettings.mapTo0, savingSettings.mapTo255 );
            IJ.run( impCTZ, "8-bit", "" );
        }

        if ( savingSettings.convertTo16Bit )
        {
            IJ.run( impCTZ, "16-bit", "" );
        }


        // Bin and save
        //
        String[] binnings = savingSettings.bin.split( ";" );

        for ( String binning : binnings )
        {

            if ( dataStreamingTools.interruptSavingThreads )
            {
                logger.progress( "Stopped saving thread: ", "" + t );
                return;
            }

            String newPath = savingSettings.filePath;

            // Binning
            ImagePlus impBinned = impCTZ;

            int[] binningA = Utils.delimitedStringToIntegerArray( binning, "," );

            if ( binningA[ 0 ] > 1 || binningA[ 1 ] > 1 || binningA[ 2 ] > 1 )
            {
                Binner binner = new Binner();
                impBinned = binner.shrink( impCTZ, binningA[ 0 ], binningA[ 1 ], binningA[ 2 ], binner.AVERAGE );
                newPath = savingSettings.filePath + "--bin-" + binningA[ 0 ] + "-" + binningA[ 1 ] + "-" + binningA[ 2 ];
            }

            FileSaver fileSaver = new FileSaver( impBinned );

            String sC = String.format( "%1$02d", c );
            String sT = String.format( "%1$05d", t );
            String sZ = String.format( "%1$05d", z );
            String pathCTZ = null;

            if ( imp.getNChannels() > 1 || imp.getNFrames() > 1 )
            {
                pathCTZ = newPath + "--C" + sC + "--T" + sT + "--Z" + sZ + ".tif";
            }
            else
            {
                pathCTZ = newPath + "--Z" + sZ + ".tif";
            }

            fileSaver.saveAsTiff( pathCTZ );
        }
    }
}
