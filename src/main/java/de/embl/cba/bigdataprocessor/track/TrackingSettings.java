package de.embl.cba.bigdataprocessor.track;

import ij.ImagePlus;
import ij.gui.Roi;
import de.embl.cba.bigdataprocessor.utils.Point3D;

/**
 * Created by tischi on 14/04/17.
 */
public class TrackingSettings {

    public ImagePlus imp;
    public String trackingMethod;
    public Roi trackStartROI;
    public Point3D objectSize;
    public Point3D maxDisplacement;

    public Point3D subSamplingXYZ;
    public int subSamplingT;
    public int iterationsCenterOfMass;
    public int nt;
    public int channel;
    public double trackingFactor; // not used


    public boolean processImage;
    public int[] intensityGate;
    public int showProcessedRegions;
    public String imageFilterChoice;


}
