import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.BigDataProcessorUserInterface;
import ij.IJ;

public class OpenSabineNilsH5WithUI
{


    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final BigDataProcessor bigDataProcessor = new BigDataProcessor();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory ="/Volumes/almfspim/Sabine/stream-fish2";
                bigDataProcessor.openFromDirectory(
                        directory,
                        "None",
                        ".*--C.*",
                        "Data",
                        null,
                        10  ,
                        true,
                        false);
            }
        }); t1.start();

        IJ.wait(1000);

        BigDataProcessorUserInterface bigDataProcessorUserInterface = new BigDataProcessorUserInterface();
        bigDataProcessorUserInterface.showDialog();

    }
}
