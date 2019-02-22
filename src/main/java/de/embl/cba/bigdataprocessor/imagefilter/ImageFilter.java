package de.embl.cba.bigdataprocessor.imagefilter;

import ij.ImagePlus;

/**
 * Created by tischi on 21/04/17.
 */
public interface ImageFilter {

    ImagePlus filter( ImagePlus imp );

}
