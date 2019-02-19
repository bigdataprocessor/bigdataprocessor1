package de.embl.cba.bigdataconverter.imagefilter;

import ij.ImagePlus;

/**
 * Created by tischi on 21/04/17.
 */
public class NoFilter implements ImageFilter {

    public ImagePlus filter(ImagePlus imp)
    {
        // do nothing
        return imp;
    }

}
