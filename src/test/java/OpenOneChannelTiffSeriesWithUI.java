import de.embl.cba.bigDataTools.bigDataTracker.BigDataTrackerGUI;
import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingToolsGUI;
import ij.IJ;

public class OpenOneChannelTiffSeriesWithUI
{

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        final DataStreamingTools dataStreamingTools = new DataStreamingTools();
        Thread t1 = new Thread(new Runnable() {
            public void run()
            {
                final String directory = "/Users/tischer/Documents/fiji-plugin-bigDataTools/src/test/resources/tiff-nc1-nt2-16bit";
                dataStreamingTools.openFromDirectory(
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

        DataStreamingToolsGUI dataStreamingToolsGUI = new DataStreamingToolsGUI();
        dataStreamingToolsGUI.showDialog();

        BigDataTrackerGUI bigDataTrackerGUI = new BigDataTrackerGUI();
        bigDataTrackerGUI.showDialog();

    }


}
