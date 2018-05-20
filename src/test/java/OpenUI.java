import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingToolsGUI;
import ij.IJ;

public class OpenUI
{

    public static void main(String[] args)
    {
        final net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        DataStreamingToolsGUI dataStreamingToolsGUI = new DataStreamingToolsGUI();
        dataStreamingToolsGUI.showDialog();
    }
}
