package bigDataTools.bigDataTracker;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

/**
 * Created by tischi on 14/04/17.
 */

class TrackTablePanel extends JPanel implements MouseListener, KeyListener {
    private boolean DEBUG = false;
    JTable table;
    JFrame frame;
    JScrollPane scrollPane;
    ArrayList<Track> tracks;

    public TrackTablePanel(TrackTable trackTable, ArrayList<Track> tracks) {
        super(new GridLayout(1, 0));

        this.table = trackTable.getTable();
        this.tracks = tracks;

        table.setPreferredScrollableViewportSize(new Dimension(500, 200));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(true);
        table.addMouseListener(this);
        table.addKeyListener(this);

        //Create the scroll pane and add the jTableSpots to it.
        scrollPane = new JScrollPane(table);

        //Add the scroll pane to this panel.
        add(scrollPane);
    }

    public void showTable() {
        //Create and set up the window.
        frame = new JFrame("tracks");
        //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        this.setOpaque(true); //content panes must be opaque
        frame.setContentPane(this);

        //Display the window.
        frame.pack();
        //frame.setLocation(trackingGUI.getFrame().getX() + trackingGUI.getFrame().getWidth(), trackingGUI.getFrame().getY());
        frame.setVisible(true);
    }

    public void highlightSelectedTrack() {

        // TODO: somehow get the imp from the tracks
        ImagePlus imp = IJ.getImage();

        int rs = table.getSelectedRow();
        int r = table.convertRowIndexToModel(rs);
        float x = new Float(table.getModel().getValueAt(r, 1).toString());
        float y = new Float(table.getModel().getValueAt(r, 2).toString());
        float z = new Float(table.getModel().getValueAt(r, 3).toString());
        int t = new Integer(table.getModel().getValueAt(r, 4).toString());
        int id = new Integer(table.getModel().getValueAt(r, 5).toString());
        imp.setPosition(0,(int)z+1,t+1);
        Roi pr = new PointRoi(x,y);
        pr.setPosition(0,(int)z+1,t+1);
        imp.setRoi(pr);

    }

    @Override
    public void mouseClicked(MouseEvent e) {
        highlightSelectedTrack();
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {
        highlightSelectedTrack();
    }
}
