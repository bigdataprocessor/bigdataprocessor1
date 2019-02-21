package de.embl.cba.bigdataconverter.track;

import de.embl.cba.bigdataconverter.logging.IJLazySwingLogger;
import de.embl.cba.bigdataconverter.logging.Logger;
import de.embl.cba.bigdataconverter.utils.SpringUtilities;
import de.embl.cba.bigdataconverter.utils.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import javafx.geometry.Point3D;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class AdaptiveCropUI implements ActionListener, FocusListener
{

	public static final String CORRELATION = "Correlation";
	public static final String CENTER_OF_MASS = "Center of Mass";

	Point3D maxDisplacement = new Point3D(20,20,20);
    private String resizeFactor = "1.0";
    AdaptiveCrop adaptiveCrop;
    TrackTablePanel trackTablePanel;
    String[] defaults;
    TrackingSettings trackingSettings = new TrackingSettings();

    Logger logger = new IJLazySwingLogger();

    String[] texts = {
            "Region Size: x,y,z [pixels]",
            "Maximal Displacement between Frames: x,y,z [pixels]",
            "Subsample: dx, dy, dz, dt [pixels, frames]",
            "Track Length [frames]",
            "Resize Tracked Regions [factor]"
    };

    String[] buttonActions = {
            "Set x&y from ROI",
            "Set z",
            "Track",
            "Interrupt Tracking",
            "Show Table",
            "Save Table",
            "Clear All Tracks",
            "View"

    };
    
    String[] comboNames = { "Tracking Method" };

    String[][] comboChoices = new String[1][];

    JTextField[] textFields = new JTextField[texts.length];

    JLabel[] labels = new JLabel[texts.length];

    int previouslySelectedZ = -1;
    private JPanel mainPanel;
    private int iToolTipText;
    private JButton[] buttons;
    private JComboBox[] comboBoxes;
    private JLabel[] comboLabels;
    private int iPanel;
    private int iButton;
    private int iTextField;
    private int iComboBox;
    private ArrayList< JPanel > panels;
    private JTextField intensityGatingTF;
    private JTextField showProcessedRegionsTF;
    private JComboBox imageFilterChoice;
    private JCheckBox processTrackedRegion;

    public AdaptiveCropUI()
    {
        this.adaptiveCrop = new AdaptiveCrop();
        
        trackTablePanel = new TrackTablePanel(
                adaptiveCrop.getTrackTable(),
                adaptiveCrop.getTracks());

        comboChoices[0] = new String[]{ CENTER_OF_MASS, CORRELATION };
    }

    private void configureDefaultTrackingSettings()
    {
        trackingSettings.trackingMethod = CORRELATION;
        trackingSettings.objectSize = new Point3D( 30, 30, 10);
        trackingSettings.maxDisplacement = new Point3D( 20, 20, 20);
        trackingSettings.subSamplingXYZ = new Point3D( 1, 1, 1);
        trackingSettings.subSamplingT = 1;
        trackingSettings.nt = 3;
        trackingSettings.intensityGate = new int[]{-1,-1};
        trackingSettings.showProcessedRegions = 3;
        trackingSettings.imageFilterChoice = Utils.ImageFilterTypes.NONE.toString();
    }

    public void setTextFieldDefaults()
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
                "" + trackingSettings.showProcessedRegions,
                String.valueOf( resizeFactor )
        };

        this.defaults = defaults;
    }

    public JPanel getPanel()
    {
        mainPanel = new JPanel();

        configureDefaultTrackingSettings();

//        configureToolTips();
        configureTextFields();
        configureButtons();
        configureComboBoxes();

        panels = new ArrayList<>();
        addTrackingPanel();
        addTrackingTablePanel();
        addViewingPanel();

        return mainPanel;

    }

    private void addProcessingPanel()
    {
        final JPanel panel = new JPanel( new SpringLayout() );

        intensityGatingTF = new JTextField( 12 );
        intensityGatingTF.setText("" + trackingSettings.intensityGate[0] + "," + trackingSettings.intensityGate[1]);
        intensityGatingTF.setVisible( false );

        final JLabel intensityGatingLabel = new JLabel( "Intensity Gating [min, max]" );
        intensityGatingLabel.setVisible( false );

        final JLabel imageFilterLabel = new JLabel( "Process Region Filter" );
        imageFilterLabel.setVisible( false );
        imageFilterChoice = new JComboBox( getImageFilters() );
        imageFilterChoice.setVisible( false );

        showProcessedRegionsTF = new JTextField( 12 );
        showProcessedRegionsTF.setText( "3" );
        showProcessedRegionsTF.setVisible( false );
        final JLabel showProcessedRegionsLabel = new JLabel( "Show Processed Regions [#]" );
        showProcessedRegionsLabel.setVisible( false );

        processTrackedRegion = new JCheckBox( "Process Tracked Region" );
        processTrackedRegion.addItemListener( e -> {
            final boolean selected = processTrackedRegion.isSelected();
            intensityGatingLabel.setVisible( selected );
			intensityGatingTF.setVisible( selected );
			imageFilterChoice.setVisible( selected );
            imageFilterLabel.setVisible( selected );
			showProcessedRegionsLabel.setVisible( selected );
            showProcessedRegionsTF.setVisible( selected );
		} );

        panel.add( processTrackedRegion );
        panel.add( new JLabel("") );
        panel.add( intensityGatingLabel );
        panel.add( intensityGatingTF );
        panel.add( imageFilterLabel );
        panel.add( imageFilterChoice );
        panel.add( showProcessedRegionsLabel );
        panel.add( showProcessedRegionsTF );

        SpringUtilities.makeCompactGrid(
                panel,
                4, 2,
                6, 6,
                6, 6);

        mainPanel.add( panel );
    }

    private String[] getImageFilters()
    {
        String[] imageFilters = new String[ Utils.ImageFilterTypes.values().length];
        for ( int i = 0; i < imageFilters.length; i++ )
        {
            imageFilters[i] = Utils.ImageFilterTypes.values()[i].toString();
        }
        return imageFilters;
    }


    private void addViewingPanel()
    {
        mainPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get( iPanel ).add(new JLabel("VIEW TRACKED OBJECTS"));
        mainPanel.add( panels.get( iPanel++));

        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get( iPanel ).add(labels[ iTextField ]);
        panels.get( iPanel ).add(textFields[ iTextField++]);
        panels.get( iPanel ).add(buttons[ iButton++]);
        mainPanel.add( panels.get(iPanel++));
    }

    private void addTrackingTablePanel()
    {
        mainPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get( iPanel ).add(new JLabel("TRACKING TABLE"));
        mainPanel.add( panels.get( iPanel++));
        // Table buttons
        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get( iPanel ).add( buttons[ iButton++]);
        panels.get( iPanel ).add( buttons[ iButton++]);
        panels.get( iPanel ).add( buttons[ iButton++]);
        mainPanel.add( panels.get( iPanel++));
    }

    private void addTrackingPanel()
    {
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get( iPanel ).add(new JLabel("TRACKING"));
        mainPanel.add( panels.get( iPanel++));
        // Object size
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get( iPanel ).add(buttons[iButton++]);
        panels.get( iPanel ).add(buttons[iButton++]);
        panels.get( iPanel ).add(labels[iTextField]);
        panels.get( iPanel ).add(textFields[iTextField++]);
        mainPanel.add( panels.get( iPanel++));
        // Window size
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get( iPanel ).add(labels[ iTextField ]);
        panels.get( iPanel ).add(textFields[ iTextField++]);
        mainPanel.add( panels.get( iPanel++));
        // Subsampling
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get( iPanel ).add(labels[ iTextField ]);
        panels.get( iPanel ).add(textFields[ iTextField++]);
        mainPanel.add( panels.get( iPanel++));
        // Length
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get( iPanel ).add(labels[ iTextField ]);
        panels.get( iPanel ).add(textFields[ iTextField++]);
        mainPanel.add( panels.get( iPanel++));
        // Tracking Method
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get( iPanel ).add( comboLabels[ iComboBox ]);
        panels.get( iPanel ).add( comboBoxes[ iComboBox++]);
        mainPanel.add( panels.get( iPanel++));

        addProcessingPanel();

        // Tracking Buttons
        panels.add(new JPanel(new FlowLayout(FlowLayout.CENTER)));
        panels.get( iPanel ).add( buttons[ iButton++]);
        panels.get( iPanel ).add( buttons[ iButton++]);
        mainPanel.add( panels.get( iPanel++));
    }

    private void configureToolTips()
    {
        String[] toolTipTexts = getToolTipFile("TrackAndCropHelp.html");
        iToolTipText = 0;
    }

    private void configureComboBoxes()
    {
        comboBoxes = new JComboBox[comboNames.length];
        comboLabels = new JLabel[comboNames.length];

        for (int i = 0; i < comboChoices.length; i++, iToolTipText++) {
            comboBoxes[i] = new JComboBox(comboChoices[i]);
            comboBoxes[i].setActionCommand(comboNames[i]);
            comboBoxes[i].addActionListener(this);
            //comboBoxes[i].setToolTipText(toolTipTexts[iToolTipText]);
            comboLabels[i] = new JLabel(comboNames[i] + ": ");
            comboLabels[i].setLabelFor( comboBoxes[i]);
        }
    }

    private void configureButtons()
    {
        buttons = new JButton[buttonActions.length];

        for ( int i = 0; i < buttons.length; i++, iToolTipText++) {
            buttons[i] = new JButton(buttonActions[i]);
            buttons[i].setActionCommand(buttonActions[i]);
            buttons[i].addActionListener(this);
            //buttons[i].setToolTipText(toolTipTexts[iToolTipText]);
        }
    }

    private void configureTextFields()
    {
        setTextFieldDefaults();
        for (int i = 0; i < textFields.length; i++, iToolTipText++)
        {
            textFields[i] = new JTextField(12);
            textFields[i].setActionCommand(texts[i]);
            textFields[i].addActionListener(this);
            textFields[i].addFocusListener(this);
            textFields[i].setText(defaults[i]);
            //textFields[i].setToolTipText(toolTipTexts[iToolTipText]);
            labels[i] = new JLabel(texts[i] + ": ");
            labels[i].setLabelFor(textFields[i]);
        }
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

            trackingSettings.processImage = processTrackedRegion.isSelected();
            trackingSettings.intensityGate = Utils.delimitedStringToIntegerArray( intensityGatingTF.getText(), ",");
            trackingSettings.showProcessedRegions = new Integer( showProcessedRegionsTF.getText() );
            trackingSettings.imageFilterChoice = (String) imageFilterChoice.getSelectedItem();

            trackingSettings.imp = imp;

            // TODO: think about below:
            trackingSettings.trackingFactor = 1.0 + 2.0 * maxDisplacement.getX() /
                    trackingSettings.objectSize.getX() ;

            trackingSettings.iterationsCenterOfMass =
                     (int) Math.ceil(Math.pow(trackingSettings.trackingFactor, 2));

            trackingSettings.channel = imp.getC() - 1;

            trackingSettings.trackStartROI = roi;

            trackingSettings.nt = (((imp.getT()-1) + trackingSettings.nt) > imp.getNFrames())
                    ? imp.getNFrames() - (imp.getT()-1) : trackingSettings.nt;

            adaptiveCrop.trackObject( trackingSettings );

        }
        else if ( e.getActionCommand().equals(buttonActions[i++]) )
        {

            //
            // Cancel Tracking
            //

            adaptiveCrop.cancelTracking();

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

            TableModel model = adaptiveCrop.getTrackTable().getTable().getModel();
            if(model == null) {
                logger.error("There are no tracks yet.");
                return;
            }
            fc = new JFileChooser();
            if (fc.showSaveDialog(this.mainPanel) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                adaptiveCrop.getTrackTable().saveTrackTable(file);
            }

        }
        else if ( e.getActionCommand().equals(buttonActions[i++]) )
        {

            adaptiveCrop.clearAllTracks();


        }
        else if (e.getActionCommand().equals(buttonActions[i++])) {
            //
            // View Object tracks
            //
            showTrackedObjects();
        }
        else if (e.getActionCommand().equals(texts[k++])) {
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
        else if (e.getActionCommand().equals(texts[k++]))
        {
            //
            // Cropping factor
            //
            JTextField source = (JTextField) e.getSource();
            resizeFactor = source.getText();
        }
        else if ( e.getActionCommand().equals( comboNames[ 0 ]) )
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

        ArrayList<ImagePlus> imps = adaptiveCrop.getViewsOnTrackedObjects( resizeFactor );

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
