import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.BigDataProcessorUI;
import ij.IJ;

public class OpenTwoChannelTiffSeriesWithUI
{

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final BigDataProcessor bigDataProcessor = new BigDataProcessor();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory = "/Users/tischer/Documents/fiji-plugin-bigdataprocessor/src/test/resources/tiff-nc2-nt2/";
                bigDataProcessor.openFromDirectory(
                        directory,
                        BigDataProcessorUI.LOAD_CHANNELS_FROM_FOLDERS,
                        ".*",
                        "",
                        null,
                        10  ,
                        true,
                        false);
            }
        }); t1.start();

        IJ.wait(1000);

        BigDataProcessorUI bigDataProcessorUI = new BigDataProcessorUI();
        bigDataProcessorUI.showDialog();

    }

}
