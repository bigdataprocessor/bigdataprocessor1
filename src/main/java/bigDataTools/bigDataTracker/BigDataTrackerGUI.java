package bigDataTools.bigDataTracker;

import bigDataTools.logging.IJLazySwingLogger;
import bigDataTools.logging.Logger;
import bigDataTools.utils.Utils;
import ij.IJ;
import ij.ImagePlus;
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

public class BigDataTrackerGUI implements ActionListener, FocusListener
{
    JFrame frame;

    Point3D maxDisplacement = new Point3D(20,20,20);
    private String resizeFactor = "1.0";
    BigDataTracker bigDataTracker;
    TrackTablePanel trackTablePanel;
    String TRACKING_LENGTH = "Length [frames]";
    String[] defaults;
    TrackingSettings trackingSettings = new TrackingSettings();

    Logger logger = new IJLazySwingLogger();

    String[] texts = {
            "Region size: x,y,z [pixels]",
            "Maximal displacement between subsequent frames: x,y,z [pixels]",
            "dx(bin), dy(bin), dz(subsample), dt(subsample) [pixels, frames]",
            TRACKING_LENGTH,
            "Intensity gating [min, max]",
            "Show N first processed image pairs [Num]",
            "Resize regions by [factor]"
    };

    String[] buttonActions = {
            "Set x&y from ROI",
            "Set z",
            "Track selected object",
            "Stop tracking",
            "Show table",
            "Save table",
            "Clear all tracks",
            "View as new stream",
            "Report issue"
    };


    String[] comboNames = {
            "Enhance image features",
            "Tracking method"
    };

    String[][] comboChoices = new String[2][];

    JTextField[] textFields = new JTextField[texts.length];

    JLabel[] labels = new JLabel[texts.length];

    int previouslySelectedZ = -1;

    public BigDataTrackerGUI()
    {
        ImagePlus imp = IJ.getImage();

        this.bigDataTracker = new BigDataTracker();
        trackTablePanel = new TrackTablePanel(bigDataTracker.getTrackTable(),
                bigDataTracker.getTracks());

        String[] imageFilters = new String[Utils.ImageFilterTypes.values().length];
        for ( int i = 0; i < imageFilters.length; i++ )
        {
            imageFilters[i] = Utils.ImageFilterTypes.values()[i].toString();
        }
        comboChoices[0] = imageFilters;
        comboChoices[1] = new String[]{"correlation","center of mass"};

        trackingSettings.trackingMethod = "correlation";
        trackingSettings.objectSize = new Point3D( 200, 200, 30);
        trackingSettings.maxDisplacement = new Point3D( 150, 150, 150);
        trackingSettings.subSamplingXYZ = new Point3D( 3, 3, 1);
        trackingSettings.subSamplingT = 1;
        trackingSettings.nt = imp.getNFrames();
        trackingSettings.intensityGate = new int[]{-1,-1};
        trackingSettings.viewFirstNProcessedRegions = 1;

        setDefaults();
    }

    public void setDefaults()
    {

        String[] defaults = {
                "" + (int) trackingSettings.objectSize.getX() + "," +
                        (int) trackingSettings.objectSize.getY() + "," +
                        (int) trackingSettings.objectSize.getZ(),
                "" + (int) trackingSettings.maxDisplacement.getX() + "," +
                        (int) trackingSettings.maxDisplacement.getY() + "," +
                        (int) trackingSettings.maxDisplacement.getZ(),
                "" + (int) trackingSettings.subSamplingXYZ.getX() + "," +
                        (int) trackingSettings.subSamplingXYZ.getY() + "," +
                        (int) trackingSettings.subSamplingXYZ.getZ() + "," +
                        trackingSettings.subSamplingT,
                "" + trackingSettings.nt,
                "" + trackingSettings.intensityGate[0] + "," +
                        trackingSettings.intensityGate[1],
                "" + trackingSettings.viewFirstNProcessedRegions,
                String.valueOf( resizeFactor )
        };

        this.defaults = defaults;
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
        // Intensity gating
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // Enhance features
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(comboLabels[iComboBox]);
        panels.get(iPanel).add(comboBoxes[iComboBox++]);
        c.add(panels.get(iPanel++));
        // View processed tracked region
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(labels[k]);
        panels.get(iPanel).add(textFields[k++]);
        c.add(panels.get(iPanel++));
        // Tracking Method
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(comboLabels[iComboBox]);
        panels.get(iPanel).add(comboBoxes[iComboBox++]);
        c.add(panels.get(iPanel++));
        // ObjectTracker button
        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get(iPanel).add(buttons[i++]);
        c.add(panels.get(iPanel++));
        // ObjectTracker cancel button
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
        //frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
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

        ImagePlus imp = IJ.getImage();

        if (e.getActionCommand().equals(buttonActions[i++]))
        {

            //
            // Set nx, ny
            //

            Roi r = imp.getRoi();

            if(r==null || !r.getTypeAsString().equals("Rectangle")) {
                logger.error("Please put a rectangular selection on the image");
                return;
            }

            trackingSettings.objectSize = new Point3D((int)r.getFloatWidth(),
                    (int)r.getFloatHeight(), trackingSettings.objectSize.getZ() );

            changeTextField(0, "" + (int) trackingSettings.objectSize.getX() + "," +
                    (int) trackingSettings.objectSize.getY() + "," +
                    (int) trackingSettings.objectSize.getZ());

        }
        else if (e.getActionCommand().equals(buttonActions[i++]))
        {

            //
            //  Set nz
            //

            int z = imp.getZ()-1;
            if (previouslySelectedZ==-1) {
                // first time do nothing
            } else {
                int nz = Math.abs(z - previouslySelectedZ);
                trackingSettings.objectSize = new Point3D(trackingSettings.objectSize.getX(),
                        trackingSettings.objectSize.getY(), nz);

                changeTextField(0, "" + (int) trackingSettings.objectSize.getX() + "," +
                        (int) trackingSettings.objectSize.getY() + "," +
                        (int) trackingSettings.objectSize.getZ());
            }
            previouslySelectedZ = z;

        }
        else if (e.getActionCommand().equals(buttonActions[i++]))
        {

            //
            // track selected object
            //

            // check roi
            //
            Roi roi = imp.getRoi();
            if ( roi == null || !(roi.getTypeAsString().equals("Point")) )
            {
                logger.error("Please use ImageJ's Point selection tool on image: '"
                        + imp.getTitle() + "'");
                return;
            }

            //
            // configure tracking
            //

            trackingSettings.imp = imp;

            // TODO: think about below:
            trackingSettings.trackingFactor = 1.0 + 1.0 * maxDisplacement.getX() /
                    trackingSettings.objectSize.getX() ;

            trackingSettings.iterationsCenterOfMass =
                     (int) Math.ceil(Math.pow(trackingSettings.trackingFactor, 2));

            trackingSettings.channel = imp.getC() - 1;

            trackingSettings.trackStartROI = roi;

            trackingSettings.nt = (((imp.getT()-1) + trackingSettings.nt) > imp.getNFrames())
                    ? imp.getNFrames() - (imp.getT()-1) : trackingSettings.nt;

            // do it
            //
            bigDataTracker.trackObject( trackingSettings );

        }
        else if ( e.getActionCommand().equals(buttonActions[i++]) )
        {

            //
            // Cancel Tracking
            //

            bigDataTracker.cancelTracking();

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

            TableModel model = bigDataTracker.getTrackTable().getTable().getModel();
            if(model == null) {
                logger.error("There are no tracks yet.");
                return;
            }
            fc = new JFileChooser();
            if (fc.showSaveDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                bigDataTracker.getTrackTable().saveTrackTable(file);
            }

        }
        else if ( e.getActionCommand().equals(buttonActions[i++]) )
        {

            bigDataTracker.clearAllTracks();


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
            trackingSettings.objectSize = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
        }
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // ObjectTracker maximal displacements
            //
            JTextField source = (JTextField) e.getSource();
            String[] sA = source.getText().split(",");
            trackingSettings.maxDisplacement = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
        }
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // ObjectTracker sub-sampling
            //
            JTextField source = (JTextField) e.getSource();
            String[] sA = source.getText().split(",");
            trackingSettings.subSamplingXYZ = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
            trackingSettings.subSamplingT = new Integer(sA[3]);
        }
        else if ( e.getActionCommand().equals(texts[k++]) )
        {
            //
            // Track length
            //
            JTextField source = (JTextField) e.getSource();
            trackingSettings.nt = new Integer(source.getText());
        }
        else if ( e.getActionCommand().equals(texts[k++]) )
        {
            //
            // Image intensityGate value
            //
            JTextField source = (JTextField) e.getSource();
            trackingSettings.intensityGate = Utils.delimitedStringToIntegerArray( source.getText(), ",");
        }
        else if ( e.getActionCommand().equals(texts[k++]) )
        {
            //
            // Show processed image regions
            //
            JTextField source = (JTextField) e.getSource();
            trackingSettings.viewFirstNProcessedRegions = new Integer(source.getText());;
        }
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // Cropping factor
            //
            JTextField source = (JTextField) e.getSource();
            resizeFactor = source.getText();
        }
        else if ( e.getActionCommand().equals( comboNames[0]) )
        {
            //
            // Image feature enhancement method
            //
            JComboBox cb = (JComboBox)e.getSource();
            trackingSettings.imageFeatureEnhancement = (String)cb.getSelectedItem();
        }
        else if ( e.getActionCommand().equals( comboNames[1]) )
        {
            //
            // ObjectTracker method
            //
            JComboBox cb = (JComboBox)e.getSource();
            trackingSettings.trackingMethod = (String)cb.getSelectedItem();
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

    public void showTrackTable()
    {
        trackTablePanel.showTable();
    }

    public void showTrackedObjects() {

        ArrayList<ImagePlus> imps = bigDataTracker.getViewsOnTrackedObjects(resizeFactor);

        if( imps == null )
        {
            logger.info("The cropping failed!");
        }
        else
        {
            for (ImagePlus imp : imps)
            {
                Utils.show(imp);
            }
        }
    }

}
