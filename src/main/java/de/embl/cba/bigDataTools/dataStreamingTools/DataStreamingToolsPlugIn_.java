package de.embl.cba.bigDataTools.dataStreamingTools;

import ij.plugin.PlugIn;

import javax.swing.*;

public class DataStreamingToolsPlugIn_ implements PlugIn {

    @Override
    public void run(String s)
    {
        DataStreamingToolsGUI dataStreamingToolsGUI = new DataStreamingToolsGUI();

        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                dataStreamingToolsGUI.showDialog();
            }
        });

    }
}
