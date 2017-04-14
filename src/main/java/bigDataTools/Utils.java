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

package bigDataTools;

import fiji.plugin.trackmate.Spot;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;

import java.util.Iterator;
import java.util.Map;

import static ij.IJ.log;

/**
 * Created by tischi on 06/11/16.
 */

public class Utils {

    public static boolean verbose = false;
    public static String version = "2016-Nov-21a";
    public static String TRACKMATEDOGSUBPIXEL = "TrackMate_DoG_SubPixel";
    public static String TRACKMATEDOG = "TrackMate_DoG";
    public static String IMAGESUITE3D = "3D ImageSuite";
    public static String LOAD_CHANNELS_FROM_FOLDERS = "sub-folders";
    static Logger logger = new IJLazySwingLogger();

    public static void show(ImagePlus imp)
    {
        imp.show();
        imp.setPosition(1, imp.getNSlices() / 2, 1);
        IJ.wait(200);
        imp.resetDisplayRange();
        imp.updateAndDraw();
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
            nums[i] = Double.parseDouble(sA[i]);
        }

        return nums;
    }

    public static int[] delimitedStringToIntegerArray(String s, String delimiter) {

        String[] sA = s.split(delimiter);
        int[] nums = new int[sA.length];
        for (int i = 0; i < nums.length; i++) {
            nums[i] = Integer.parseInt(sA[i]);
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



}
