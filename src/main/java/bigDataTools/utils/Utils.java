/*
 * #%L
 * Data streaming, tracking and cropping tools
 * %%
 * Copyright (C) 2017 Christian Tischer
 *
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package bigDataTools.utils;

import bigDataTools.Region5D;
import bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import bigDataTools.logging.IJLazySwingLogger;
import bigDataTools.logging.Logger;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.Binner;
import ij.plugin.Duplicator;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import javafx.geometry.Point3D;

import java.awt.*;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by tischi on 06/11/16.
 */

public class Utils {

    public static boolean verbose = false;
    public static String LOAD_CHANNELS_FROM_FOLDERS = "channels from sub-folders";

    static Logger logger = new IJLazySwingLogger();

    public enum FileType {
        TIFF("Tiff"),
        HDF5("Hdf5"),
        HDF5_IMARIS_BDV("Hdf5_Imaris_Bdv"),
        TIFF_STACKS("Tiff stacks"),
        SINGLE_PLANE_TIFF("single tif"),
        SERIALIZED_HEADERS("Serialized headers");
        private final String text;
        private FileType(String s) {
            text = s;
        }
        @Override
        public String toString() {
            return text;
        }
    }


    public enum ImageFilterTypes {
        NONE("None"),
        VARIANCE("Variance");
        private final String text;
        private ImageFilterTypes(String s) {
            text = s;
        }
        @Override
        public String toString() {
            return text;
        }
    }


    public static String[] getNames(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toArray(String[]::new);
    }


    public static void logArrayList(ArrayList<long[]> arrayList )
    {
        for ( long[] entry : arrayList )
        {
            logger.info( "" + entry[0] + "," + entry[1] + "," + entry[2]);
        }
    }

    public static boolean checkRange(ImagePlus imp, int min, int max, String dimension)
    {
        // setup
        //

        int Min = 0, Max = 0;

        if ( dimension.equals("z") )
        {
            Min = 1;
            Max = imp.getNSlices();
        }
        else if ( dimension.equals("t") )
        {
            Min = 1;
            Max = imp.getNFrames();
        }

        // check
        //

        if (min < Min)
        {
            logger.error(""+dimension+" minimum must be >= " + Min + "; please change the value.");
            return false;
        }

        if (max > Max)
        {
            logger.error(""+dimension+" maximum must be <= " + Max + "; please change the value.");
            return false;
        }


        return true;

    }

    public static Point3D computeOffsetFromCenterSize(Point3D pCenter, Point3D pSize) {
        return(pCenter.subtract(pSize.subtract(1, 1, 1).multiply(0.5)));
    }

    public static Point3D computeCenterFromOffsetSize(Point3D pOffset, Point3D pSize) {
        // center of width 7 is 0,1,2,*3*,4,5,6
        // center of width 6 is 0,1,2,*2.5*,3,4,5
        return(pOffset.add(pSize.subtract(1, 1, 1).multiply(0.5)));
    }

    public static Point3D multiplyPoint3dComponents(Point3D p0, Point3D p1) {

        double x = p0.getX() * p1.getX();
        double y = p0.getY() * p1.getY();
        double z = p0.getZ() * p1.getZ();

        return (new Point3D(x,y,z));

    }

    public static void show(ImagePlus imp)
    {
        imp.show();
        imp.setPosition(1, imp.getNSlices() / 2, 1);
        IJ.wait(200);
        imp.resetDisplayRange();
        imp.updateAndDraw();
    }

    public static ImagePlus getDataCubeFromImagePlus(ImagePlus imp, Region5D region5D)
    {

        Rectangle rect = new Rectangle(
                (int)region5D.offset.getX(),
                (int)region5D.offset.getY(),
                (int)region5D.size.getX(),
                (int)region5D.size.getY());

        ImageStack stack = imp.getStack();
        ImageStack stack2 = null;

        int firstT = region5D.t + 1;
        int lastT = region5D.t + 1;
        int firstC = region5D.c + 1;
        int lastC = region5D.c + 1;
        int firstZ = (int)region5D.offset.getZ() + 1;
        int lastZ = (int)region5D.offset.getZ() + (int)region5D.size.getZ();

        for (int t=firstT; t<=lastT; t++) {
            for (int z=firstZ; z<=lastZ; z++) {
                for (int c=firstC; c<=lastC; c++) {
                    int n1 = imp.getStackIndex(c, z, t);
                    ImageProcessor ip = stack.getProcessor(n1);
                    String label = stack.getSliceLabel(n1);
                    ip.setRoi(rect);
                    ip = ip.crop();
                    if (stack2==null)
                        stack2 = new ImageStack(ip.getWidth(), ip.getHeight(), null);
                    stack2.addSlice(label, ip);
                }
            }
        }
        ImagePlus imp2 = imp.createImagePlus();
        imp2.setStack("DUP_" + imp.getTitle(), stack2);
        imp2.setDimensions(lastC - firstC + 1, lastZ - firstZ + 1, lastT - firstT + 1);
        imp2.setOpenAsHyperStack(true);

        return imp2;
    }


    public static VirtualStackOfStacks getVirtualStackOfStacks(ImagePlus imp) {
        VirtualStackOfStacks vss = null;
        try {
            vss = (VirtualStackOfStacks) imp.getStack();
            return (vss);
        } catch (Exception e) {
             logger.error("This is only implemented for images opened with the Data Streaming Tools plugin.");
            return (null);
        }
    }

    public static double[] delimitedStringToDoubleArray(String s, String delimiter) {

        String[] sA = s.split(delimiter);
        double[] nums = new double[sA.length];
        for (int i = 0; i < nums.length; i++) {
            nums[i] = Double.parseDouble(sA[i].trim());
        }

        return nums;
    }

    public static int[] delimitedStringToIntegerArray(String s, String delimiter) {

        String[] sA = s.split(delimiter);
        int[] nums = new int[sA.length];
        for (int i = 0; i < nums.length; i++)
        {
            nums[i] = Integer.parseInt(sA[i].trim());
        }

        return nums;
    }

    public static void printMap(Map mp) {
        Iterator it = mp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
              logger.info("" + pair.getKey() + " = " + pair.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }
    }

    public static boolean hasVirtualStackOfStacks(ImagePlus imp) {

        if( ! (imp.getStack() instanceof VirtualStackOfStacks) ) {
             logger.error("Wrong image type. " +
                     "This method is only implemented for images opened via " +
                     "the Data Streaming Tools plugin.");
            return false;
        }
        else
        {
            return true;
        }

    }

    public static boolean checkMemoryRequirements(ImagePlus imp)
    {
        long numPixels = (long)imp.getWidth()*imp.getHeight()*imp.getNSlices()*imp.getNChannels()*imp.getNFrames();
        boolean ok = checkMemoryRequirements(numPixels, imp.getBitDepth(), 1);
        return(ok);
    }

    public static boolean checkMemoryRequirements(ImagePlus imp, int nThreads)
    {
        long numPixels = (long)imp.getWidth()*imp.getHeight()*imp.getNSlices();
        boolean ok = checkMemoryRequirements(numPixels, imp.getBitDepth(), nThreads);
        return(ok);
    }

    public static boolean checkMemoryRequirements(long numPixels, int bitDepth, int nThreads)
    {
        //
        // check that the data cube is not too large for the java indexing
        //
        long maxSize = (1L<<31) - 1;
        if( numPixels > maxSize )
        {

              logger.info("Warning: " + "The size of one requested data cube is " + numPixels + " (larger than 2^31)\n");
            //logger.error("The size of one requested data cube is "+numPixels +" (larger than 2^31)\n" +
            //        "and can thus not be loaded as one java array into RAM.\n" +
            //        "Please crop a smaller region.");
            //return(false);
        }

        //
        // check that the data cube(s) fits into the RAM
        //
        double GIGA = 1000000000.0;
        long freeMemory = IJ.maxMemory() - IJ.currentMemory();
        double maxMemoryGB = IJ.maxMemory()/GIGA;
        double freeMemoryGB = freeMemory/GIGA;
        double requestedMemoryGB = numPixels*bitDepth/8*nThreads/GIGA;

        if( requestedMemoryGB > freeMemoryGB )
        {
             logger.error("The size of the requested data cube(s) is " + requestedMemoryGB + " GB.\n" +
                     "The free memory is only " + freeMemoryGB + " GB.\n" +
                     "Please consider cropping a smaller region \n" +
                     "and/or reducing the number of I/O threads \n" +
                     "(you are currently using " + nThreads + ").");
            return(false);
        }
        else
        {
            if( requestedMemoryGB > 0.1 ) {
                logger.info("Memory [GB]: Max=" + maxMemoryGB + "; Free=" + freeMemoryGB + "; Requested=" +
                        requestedMemoryGB);
            }

        }



        return(true);

    }

    public static ImagePlus getDataCube(ImagePlus imp, Region5D region5D, int[] intensityGate, int nThreads)
    {
        ImagePlus dataCube = null;

        if(imp.getStack() instanceof VirtualStackOfStacks)
        {
            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
            dataCube = vss.getDataCube( region5D, intensityGate, nThreads );
        }
        else
        {
            dataCube = getDataCubeFromImagePlus( imp, region5D );
        }

        //dataCube.show();

        return( dataCube );
    };

    public static void applyIntensityGate( ImagePlus imp, int[] gate )
    {
        ImageStack stack = imp.getStack();
        int nx = imp.getWidth();
        int ny = imp.getHeight();
        long nPixels = nx*ny;
        int nz = imp.getNSlices();
        int min = gate[0];
        int max = gate[1];

        if ( stack.getBitDepth() == 8 )
        {
            if ( max == -1 )
            {
                max = 255;
            }

            for (int z = 1; z <= nz; z++)
            {
                ImageProcessor ip = stack.getProcessor(z);

                byte[] pixels = (byte[]) ip.getPixels();

                for ( int i = 0; i < nPixels; i++ )
                {
                    if ( (pixels[i] > max) ||  pixels[i] < min )
                    {
                        pixels[i] = 0;
                    }
                    else
                    {
                        pixels[i] -= min;
                    }
                }
            }
        }


        if ( stack.getBitDepth() == 16 )
        {

            if ( max == -1 )
            {
                max = 65535;
            }

            for (int z = 1; z <= nz; z++)
            {
                ImageProcessor ip = stack.getProcessor(z);

                short[] pixels = (short[]) ip.getPixels();

                for ( int i = 0; i < nPixels; i++ )
                {
                    if ( (pixels[i] > max) ||  pixels[i] < min )
                    {
                        pixels[i] = 0;
                    }
                    else
                    {
                        pixels[i] -= min;
                    }
                }
            }
        }
    }

    public static ImagePlus bin( ImagePlus imp_, int[] binning_, String binningTitle, String method )
    {
        ImagePlus imp = imp_;
        int[] binning = binning_;
        String title = new String(imp.getTitle());
        Binner binner = new Binner();

        Calibration saveCalibration = imp.getCalibration().copy(); // this is due to a bug in the binner

        ImagePlus impBinned = null;

        switch( method )
        {
            case "OPEN":
                impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.MIN);
                //impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.AVERAGE);
                //IJ.run(impBinned, "Minimum 3D...", "x=1 y=1 z=1");
                //IJ.run(impBinned, "Maximum 3D...", "x=1 y=1 z=1");
                impBinned.setTitle("Open_" + title);
                break;
            case "CLOSE":
                impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.MAX);
                //impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.AVERAGE);
                //IJ.run(impBinned, "Maximum 3D...", "x=1 y=1 z=1");
                //IJ.run(impBinned, "Minimum 3D...", "x=1 y=1 z=1");
                impBinned.setTitle("Close_" + title);
                break;
            case "AVERAGE":
                impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.AVERAGE);
                impBinned.setTitle(binningTitle + "_" + title);
                break;
            case "MIN":
                impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.MIN);
                impBinned.setTitle(binningTitle + "_Min_" + title);
                break;
            case "MAX":
                impBinned = binner.shrink(imp, binning[0], binning[1], binning[2], binner.MAX);
                impBinned.setTitle(binningTitle + "_Max_" + title);
                break;
            default:
                IJ.showMessage("Error while binning; method not supported :"+method);
                break;
        }

        // reset calibration of input image
        // necessary due to a bug in the binner
        imp.setCalibration( saveCalibration );

        return ( impBinned );
}

    private Point3D compute16bitCenterOfMass(ImageStack stack, Point3D pMin, Point3D pMax)
    {

        final String centeringMethod = "center of mass";

        //long startTime = System.currentTimeMillis();
        double sum = 0.0, xsum = 0.0, ysum = 0.0, zsum = 0.0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xmin = 0 > (int) pMin.getX() ? 0 : (int) pMin.getX();
        int xmax = (width-1) < (int) pMax.getX() ? (width-1) : (int) pMax.getX();
        int ymin = 0 > (int) pMin.getY() ? 0 : (int) pMin.getY();
        int ymax = (height-1) < (int) pMax.getY() ? (height-1) : (int) pMax.getY();
        int zmin = 0 > (int) pMin.getZ() ? 0 : (int) pMin.getZ();
        int zmax = (depth-1) < (int) pMax.getZ() ? (depth-1) : (int) pMax.getZ();

        // compute one-based, otherwise the numbers at x=0,y=0,z=0 are lost for the center of mass

        if (centeringMethod.equals("center of mass")) {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                short[] pixels = (short[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i] & 0xffff;
                        // v=0 is ignored automatically in below formulas
                        sum += v;
                        xsum += x * v;
                        ysum += y * v;
                        zsum += z * v;
                        i++;
                    }
                }
            }
        }

        if (centeringMethod.equals("centroid")) {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                short[] pixels = (short[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i] & 0xffff;
                        if (v > 0) {
                            sum += 1;
                            xsum += x;
                            ysum += y;
                            zsum += z;
                        }
                        i++;
                    }
                }
            }
        }

        // computation is one-based; result should be zero-based
        double xCenter = (xsum / sum) - 1;
        double yCenter = (ysum / sum) - 1;
        double zCenter = (zsum / sum) - 1;

        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime;  logger.info("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xCenter,yCenter,zCenter));
    }

    private Point3D compute8bitCenterOfMass(ImageStack stack, Point3D pMin, Point3D pMax)
    {

        final String centeringMethod = "center of mass";

        //long startTime = System.currentTimeMillis();
        double sum = 0.0, xsum = 0.0, ysum = 0.0, zsum = 0.0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xmin = 0 > (int) pMin.getX() ? 0 : (int) pMin.getX();
        int xmax = (width-1) < (int) pMax.getX() ? (width-1) : (int) pMax.getX();
        int ymin = 0 > (int) pMin.getY() ? 0 : (int) pMin.getY();
        int ymax = (height-1) < (int) pMax.getY() ? (height-1) : (int) pMax.getY();
        int zmin = 0 > (int) pMin.getZ() ? 0 : (int) pMin.getZ();
        int zmax = (depth-1) < (int) pMax.getZ() ? (depth-1) : (int) pMax.getZ();

        // compute one-based, otherwise the numbers at x=0,y=0,z=0 are lost for the center of mass

        if (centeringMethod.equals("center of mass")) {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                byte[] pixels = (byte[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i] & 0xff;
                        // v=0 is ignored automatically in below formulas
                        sum += v;
                        xsum += x * v;
                        ysum += y * v;
                        zsum += z * v;
                        i++;
                    }
                }
            }
        }

        if (centeringMethod.equals("centroid")) {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                byte[] pixels = (byte[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i] & 0xff;
                        if (v > 0) {
                            sum += 1;
                            xsum += x;
                            ysum += y;
                            zsum += z;
                        }
                        i++;
                    }
                }
            }
        }

        // computation is one-based; result should be zero-based
        double xCenter = (xsum / sum) - 1;
        double yCenter = (ysum / sum) - 1;
        double zCenter = (zsum / sum) - 1;

        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime;  logger.info("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xCenter,yCenter,zCenter));
    }


}
