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


package de.embl.cba.bigdataconverter.track;

import de.embl.cba.bigdataconverter.utils.Region5D;
import de.embl.cba.bigdataconverter.virtualstack2.VirtualStack2;
import de.embl.cba.bigdataconverter.BigDataConverter;
import de.embl.cba.bigdataconverter.logging.IJLazySwingLogger;
import de.embl.cba.bigdataconverter.logging.Logger;
import de.embl.cba.bigdataconverter.utils.Utils;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.embl.cba.bigdataconverter.track.ObjectTracker.CORRELATION;


public class AdaptiveCrop
{

    Logger logger = new IJLazySwingLogger();
    private ArrayList<Track> tracks = new ArrayList<Track>();
    private TrackTable trackTable;
    private ExecutorService es = Executors.newCachedThreadPool();

    public boolean interruptTrackingThreads = false;

    public AdaptiveCrop()
    {
        this.trackTable = new TrackTable();
    }

    public TrackTable getTrackTable()
    {
        return trackTable;
    }

    public ArrayList<Track> getTracks()
    {
        return tracks;
    }

    public void clearAllTracks()
    {

        // clear overlays from all images
        List<Track> tracks = getTracks();
        for ( Track track : tracks )
        {
            ImagePlus imp = track.getImp();
            imp.setOverlay(new Overlay());
        }

        // remove tracks
        tracks.clear();
        // empty the table
        getTrackTable().clear();


    }

    public ArrayList<ImagePlus> getViewsOnTrackedObjects( String croppingFactor ) {

        ArrayList<ImagePlus> imps = new ArrayList<>();

        for( Track track : tracks ) {

            Point3D pCropSize;

            //
            // convert track center coordinates to bounding box offsets
            //
            ArrayList<Point3D> trackOffsets = new ArrayList<>();
            Map<Integer, Point3D> locations = track.getLocations();

            if( croppingFactor.equals("all") ) {

                // the object was used to "drift correct" the whole image
                // thus, show the whole image
                Point3D pImageSize = new Point3D(track.getImp().getWidth(), track.getImp().getHeight(), track.getImp().getNSlices());
                Point3D pImageCenter = pImageSize.multiply(0.5);
                Point3D offsetToImageCenter = locations.get(track.getTmin()).subtract(pImageCenter);
                for( Point3D position : locations.values() )
                {
                    Point3D correctedImageCenter = position.subtract(offsetToImageCenter);
                    trackOffsets.add( Utils.computeOffsetFromCenterSize(correctedImageCenter, pImageSize) );
                }

                pCropSize = pImageSize;

            }
            else
            {
                //  crop around the object

                double croppingFactorValue;

                try
                {
                    croppingFactorValue = Double.parseDouble(croppingFactor);
                }
                catch(NumberFormatException nfe)
                {
                    logger.error("Please either enter 'all' or a number as the Cropping Factor");
                    return null;
                }

                pCropSize = track.getObjectSize().multiply( croppingFactorValue );

                for( Point3D position : locations.values() )
                {
                    trackOffsets.add( Utils.computeOffsetFromCenterSize( position, pCropSize ) );
                }

            }

            ImagePlus impCroppedAlongObject = null;

            if ( track.getImp().getStack() instanceof VirtualStack2 )
            {
                impCroppedAlongObject = BigDataConverter.getCroppedVS2(
                        track.getImp(),
                        trackOffsets.toArray( new Point3D[ trackOffsets.size() ] ),
                        pCropSize,
                        track.getTmin(),
                        track.getTmax());
            }
            else
            {
                impCroppedAlongObject = getCroppedImagePlus(
                        track.getImp(),
                        trackOffsets.toArray( new Point3D[trackOffsets.size()] ),
                        pCropSize,
                        track.getTmin(),
                        track.getTmax());
            }

            impCroppedAlongObject.setTitle("Track_" + track.getID());
            imps.add(impCroppedAlongObject);

        }

        return imps;

    }

    private ImagePlus getCroppedImagePlus(ImagePlus imp, Point3D[] po, Point3D ps, int tMin, int tMax)
    {

        int nC = imp.getNChannels();
        int nT = tMax - tMin + 1;
        int nZ = imp.getNSlices();

        ImageStack stackOut = null;

        for (int c = 0; c < nC; c++)
        {
            for (int t = tMin; t <= tMax; t++)
            {

                Region5D region5D = new Region5D();
                region5D.t = t;
                region5D.c = c;
                region5D.offset = po[t-tMin];
                region5D.size = ps;
                region5D.subSampling = new Point3D(1,1,1);

                ImagePlus imp2 = Utils.getDataCube(imp, region5D);
                ImageStack stack2 = imp2.getStack();

                int n = stack2.getSize();

                for ( int i = 1; i <= n; i++ )
                {
                    ImageProcessor ip2 = stack2.getProcessor(i);
                    if (stackOut == null)
                        stackOut = new ImageStack(ip2.getWidth(), ip2.getHeight(), imp.getProcessor().getColorModel());
                    stackOut.addSlice( stack2.getSliceLabel(i), ip2 );
                }
            }
        }

        ImagePlus impOut = imp.createImagePlus();
        impOut.setStack("DUP_"+imp.getTitle(), stackOut);
        String info = (String)imp.getProperty("Info");
        if (info!=null)
            impOut.setProperty("Info", info);
        int[] dim = imp.getDimensions();

        return ( impOut );

    }

    public synchronized void addLocationToOverlay( final Track track, int t ) {

        int rx = (int) track.getObjectSize().getX()/2;
        int ry = (int) track.getObjectSize().getY()/2;
        int rz = (int) track.getObjectSize().getZ()/2;

        Roi roi;
        Overlay o = track.getImp().getOverlay();
        if(o==null) {
            o = new Overlay();
            track.getImp().setOverlay(o);
        }

        int x = (int) track.getPosition(t).getX();
        int y = (int) track.getPosition(t).getY();
        int z = (int) track.getPosition(t).getZ();  // one-based in IJ
        int c = (int) track.getC();

        int rrx, rry;
        for(int iz=0; iz<track.getImp().getNSlices(); iz++) {
            rrx = Math.max(rx/(Math.abs(iz-z)+1),1);
            rry = Math.max(ry/(Math.abs(iz-z)+1),1);
            roi = new Roi(x - rrx, y - rry, 2 * rrx + 1, 2 * rry + 1);
            roi.setPosition(c+1, iz+1, t+1);
            o.add(roi);
        }
    }

    public synchronized Track addNewTrack( TrackingSettings trackingSettings ) {

        int trackID = tracks.size(); // TODO: something else here as ID?
        tracks.add(new Track(trackingSettings, trackID));
        return(tracks.get(tracks.size()-1));

    }

    public void trackObject( TrackingSettings trackingSettings )
    {
        interruptTrackingThreads = false;

        if ( trackingSettings.trackingMethod.equals( CORRELATION ) )
        {
            CorrelationTracker objectTracker = new CorrelationTracker(this, trackingSettings, logger, 1);
            es.execute( objectTracker );
        }

    }

    public void cancelTracking()
    {
        logger.info("Stopping all tracking...");
        interruptTrackingThreads = true;
    }

}


/*
    public int addTrackStartWholeDataSet(ImagePlus imp) {
        int t;

        int ntTracking = nt;
        t = imp.getT()-1;

        if(t+nt > imp.getNFrames()) {
            logger.error("Your track would be longer than the movie!\n" +
                    "Please\n- reduce the 'Track length', or\n- move the time slider to an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = tracks.size();
        //info("added new track start; trackID = "+newTrackID+"; starting [frame] = "+t+"; length [frames] = "+ntTracking);
        tracks.add(new Track(ntTracking));
        tracks.get(newTrackID).addLocation(new Point3D(0, 0, imp.getZ()-1), t, imp.getC()-1);

        return(newTrackID);

    }
*/


/*

    public int getNumberOfUncompletedTracks() {
        int uncomplete = 0;
        for(Track t:tracks) {
            if(!t.completed)
                uncomplete++;
        }
        return uncomplete;
    }


 */