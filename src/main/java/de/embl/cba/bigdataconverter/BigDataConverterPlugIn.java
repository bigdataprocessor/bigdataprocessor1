package de.embl.cba.bigdataconverter;

import ij.plugin.PlugIn;

import javax.swing.*;

public class BigDataConverterPlugIn implements PlugIn {

    @Override
    public void run(String s)
    {
        BigDataConverterUI bigDataConverterUI = new BigDataConverterUI();

        SwingUtilities.invokeLater( () -> bigDataConverterUI.showDialog() );
    }
}
