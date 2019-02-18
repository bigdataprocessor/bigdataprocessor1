import de.embl.cba.bigdataconverter.BigDataConverter;
import de.embl.cba.bigdataconverter.BigDataConverterUI;
import ij.IJ;

public class OpenAshnaWithUI
{


    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final BigDataConverter bigDataConverter = new BigDataConverter();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory ="/Volumes/cba/tischer/projects/ashna-spim/reg2-3x3";
                bigDataConverter.openFromDirectory(
                        directory,
                        "None",
                        ".*--C.*",
                        "Resolution 0/Data",
                        null,
                        10  ,
                        true,
                        false);
            }
        }); t1.start();

        IJ.wait(1000);

        BigDataConverterUI bigDataConverterUI = new BigDataConverterUI();
        bigDataConverterUI.showDialog();

    }
}
