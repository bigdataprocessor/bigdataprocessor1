package bigDataTools.bigDataTracker;

import bigDataTools.bigDataTracker.BigDataTrackerGUI;
import ij.IJ;
import ij.plugin.PlugIn;

import javax.swing.*;

public class BigDataTrackerPlugIn_ implements PlugIn {

    @Override
    public void run(String s)
    {
        BigDataTrackerGUI bigDataTrackerGUI = new BigDataTrackerGUI();

        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                bigDataTrackerGUI.showDialog();
            }
        });

    }
}

