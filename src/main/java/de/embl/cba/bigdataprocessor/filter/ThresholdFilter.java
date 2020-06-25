package de.embl.cba.bigdataprocessor.filter;

import ij.IJ;
import ij.ImagePlus;

/**
 * Created by tischi on 19/09/17.
 */
public class ThresholdFilter implements ImageFilter {

    public static final String DEFAULT = "Default";

    String method = DEFAULT;

    public ThresholdFilter(String method)
    {
        this.method = method;
    }

    public ImagePlus filter ( ImagePlus imp )
    {
        ImagePlus filteredImage = imp.duplicate();

        IJ.run( filteredImage, "Convert to Mask", "method="+method+" background=Dark calculate");

        filteredImage.setTitle( imp.getTitle() + "_threshold");

        return ( filteredImage );
    }


}
