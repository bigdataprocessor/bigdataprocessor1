import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.ui.BigDataProcessorUI;
import ij.IJ;

public class OpenLeicaDLS
{
    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final BigDataProcessor bigDataProcessor = new BigDataProcessor();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory = "/Users/tischer/Documents/fiji-plugin-bigdataprocessor/src/test/resources/leicaDLS";
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

    }
}
