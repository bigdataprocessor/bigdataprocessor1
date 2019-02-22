import de.embl.cba.bigdataprocessor.track.AdaptiveCropUI;
import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.BigDataProcessorUI;
import ij.IJ;

public class OpenOneChannelTiffSeriesWithUI
{

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final BigDataProcessor bigDataProcessor = new BigDataProcessor();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory = "/Users/tischer/Documents/fiji-plugin-bigdataprocessor/src/test/resources/tiff-nc1-nt2-16bit";
                bigDataProcessor.openFromDirectory(
                        directory,
                        "None",
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

        AdaptiveCropUI adaptiveCropUI = new AdaptiveCropUI();
        adaptiveCropUI.getPanel();

    }


}
