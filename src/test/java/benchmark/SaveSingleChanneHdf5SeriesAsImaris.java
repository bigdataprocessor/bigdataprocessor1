package benchmark;

import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.saving.SavingSettings;
import de.embl.cba.bigdataprocessor.utils.Utils;
import ij.ImagePlus;

import java.io.File;

public class SaveSingleChanneHdf5SeriesAsImaris
{

    public static void main(String[] args)
    {
        final BigDataProcessor bdp = new BigDataProcessor();

        final String directory = "/Users/tischer/Documents/isabell-schneider-splitchipmerge/stack_0_channel_0";

        final int numIOThreads = 4;

        final ImagePlus imagePlus = bdp.openFromDirectory(
                directory,
                "None",
                ".*.h5",
                "Data",
                null,
                numIOThreads,
                true,
                false );

        final File out = new File( "/Users/tischer/Desktop/stack_0_channel_0-asImaris/im");

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
