import de.embl.cba.bigdataconverter.BigDataConverterUI;

public class OpenUI
{

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        BigDataConverterUI bigDataConverterUI = new BigDataConverterUI();
        bigDataConverterUI.showDialog();
    }
}
