import de.embl.cba.bigdataprocessor.BigDataProcessorUserInterface;

public class OpenUI
{

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        BigDataProcessorUserInterface bigDataProcessorUserInterface = new BigDataProcessorUserInterface();
        bigDataProcessorUserInterface.showDialog();
    }
}
