package bigDataTools;

import ij.plugin.PlugIn;

public class DataStreamingToolsPlugIn implements PlugIn {

    @Override
    public void run(String s)
    {
        DataStreamingToolsGUI dataStreamingToolsGUI = new DataStreamingToolsGUI();
        dataStreamingToolsGUI.showDialog();
    }
}
