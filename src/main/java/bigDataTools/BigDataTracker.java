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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.imglib.algorithm.fft.PhaseCorrelationPeak;
import mpicbg.imglib.image.ImagePlusAdapter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


// todo: expose the number of iterations for the center of mass to the gui
// todo: correlation tracking: it did not show an error when no object was selected

// todo: add unique track id to tracktable, track, and imageName of cropped track

// todo: multi-point selection tool
// todo: track-jTableSpots: load button

    // todo: make frames around buttons that belong together


public class BigDataTracker implements PlugIn {

    ImagePlus imp;
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables

    ArrayList<Track> tracks = new ArrayList<Track>();
    ArrayList<Roi> rTrackStarts = new ArrayList<Roi>();
    String gui_trackingMethod = "center of mass";
    String gui_centeringMethod = "center of mass";
    TrackTable trackTable;
    long trackStatsStartTime;
    long trackStatsReportDelay = 200;
    long trackStatsLastReport = System.currentTimeMillis();
    int totalTimePointsToBeTracked = 0;
    AtomicInteger totalTimePointsTracked = new AtomicInteger(0);
    AtomicInteger totalTimeSpentTracking = new AtomicInteger(0);
    long trackStatsLastTrackStarted;
    int trackStatsTotalPointsTrackedAtLastStart;

    ExecutorService es = Executors.newCachedThreadPool();

    Logger logger = new IJLazySwingLogger();

    public BigDataTracker(ImagePlus imp) {
        this.imp = imp;
        this.trackTable = new TrackTable();
    }

    class TrackTable  {
        JTable table;

        public TrackTable() {
            String[] columnNames = {"ID_T",
                    "X",
                    "Y",
                    "Z",
                    "T",
                    "ID"
                    //,
                    //"t_TotalSum",
                    //"t_ReadThis",
                    //"t_ProcessThis"
            };

            DefaultTableModel model = new DefaultTableModel(columnNames,0);
            table = new JTable(model);
            table.setPreferredScrollableViewportSize(new Dimension(500, 200));
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
        }

        public void addRow(final Object[] row) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.addRow(row);
                }
            });
        }

        public void clear() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                }
            });
        }

        public JTable getTable() {
            return table;
        }

    }

    public JTable getTrackTable() {
        return trackTable.getTable();
    }

    public void saveTrackTable(File file) {
        try{
            TableModel model = trackTable.getTable().getModel();
            FileWriter excel = new FileWriter(file);

            for(int i = 0; i < model.getColumnCount(); i++){
                excel.write(model.getColumnName(i) + "\t");
            }
            excel.write("\n");

            for(int i=0; i< model.getRowCount(); i++) {
                for(int j=0; j < model.getColumnCount(); j++) {
                    excel.write(model.getValueAt(i,j).toString()+"\t");
                }
                excel.write("\n");
            }
            excel.close();

        } catch(IOException e) { logger.error(e.toString()); }
    }

    public ImagePlus[] getViewsOnTrackedObjects() {

        ImagePlus[] imps = new ImagePlus[tracks.size()];

        for(int i=0; i< tracks.size(); i++) {

            Track track = tracks.get(i);

            if (track.completed) {


                //
                // convert track center coordinates to bounding box offsets
                //
                Point3D[] trackOffsets = new Point3D[track.getLength()];

                if( gui_croppingFactor.equals("all") ) {

                    // the object was used to "drift correct" the whole image
                    // thus, show the whole image
                    ImagePlus imp = track.getImp();
                    Point3D pImageSize = new Point3D(imp.getWidth(), imp.getHeight(), imp.getNSlices());
                    Point3D pImageCenter = pImageSize.multiply(0.5);
                    Point3D offsetToImageCenter = track.getXYZ(0).subtract(pImageCenter);
                    for( int iPosition = 0; iPosition < track.getLength(); iPosition++ )
                    {
                        Point3D correctedImageCenter = track.getXYZ(iPosition).subtract(offsetToImageCenter);
                        trackOffsets[iPosition] = computeOffset(correctedImageCenter, pImageSize);
                    }
                    imps[i] = DataStreamingTools.getCroppedVSS(track.getImp(), trackOffsets, pImageSize, track
                            .getTmin(), track.getTmax());

                }
                else
                {
                    //  crop around the object

                    double croppingFactor;

                    try
                    {
                        croppingFactor = Double.parseDouble(gui_croppingFactor);
                    }
                    catch(NumberFormatException nfe)
                    {
                        logger.error("Please either enter 'all' or a number as the Cropping Factor");
                        return;
                    }

                    Point3D pObjectSize = track.getObjectSize().multiply(croppingFactor);

                    for (int iPosition = 0; iPosition < track.getLength(); iPosition++)
                    {
                        trackOffsets[iPosition] = computeOffset(track.getXYZ(iPosition), pObjectSize);
                    }
                    imps[i] = DataStreamingTools.getCroppedVSS(track.getImp(), trackOffsets, pObjectSize, track
                            .getTmin(), track.getTmax());

                }

            }
        }

        return imps;

    }

    public void addTrackToOverlay(final Track track, final int i) {
        // using invokeLater to avoid that two different tracking threads change the imp overlay
        // concurrently; which could lead to disruption of the imp overlay
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int rx = (int) track.getObjectSize().getX()/2;
                int ry = (int) track.getObjectSize().getY()/2;
                int rz = (int) track.getObjectSize().getZ()/2;

                Roi roi;
                Overlay o = imp.getOverlay();
                if(o==null) {
                    o = new Overlay();
                    imp.setOverlay(o);
                }

                int x = (int) track.getX(i);
                int y = (int) track.getY(i);
                int z = (int) track.getZ(i);
                int c = (int) track.getC(i);
                int t = (int) track.getT(i);

                int rrx, rry;
                for(int iz=0; iz<imp.getNSlices(); iz++) {
                    rrx = Math.max(rx/(Math.abs(iz-z)+1),1);
                    rry = Math.max(ry/(Math.abs(iz-z)+1),1);
                    roi = new Roi(x - rrx, y - rry, 2 * rrx + 1, 2 * rry + 1);
                    roi.setPosition(c+1, iz+1, t+1);
                    o.add(roi);
                }
            }
        });
    }

    private int addTrackStart(Roi roi) {
        Point3D pTrackCenter = null;

        int t;

        if(roi.getTypeAsString().equals("Point")) {
            pTrackCenter = new Point3D(roi.getPolygon().xpoints[0],
                    roi.getPolygon().ypoints[0],
                    imp.getZ()-1);
        } else {
            logger.error("Please use the point selection tool to mark an object.");
            return(-1);
        }

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;
        if( t+gui_ntTracking > imp.getNFrames() )
        {
            ntTracking = imp.getNFrames() - t;
            logger.warning("Due to the requested track length, the track would have been longer than the movie; " +
                    "the length of the track was thus adjusted.");
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = tracks.size();

        tracks.add(new Track(ntTracking));
        Track track = tracks.get(newTrackID);
        track.setID(newTrackID);
        track.setImp(imp);
        track.addLocation(pTrackCenter, t, imp.getC() - 1);
        track.setObjectSize(gui_pTrackingSize);

        return(newTrackID);

    }

    public ObjectTracker trackObject(Roi roi, Point3D pSubSample, int gui_tSubSample, int iterations,
                            double trackingFactor, int background)
    {
        int iTrack = addTrackStart(roi);
        ObjectTracker objectTracker = new ObjectTracker(imp, roi, pSubSample, gui_tSubSample, iterations, trackingFactor, background, tracks.get(iTrack));

        es.execute(objectTracker);

        return objectTracker;

    }

}


/*
    public int addTrackStartWholeDataSet(ImagePlus imp) {
        int t;

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;

        if(t+gui_ntTracking > imp.getNFrames()) {
            logger.error("Your track would be longer than the movie!\n" +
                    "Please\n- reduce the 'Track length', or\n- move the time slider to an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = tracks.size();
        //info("added new track start; id = "+newTrackID+"; starting [frame] = "+t+"; length [frames] = "+ntTracking);
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