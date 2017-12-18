package de.embl.cba.bigDataTools.bigDataTracker;

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

