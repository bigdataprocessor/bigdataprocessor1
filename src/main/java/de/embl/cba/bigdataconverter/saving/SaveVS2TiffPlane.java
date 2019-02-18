package de.embl.cba.bigdataconverter.saving;

import de.embl.cba.bigdataconverter.BigDataConverter;
import de.embl.cba.bigdataconverter.logging.IJLazySwingLogger;
import de.embl.cba.bigdataconverter.logging.Logger;
import de.embl.cba.bigdataconverter.utils.Utils;
import de.embl.cba.bigdataconverter.virtualstack2.VirtualStack2;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.plugin.Binner;
import ij.process.ImageProcessor;

/**
 * Created by tischi on 17/07/17.
 */
public class SaveVS2TiffPlane implements Runnable {
    int c,t,z;
    BigDataConverter bigDataConverter;
    SavingSettings savingSettings;

    Logger logger = new IJLazySwingLogger();

    public SaveVS2TiffPlane( BigDataConverter bigDataConverter,
                             int c,
                             int t,
                             int z,
                             SavingSettings savingSettings)
    {
        this.bigDataConverter = bigDataConverter;
        this.c = c;
        this.z = z;
        this.t = t;
        this.savingSettings = savingSettings;
    }

    public void run()
    {

        if ( bigDataConverter.interruptSavingThreads )
        {
            return;
        }

        ImagePlus imp = savingSettings.imp;
        VirtualStack2 vs2 = ( VirtualStack2 ) imp.getStack();

        int n = z + 1;
        n += t * ( imp.getNSlices() * imp.getNChannels() );
        n += c * imp.getNSlices();

        ImageProcessor ip = vs2.getProcessor( n );
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

            if ( bigDataConverter.interruptSavingThreads )
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
