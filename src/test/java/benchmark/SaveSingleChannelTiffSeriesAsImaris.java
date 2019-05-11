package benchmark;

import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.BigDataProcessorUI;
import de.embl.cba.bigdataprocessor.saving.SavingSettings;
import de.embl.cba.bigdataprocessor.utils.Utils;
import ij.ImagePlus;

import java.io.File;

public class SaveSingleChannelTiffSeriesAsImaris
{

    public static void main(String[] args)
    {
        final BigDataProcessor bdp = new BigDataProcessor();

        final String directory = "/Users/tischer/Documents/fiji-plugin-bigDataTools/src/test/resources/tiff-nc1-nt2";

        final int numIOThreads = 4;

        final ImagePlus imagePlus = bdp.openFromDirectory(
                directory,
                "None",
                ".*",
                "",
                null,
                numIOThreads,
                true,
                false );

        final File out = new File( "/Users/tischer/Desktop/tiff-nc1-nt2-asImaris/im");

        SavingSettings savingSettings = new SavingSettings();
        savingSettings.imp = imagePlus;
        savingSettings.bin = "0,0,0";
        savingSettings.saveVolume = true;
        savingSettings.saveProjection = false;
        savingSettings.convertTo8Bit = false;
        savingSettings.convertTo16Bit = false;
        savingSettings.gate = false;
        savingSettings.directory = out.getParent();
        savingSettings.fileBaseName = out.getName();
        savingSettings.filePath = out.getAbsolutePath();
        savingSettings.fileType = Utils.FileType.IMARIS;
        savingSettings.nThreads = numIOThreads;

        bdp.saveAsStacks( savingSettings );


    }

}
