package de.embl.cba.bigdataconverter.track;

import de.embl.cba.bigdataconverter.imagefilter.ImageFilter;
import de.embl.cba.bigdataconverter.imagefilter.NoFilter;
import de.embl.cba.bigdataconverter.imagefilter.ThresholdFilter;
import de.embl.cba.bigdataconverter.imagefilter.VarianceFilter;
import de.embl.cba.bigdataconverter.logging.Logger;
import de.embl.cba.bigdataconverter.utils.Region5D;
import de.embl.cba.bigdataconverter.utils.Utils;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.imglib.algorithm.fft.PhaseCorrelationPeak;
import mpicbg.imglib.image.ImagePlusAdapter;

import java.util.ArrayList;

import static de.embl.cba.bigdataconverter.track.AdaptiveCropUI.CENTER_OF_MASS;


public class CenterOfMassTracker implements Runnable
{
    AdaptiveCrop adaptiveCrop;
    Logger logger;
    TrackingSettings trackingSettings;
    ImageFilter imageFilter;
    int numIOThreads;
    private int tStart;
    private int channel;
    private int nt;
    private int dt;
    private Track track;
    private long elapsedReadingTime;
    private long startTime;
    private long elapsedProcessingTime;
    private boolean finish;
    private int iProcessed;
    private long stopTime;

    public CenterOfMassTracker( AdaptiveCrop adaptiveCrop,
								TrackingSettings trackingSettings,
								Logger logger,
								int numIOThreads )
    {
        this.adaptiveCrop = adaptiveCrop;
        this.trackingSettings = trackingSettings;
        this.logger = logger;
        this.numIOThreads = numIOThreads;
        setImageFilter( trackingSettings, logger );
    }

    private void setImageFilter( TrackingSettings trackingSettings, Logger logger )
    {
        if ( trackingSettings.imageFeatureEnhancement
                .equals( Utils.ImageFilterTypes.NONE.toString() ) )
        {
            imageFilter = new NoFilter();
        }
        else if ( trackingSettings.imageFeatureEnhancement
                .equals(Utils.ImageFilterTypes.VARIANCE.toString()) )
        {
            imageFilter = new VarianceFilter( 2.0F );
            logger.info("Image will be variance filtered.");
        }
        else if ( trackingSettings.imageFeatureEnhancement.
                equals(Utils.ImageFilterTypes.THRESHOLD.toString()) )
        {
            // TODO: probably some auto-local threshold works better?
            imageFilter = new ThresholdFilter( ThresholdFilter.DEFAULT );
            logger.info( "Images will be tresholded using method : " + ThresholdFilter.DEFAULT );
        }
    }

    public void run() {

        Point3D pOffset;
        Point3D pCenter;
        Point3D pShift;
        Point3D regionSize;

        track = adaptiveCrop.addNewTrack(trackingSettings);
        tStart = trackingSettings.trackStartROI.getImage().getT() - 1;
        channel = trackingSettings.channel;
        nt = trackingSettings.nt;
        dt = trackingSettings.subSamplingT;

        Point3D pStart = new Point3D(
                trackingSettings.trackStartROI.getXBase(),
                trackingSettings.trackStartROI.getYBase(),
                trackingSettings.trackStartROI.getImage().getZ() - 1);

        TrackTable trackTable = adaptiveCrop.getTrackTable();
        ImagePlus imp = trackingSettings.imp;

        regionSize = getRegionSize( track, imp );

        // we need to load the bigger region, because this is what will also be
        // loaded for the subsequent time-points; and - without aggressive
        // background subtraction - the center-of-mass
        // algorithm is sensitive to the size of the region.
        regionSize = regionSize.add( trackingSettings.maxDisplacement.multiply( 2.0 ) );

        pOffset = Utils.computeOffsetFromCenterSize( pStart, regionSize );
        pCenter = pStart;
        imp = readDataCube( pOffset, regionSize, imp );
        imp = filterDataCube( imp );

        pShift = compute16bitShiftUsingIterativeCenterOfMass( imp.getStack(),
                trackingSettings.trackingFactor,
                trackingSettings.iterationsCenterOfMass);

        publishResult( imp, track, trackTable, logger,
                pCenter.add( pShift ), tStart, nt, dt, elapsedReadingTime, elapsedProcessingTime );

        finish = false;
        int tMax = tStart + nt - 1;
        int tPrevious = tStart;
        iProcessed = 0;

        if( iProcessed++ < trackingSettings.showProcessedRegions )
        {
            imp.setTitle( "t" + tStart + "-processed" );
            imp.show();
        }

        for ( int tCurrent = tStart + dt; tCurrent < tStart + nt + dt; tCurrent = tCurrent + dt ) {

            tCurrent = adaptCurrentTimePoint( tMax, tCurrent );

            startTime = System.currentTimeMillis();

            pCenter = pCenter.add( pShift );
            pOffset = Utils.computeOffsetFromCenterSize( pCenter, regionSize );

            imp = loadAndProcessDataCube( pOffset, regionSize, imp, tCurrent );

            pShift = compute16bitShiftUsingIterativeCenterOfMass( imp.getStack(),
                    trackingSettings.trackingFactor, trackingSettings.iterationsCenterOfMass );

            pShift = correctForSubSampling( pShift );

            pShift = correctFor2D( pShift, imp, 0 );

            if( logger.isShowDebug() )
                logger.info("actual final shift " + pShift.toString());

            computeIntermediateTimePoints( pShift, trackTable, imp, tPrevious, tCurrent );

            tPrevious = tCurrent;

            if( finish ) return;

            if( adaptiveCrop.interruptTrackingThreads )
            {
                logger.info("Tracking of track " + track.getID() + " interrupted.");
                return;
            }

        }


    }

    private void computeIntermediateTimePoints( Point3D pShift, TrackTable trackTable,
                                                ImagePlus imp, int tPrevious, int tCurrent )
    {
        for ( int tUpdate = tPrevious + 1; tUpdate <= tCurrent; ++tUpdate )
		{
			Point3D pPrevious = track.getPosition( tPrevious );
			double interpolation = (double) (tUpdate - tPrevious) / (double) (tCurrent - tPrevious);
			Point3D pUpdate = pPrevious.add( pShift.multiply( interpolation ) );
			publishResult( imp, track, trackTable, logger,
                    pUpdate, tUpdate, nt, dt, elapsedReadingTime, elapsedProcessingTime );
		}
    }

    private Point3D correctFor2D( Point3D pShift, ImagePlus imp, int z )
    {
        if ( imp.getNSlices() == 1 )
        {
            pShift = new Point3D( pShift.getX(), pShift.getY(), z );
        }
        return pShift;
    }

    private Point3D correctForSubSampling( Point3D pShift )
    {
        pShift = Utils.multiplyPoint3dComponents( pShift, trackingSettings.subSamplingXYZ );
        return pShift;
    }

    private Point3D computeShift( ImagePlus imp0, ImagePlus imp1 )
    {
        Point3D pShift;
        logger.debug("Measuring drift using correlation...");
        startTime = System.currentTimeMillis();
        pShift = computeShiftUsingPhaseCorrelation(imp1, imp0);
        stopTime = System.currentTimeMillis();
        elapsedProcessingTime = stopTime - startTime;
        return pShift;
    }

    private ImagePlus loadAndProcessDataCube( Point3D offset, Point3D size, ImagePlus imp, int t )
    {
        final Region5D region5D = new Region5D();
        region5D.t = t;
        region5D.c = channel;
        region5D.size = size;
        region5D.offset = offset;
        region5D.subSampling = trackingSettings.subSamplingXYZ;

        ImagePlus dataCube = Utils.getDataCube( imp, region5D, numIOThreads );
        Utils.applyIntensityGate( dataCube, trackingSettings.intensityGate );
        elapsedReadingTime = System.currentTimeMillis() - startTime;
        dataCube = imageFilter.filter( dataCube );

        if( iProcessed++ < trackingSettings.showProcessedRegions )
		{
			dataCube.setTitle( "t" + t + "-processed" );
			dataCube.show();
		}

		return dataCube;
    }

    private int adaptCurrentTimePoint( int tMax, int tNow )
    {
        if( tNow >= tMax ) {
			// due to the sub-sampling in t the addition of dt
			// can cause the frame to be outside of
			// the tracking range => load the last frame
			tNow = tMax;
			finish = true;
		}
        return tNow;
    }

    private ImagePlus filterDataCube( ImagePlus imp0 )
    {
        Utils.applyIntensityGate( imp0, trackingSettings.intensityGate );
        imp0 = imageFilter.filter( imp0 );
        return imp0;
    }

    private ImagePlus readDataCube( Point3D p0offset, Point3D regionSize, ImagePlus imp )
    {
        ImagePlus imp0;
        startTime = System.currentTimeMillis();
        Region5D region5D = new Region5D();
        region5D.t = tStart;
        region5D.c = channel;
        region5D.offset = p0offset;
        region5D.size = regionSize;
        region5D.subSampling = trackingSettings.subSamplingXYZ;
        imp0 = Utils.getDataCube( imp, region5D, numIOThreads );
        elapsedReadingTime = System.currentTimeMillis() - startTime;
        return imp0;
    }

    private Point3D getRegionSize( Track track, ImagePlus imp )
    {
        Point3D regionSize = track.getObjectSize();
        regionSize = correctFor2D( regionSize, imp, 1 );
        return regionSize;
    }

    private void publishResult(ImagePlus imp, Track track, TrackTable trackTable, Logger logger, Point3D location,
                               int t, int nt, int dt,
                               long elapsedReadingTime, long elapsedProcessingTime)
    {

        track.addLocation(t, location);

        // store one-based values in table
        trackTable.addRow(new Object[]{
                String.format("%1$04d", track.getID()) + "_" + String.format("%1$05d", t+1),
                String.format("%.2f", (float) location.getX() ),
                String.format("%.2f", (float) location.getY() ),
                String.format("%.2f", (float) location.getZ() + 1.0 ),
                String.format("%1$04d", t + 1),
                String.format("%1$04d", track.getID() )
        });

        adaptiveCrop.addLocationToOverlay(track, t);

        logger.progress( "Track: " + track.getID(),
                "; Image: " + imp.getTitle() +
                        "; Frame: " + ( t - track.getTmin() + 1 ) + "/" + nt +
                        "; Reading [ms] = " + elapsedReadingTime +
                        "; Processing [ms] = " + elapsedProcessingTime );

    }



    private Point3D compute16bitShiftUsingIterativeCenterOfMass(
            ImageStack stack,
            double trackingFactor,
            int iterations) {

        Point3D pMin, pMax;

        // compute stack center and tracking radii
        // at each iteration, the center of mass is only computed for a subset of the data cube
        // this subset iteratively shifts every iteration according to the results of the center of mass computation
        Point3D pStackSize = new Point3D(stack.getWidth(), stack.getHeight(), stack.getSize() );
        Point3D pStackCenter = Utils.computeCenterFromOffsetSize(new Point3D(0,0,0), pStackSize);
        Point3D pCenter = pStackCenter;
        double trackingFraction;
        for(int i=0; i<iterations; i++) {
            // trackingFraction = 1/trackingFactor is the user selected object size, because we are loading
            // a portion of the data, which is trackingFactor times larger than the object size
            // below formula makes the region in which the center of mass is compute go from 1 to 1/trackingfactor
            trackingFraction = 1.0 - Math.pow(1.0*(i+1)/iterations,1.0/4.0)*(1.0-1.0/trackingFactor);
            pMin = pCenter.subtract(pStackSize.multiply(trackingFraction / 2)); // div 2 because it is radius
            pMax = pCenter.add(pStackSize.multiply(trackingFraction / 2));
            pCenter = computeCenterOfMass(stack, pMin, pMax);
            //info("i "+i+" trackingFraction "+trackingFraction+" pCenter "+pCenter.toString());
        }
        return(pCenter.subtract(pStackCenter));
    }


    private Point3D computeShiftUsingPhaseCorrelation(ImagePlus imp1, ImagePlus imp0)
    {
        if( logger.isShowDebug() )   logger.info("PhaseCorrelation phc = new PhaseCorrelation(...)");
        PhaseCorrelation phc = new PhaseCorrelation( ImagePlusAdapter.wrap( imp1 ),
                ImagePlusAdapter.wrap( imp0 ), 5, true);

        if( logger.isShowDebug() )   logger.info("phc.process()... ");
        phc.process();
        // get the first peak that is not a clean 1.0,
        // because 1.0 cross-correlation typically is an artifact of too much shift into black areas of both images
        ArrayList<PhaseCorrelationPeak> pcp = phc.getAllShifts();
        float ccPeak = 0;
        int iPeak = 0;
        for(iPeak = pcp.size() - 1; iPeak >= 0; iPeak--) {
            ccPeak = pcp.get(iPeak).getCrossCorrelationPeak();
            if (ccPeak < 0.999) break;
        }
        //info(""+ccPeak);
        int[] shift = pcp.get(iPeak).getPosition();
        if ( imp1.getNSlices() == 1 )
            return(new Point3D(shift[0],shift[1],0));
        else
            return(new Point3D(shift[0],shift[1],shift[2]));
    }

    private Point3D computeCenterOfMass(ImageStack stack, Point3D pMin, Point3D pMax)
    {
        Point3D centerOfMass = null;

        if ( stack.getBitDepth() == 8 )
        {
             centerOfMass = compute8bitCenterOfMass(stack, pMin, pMax);
        }
        else if ( stack.getBitDepth() == 16 )
        {
             centerOfMass = compute16bitCenterOfMass(stack, pMin, pMax);
        }
        else if ( stack.getBitDepth() == 32 )
        {
            centerOfMass = compute32bitCenterOfMass(stack, pMin, pMax);
        }


        return(centerOfMass);
    }

    // TODO: make this type independent
    private Point3D compute32bitCenterOfMass(ImageStack stack, Point3D pMin, Point3D pMax)
    {

        final String centeringMethod = CENTER_OF_MASS;

        //long startTime = System.currentTimeMillis();
        double sum = 0.0, xsum = 0.0, ysum = 0.0, zsum = 0.0;
        int i;
        float v;
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

        if (centeringMethod.equals(CENTER_OF_MASS)) {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                float[] pixels = (float[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i];
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

    private Point3D compute16bitCenterOfMass(ImageStack stack, Point3D pMin, Point3D pMax)
    {

        final String centeringMethod = CENTER_OF_MASS;

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

        if (centeringMethod.equals(CENTER_OF_MASS)) {
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

        final String centeringMethod = CENTER_OF_MASS;

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

        if (centeringMethod.equals(CENTER_OF_MASS)) {
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

    private int compute16bitMean(ImageStack stack)
    {

        //long startTime = System.currentTimeMillis();
        double sum = 0.0;
        int i;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xMin = 0;
        int xMax = (width-1);
        int yMin = 0;
        int yMax = (height-1);
        int zMin = 0;
        int zMax = (depth-1);

        for(int z=zMin; z<=zMax; z++) {
            short[] pixels = (short[]) stack.getProcessor(z+1).getPixels();
            for (int y = yMin; y<=yMax; y++) {
                i = y * width + xMin;
                for (int x = xMin; x <= xMax; x++) {
                    sum += (pixels[i] & 0xffff);
                    i++;
                }
            }
        }

        return((int) sum/(width*height*depth));

    }

    private Point3D compute16bitMaximumLocation(ImageStack stack)
    {
        long startTime = System.currentTimeMillis();
        int vmax = 0, xmax = 0, ymax = 0, zmax = 0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();

        for(int z=1; z <= depth; z++) {
            ImageProcessor ip = stack.getProcessor(z);
            short[] pixels = (short[]) ip.getPixels();
            i = 0;
            for (int y = 1; y <= height; y++) {
                i = (y-1) * width;
                for (int x = 1; x <= width; x++) {
                    v = pixels[i] & 0xffff;
                    if (v > vmax) {
                        xmax = x;
                        ymax = y;
                        zmax = z;
                        vmax = v;
                    }
                    i++;
                }
            }
        }

        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime;
        logger.info("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xmax,ymax,zmax));
    }

}

