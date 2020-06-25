import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.BigDataProcessorUserInterface;
import ij.IJ;

public class OpenEmTiffSlices
{
    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final BigDataProcessor bigDataProcessor = new BigDataProcessor();
        Thread t1 = new Thread( () -> {
            final String directory = "/Volumes/cba/exchange/paolo/compressed tif from fibsem";
            bigDataProcessor.openFromDirectory(
                    directory,
                    BigDataProcessorUserInterface.EM_TIFF_SLICES,
                    ".*.tif",
                    "",
                    null,
                    10,
                    true,
                    false );
        } );
        t1.start();

        IJ.wait( 1000 );

        BigDataProcessorUserInterface bigDataProcessorUserInterface = new BigDataProcessorUserInterface();
        bigDataProcessorUserInterface.showDialog();
    }
}
