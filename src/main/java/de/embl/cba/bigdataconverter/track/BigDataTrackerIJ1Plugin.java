package de.embl.cba.bigdataconverter.track;

import ij.plugin.PlugIn;

import javax.swing.*;

public class BigDataTrackerIJ1Plugin implements PlugIn {

    @Override
    public void run(String s)
    {
        AdaptiveCropUI adaptiveCropUI = new AdaptiveCropUI();

        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                adaptiveCropUI.getPanel();
            }
        });

    }
}

