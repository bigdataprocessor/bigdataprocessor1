package de.embl.cba.bigDataTools.imageFilter;


import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Filters3D;

/**
 * Created by tischi on 18/09/17.
 */
public class VarianceFilter implements ImageFilter {

    private float radius = 2.0F;

    public VarianceFilter ( float radius )
    {
        this.radius = radius;
    }

    public ImagePlus filter ( ImagePlus imp )
    {
        ImageStack stack = Filters3D.filter( imp.getImageStack(), Filters3D.VAR, radius, radius, radius );

        ImagePlus filteredImage = new ImagePlus( imp.getTitle() + "_variance" , stack );

        return ( filteredImage );
    }

}
