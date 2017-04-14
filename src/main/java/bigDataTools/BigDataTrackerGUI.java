package bigDataTools;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import javafx.geometry.Point3D;

import javax.swing.*;
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

public class BigDataTrackerGUI implements ActionListener, FocusListener
{
    JFrame frame;

    int gui_ntTracking;
    int gui_bg;
    int gui_iterations = 6;
    Point3D gui_pTrackingSize;
    double gui_trackingFactor = 2.5;
    Point3D gui_pSubSample = new Point3D(1,1,1);
    int gui_tSubSample = 1;
    private String gui_croppingFactor = "1.0";
    private int gui_background = 0;

    BigDataTracker bigDataTracker;
    TrackTablePanel trackTablePanel;

    ImagePlus imp;

    Logger logger = new IJLazySwingLogger();

    String[] texts = {
            "Object size: x,y,z [pixels]",
            "Window size [factor]",
            "xy-binning & zt-sub-sampling: dx, dy, dz, dt [pixels, frames]",
            "Length [frames]",
            "Background value [gray values]",
            "Resize the tracked region by [factor]"
    };

    String[] buttonActions = {
            "Set x&y from ROI",
            "Set z",
            "Track selected object",
            "Show jTableSpots",
            "Save jTableSpots",
            "Clear all tracks",
            "View as new stream",
            "Report issue"
    };


    String[] defaults = {
            String.valueOf((int) gui_pTrackingSize.getX()) + "," + (int) gui_pTrackingSize.getY() + "," +String.valueOf((int) gui_pTrackingSize.getZ()),
            String.valueOf(gui_trackingFactor),
            String.valueOf((int) gui_pSubSample.getX() + "," + (int) gui_pSubSample.getY() + "," + (int) gui_pSubSample.getZ() + "," + gui_tSubSample),
            String.valueOf(imp.getNFrames()),
            String.valueOf(gui_background),
            String.valueOf(gui_croppingFactor)
    };

    String[] comboNames = {
            "Method"
    };

    String[][] comboChoices = {
            {"center of mass","correlation"}
    };

    JTextField[] textFields = new JTextField[texts.length];
    JLabel[] labels = new JLabel[texts.length];

    int previouslySelectedZ = -1;

    public void BigDataTrackerGUI(ImagePlus imp)
    {
        if ( !Utils.hasVirtualStackOfStacks(imp) ) return;

        gui_pTrackingSize = new Point3D(55,55,40);
        gui_ntTracking = imp.getNFrames();
        gui_bg = (int) imp.getProcessor().getMin();

        this.imp = imp;
        this.bigDataTracker = new BigDataTracker(imp);
        trackTablePanel = new TrackTablePanel(bigDataTracker.getTrackTable());
    }

    public void showDialog()
    {

        frame = new JFrame("Big Data Tracker");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container c = frame.getContentPane();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

        String[] toolTipTexts = getToolTipFile("TrackAndCropHelp.html");
        int iToolTipText = 0;

        //
        // Configure all TextFields
        //
        for (int i = 0; i < textFields.length; i++, iToolTipText++)
        {
            textFields[i] = new JTextField(12);
            textFields[i].setActionCommand(texts[i]);
            textFields[i].addActionListener(this);
            textFields[i].addFocusListener(this);
            textFields[i].setText(defaults[i]);
            textFields[i].setToolTipText(toolTipTexts[iToolTipText]);
            labels[i] = new JLabel(texts[i] + ": ");
            labels[i].setLabelFor(textFields[i]);
        }

        //
        // Buttons
        //
        JButton[] buttons = new JButton[buttonActions.length];

        for (int i = 0; i < buttons.length; i++, iToolTipText++) {
            buttons[i] = new JButton(buttonActions[i]);
            buttons[i].setActionCommand(buttonActions[i]);
            buttons[i].addActionListener(this);
            buttons[i].setToolTipText(toolTipTexts[iToolTipText]);
        }

        //
        // ComboBoxes
        //
        JComboBox[] comboBoxes = new JComboBox[comboNames.length];
        JLabel[] comboLabels = new JLabel[comboNames.length];

        for (int i = 0; i < comboChoices.length; i++, iToolTipText++) {
            comboBoxes[i] = new JComboBox(comboChoices[i]);
            comboBoxes[i].setActionCommand(comboNames[i]);
            comboBoxes[i].addActionListener(this);
            comboBoxes[i].setToolTipText(toolTipTexts[iToolTipText]);
            comboLabels[i] = new JLabel(comboNames[i] + ": ");
            comboLabels[i].setLabelFor(comboBoxes[i]);
        }

        //
        // Panels
        //
        int i = 0;
        ArrayList<JPanel> panels = new ArrayList<JPanel>();
        int iPanel = 0;
        int k = 0;
        int iComboBox = 0;
        //
        // TRACKING
        //
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("TRACKING"));
        c.add(panels.get(iPanel++));
        // Object size
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(buttons[i++]);
        panels.get(iPanel).add(buttons[i++]);
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // Window size
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // Subsampling
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // Length
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // Method
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(comboLabels[iComboBox]);
        panels.get(iPanel).add(comboBoxes[iComboBox++]);
        c.add(panels.get(iPanel++));
        // Background value (this will be subtracted from the image)
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // ObjectTracker button
        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get(iPanel).add(buttons[i++]);
        c.add(panels.get(iPanel++));
        //
        // RESULTS TABLE
        //
        c.add(new JSeparator(SwingConstants.HORIZONTAL));
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("RESULTS TABLE"));
        c.add(panels.get(iPanel++));
        // Table buttons
        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get(iPanel).add(buttons[i++]);
        panels.get(iPanel).add(buttons[i++]);
        panels.get(iPanel).add(buttons[i++]);
        c.add(panels.get(iPanel++));
        //
        // CROPPING
        //
        c.add(new JSeparator(SwingConstants.HORIZONTAL));
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("VIEW TRACKED OBJECTS"));
        c.add(panels.get(iPanel++));

        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        panels.get(iPanel).add(buttons[i++]);
        c.add(panels.get(iPanel++));
        //
        // MISCELLANEOUS
        //
        c.add(new JSeparator(SwingConstants.HORIZONTAL));
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("MISCELLANEOUS"));
        c.add(panels.get(iPanel++));

        panels.add(new JPanel());
        panels.get(iPanel).add(buttons[i++]);
        c.add(panels.get(iPanel++));

        //
        // Show the GUI
        //
        frame.pack();
        frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
        frame.setVisible(true);

    }

    public void changeTextField(int i, String text) {
        textFields[i].setText(text);
    }

    public void focusGained(FocusEvent e) {
        //
    }

    public void focusLost(FocusEvent e) {
        JTextField tf = (JTextField) e.getSource();
        if (!(tf == null)) {
            tf.postActionEvent();
        }
    }

    public void actionPerformed(ActionEvent e) {

        int i = 0, j = 0, k = 0;
        JFileChooser fc;

        if ( !Utils.hasVirtualStackOfStacks(imp) ) return;
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

        if (e.getActionCommand().equals(buttonActions[i++])) {

            //
            // Set nx, ny
            //

            Roi r = imp.getRoi();

            if(r==null || !r.getTypeAsString().equals("Rectangle")) {
                logger.error("Please put a rectangular selection on the image");
                return;
            }

            gui_pTrackingSize = new Point3D((int)r.getFloatWidth(), (int)r.getFloatHeight(), gui_pTrackingSize.getZ() );
            changeTextField(0, "" + (int) gui_pTrackingSize.getX() + "," + (int) gui_pTrackingSize.getY() + "," +
                    (int) gui_pTrackingSize.getZ());

        } else if (e.getActionCommand().equals(buttonActions[i++])) {
            //
            //  Set nz
            //

            int z = imp.getZ()-1;
            if (previouslySelectedZ==-1) {
                // first time do nothing
            } else {
                int nz = Math.abs(z - previouslySelectedZ);
                gui_pTrackingSize = new Point3D(gui_pTrackingSize.getX(), gui_pTrackingSize.getY(), nz);
                changeTextField(0, "" + (int) gui_pTrackingSize.getX() + "," + (int) gui_pTrackingSize.getY() + "," +
                        (int) gui_pTrackingSize.getZ());
            }
            previouslySelectedZ = z;

        } else if (e.getActionCommand().equals(buttonActions[i++])) {

            //
            // Track selected object
            //

            Roi roi = imp.getRoi();
            if (roi == null || ! (roi.getTypeAsString().equals("Point") || roi.getTypeAsString().equals("Rectangle")) ) {
                logger.error("Please use ImageJ's Point selection tool on image: '"
                        + imp.getTitle() + "'");
                return;
            }

            //
            // automatically adjust the number of iterations for the center of mass
            //

            gui_iterations = (int) Math.ceil(Math.pow(gui_trackingFactor, 2)); // this purely gut-feeling

            //trackStatsLastTrackStarted = System.currentTimeMillis();
            //trackStatsTotalPointsTrackedAtLastStart = totalTimePointsTracked.get();
            //Runnable objectTracker = new bigDataTracker.ObjectTracker();


            ObjectTracker objectTracker = bigDataTracker.trackObject(roi, gui_pSubSample,
                                                        gui_tSubSample, gui_iterations,
                                                        gui_trackingFactor, gui_background);

            // TODO: monitor progress

        }
        else if ( e.getActionCommand().equals(buttonActions[i++]) )
        {

            //
            // Show Table
            //

            showTrackTable();

        }
        else if (e.getActionCommand().equals(buttonActions[i++]))
        {

            //
            // Save Table
            //

            TableModel model = trackTable.getTable().getModel();
            if(model == null) {
                logger.error("There are no tracks yet.");
                return;
            }
            fc = new JFileChooser(vss.getDirectory());
            if (fc.showSaveDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                saveTrackTable(file);
            }

        }
        else if ( e.getActionCommand().equals(buttonActions[i++]) )
        {

            //
            // Clear Table and all tracks
            //

            trackTable.clear();
            tracks = new ArrayList<Track>();
            rTrackStarts = new ArrayList<Roi>();

            // remove overlay
            imp.setOverlay(new Overlay());

            totalTimePointsTracked.set(0);
            totalTimePointsToBeTracked = 0;


        } else if (e.getActionCommand().equals(buttonActions[i++])) {

            //
            // View Object tracks
            //

            showTrackedObjects();


        } else if (e.getActionCommand().equals(buttonActions[i++])) {

            //
            // Report issue
            //

            if (Desktop.isDesktopSupported()) {
                try {
                    final URI uri = new URI("https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues");
                    Desktop.getDesktop().browse(uri);
                } catch (URISyntaxException uriEx) {
                    logger.error(uriEx.toString());
                } catch (IOException ioEx) {
                    logger.error(ioEx.toString());
                }
            } else { /* TODO: error handling */ }

        } else if (e.getActionCommand().equals(texts[k++])) {
            //
            // ObjectTracker object size
            //
            JTextField source = (JTextField) e.getSource();
            String[] sA = source.getText().split(",");
            gui_pTrackingSize = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
        }
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // ObjectTracker factor
            //
            JTextField source = (JTextField) e.getSource();
            gui_trackingFactor = new Double(source.getText());
        }
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // ObjectTracker sub-sampling
            //
            JTextField source = (JTextField) e.getSource();
            String[] sA = source.getText().split(",");
            gui_pSubSample = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
            gui_tSubSample = new Integer(sA[3]);
        }
        else if ( e.getActionCommand().equals(texts[k++]) )
        {
            //
            // Track length
            //
            JTextField source = (JTextField) e.getSource();
            gui_ntTracking = new Integer(source.getText());
        }
        else if ( e.getActionCommand().equals(texts[k++]) )
        {
            //
            // Image background value
            //
            JTextField source = (JTextField) e.getSource();
            gui_background = new Integer(source.getText());
        }
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // Cropping factor
            //
            JTextField source = (JTextField) e.getSource();
            gui_croppingFactor = source.getText();
        }
        else if (e.getActionCommand().equals(comboNames[j++]))
        {
            //
            // ObjectTracker method
            //
            JComboBox cb = (JComboBox)e.getSource();
            gui_trackingMethod = (String)cb.getSelectedItem();
        }
    }

    private String[] getToolTipFile(String fileName) {
        ArrayList<String> toolTipTexts = new ArrayList<String>();

        //Get file from resources folder
        InputStream in = getClass().getResourceAsStream("/"+fileName);
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        Scanner scanner = new Scanner(input);
        StringBuilder sb = new StringBuilder("");

        while ( scanner.hasNextLine() )
        {
            String line = scanner.nextLine();
            if(line.equals("###")) {
                toolTipTexts.add(sb.toString());
                sb = new StringBuilder("");
            } else {
                sb.append(line);
            }

        }

        scanner.close();


        return(toolTipTexts.toArray(new String[0]));
    }

    class TrackTablePanel extends JPanel implements MouseListener, KeyListener {
        private boolean DEBUG = false;
        JTable table;
        JFrame frame;
        JScrollPane scrollPane;

        public TrackTablePanel(JTable table) {
            super(new GridLayout(1, 0));

            this.table = table;
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
            frame.setLocation(trackingGUI.getFrame().getX() + trackingGUI.getFrame().getWidth(), trackingGUI.getFrame().getY());
            frame.setVisible(true);
        }

        public void highlightSelectedTrack() {
            int rs = table.getSelectedRow();
            int r = table.convertRowIndexToModel(rs);
            float x = new Float(table.getModel().getValueAt(r, 1).toString());
            float y = new Float(table.getModel().getValueAt(r, 2).toString());
            float z = new Float(table.getModel().getValueAt(r, 3).toString());
            int t = new Integer(table.getModel().getValueAt(r, 4).toString());
            int id = new Integer(table.getModel().getValueAt(r, 5).toString());
            ImagePlus imp = tracks.get(id).getImp();
            imp.setPosition(0,(int)z+1,t+1);
            Roi pr = new PointRoi(x,y);
            pr.setPosition(0,(int)z+1,t+1);
            imp.setRoi(pr);
            //info(" rs="+rs+" r ="+r+" x="+x+" y="+y+" z="+z+" t="+t);
            //info("t="+jTableSpots.getModel().getValueAt(r, 5));
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

    public void showTrackTable()
    {
        trackTablePanel.showTable();
    }

    public void showTrackedObjects() {

        ImagePlus[] imps = bigDataTracker.getViewsOnTrackedObjects();

        if( imps[i] == null )
        {
            logger.info("The cropping failed!");
        }
        else
        {
            imps[i].setTitle(imp.getTitle()+"Track_" + track.getID());
            imps[i].show();
            imps[i].setPosition(0, (int) (imps[i].getNSlices() / 2 + 0.5), 0);
            imps[i].resetDisplayRange();
        }
    }

}
