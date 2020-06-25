package de.embl.cba.bigdataprocessor.filter;

import ij.ImagePlus;

/**
 * Created by tischi on 21/04/17.
 */
public interface ImageFilter {

    ImagePlus filter( ImagePlus imp );

}
