import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingToolsGUI;
import ij.IJ;

public class OpenOneChannelTiffSeriesAndShowUI
{

    public final static String ROOT = "/Users/tischer/Documents/fiji-plugin-bigDataTools/";
    public final static String TEST_RESOURCES = ROOT + "/src/test/resources/";

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final DataStreamingTools dataStreamingTools = new DataStreamingTools();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory = TEST_RESOURCES + "tiff-nc1-nt1";
                dataStreamingTools.openFromDirectory(
                        directory,
                        "None",
                        ".*",
                        "ResolutionLevel 0/Data",
                        null,
                        10  ,
                        true,
                        false);
            }
        }); t1.start();

        IJ.wait(1000);

        DataStreamingToolsGUI dataStreamingToolsGUI = new DataStreamingToolsGUI();
        dataStreamingToolsGUI.showDialog();

    }


}
