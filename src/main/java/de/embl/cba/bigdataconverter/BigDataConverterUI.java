package de.embl.cba.bigdataconverter;

import de.embl.cba.bigdataconverter.track.AdaptiveCropUI;
import de.embl.cba.bigdataconverter.imaris.ImarisUtils;
import de.embl.cba.bigdataconverter.logging.IJLazySwingLogger;
import de.embl.cba.bigdataconverter.logging.Logger;
import de.embl.cba.bigdataconverter.saving.SavingSettings;
import de.embl.cba.bigdataconverter.utils.ImageDataInfo;
import de.embl.cba.bigdataconverter.utils.SpringUtilities;
import de.embl.cba.bigdataconverter.utils.Utils;
import de.embl.cba.bigdataconverter.virtualstack2.VirtualStack2;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import javafx.geometry.Point3D;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;

import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;

/**
 * Created by tischi on 11/04/17.
 */

public class BigDataConverterUI extends JFrame implements ActionListener, FocusListener, ItemListener
{

    public static String LEICA_SINGLE_TIFF = "Leica Light-Sheet Tiff";
    public static String EM_TIFF_SLICES = "EM Tiff Slices";
    public static String LOAD_CHANNELS_FROM_FOLDERS = "Channels from Folders";

    JCheckBox cbVerboseLogging = new JCheckBox("Verbose Logging");
    JCheckBox cbLZW = new JCheckBox("LZW Compression (Tiff)");
    JCheckBox cbSaveVolume = new JCheckBox("Save Volume Data");
    JCheckBox cbSaveProjection = new JCheckBox("Save Projections");
    JCheckBox cbConvertTo8Bit = new JCheckBox("8-bit Conversion   ");
    JCheckBox cbConvertTo16Bit = new JCheckBox("16-bit Conversion");
    JCheckBox cbGating = new JCheckBox("Gate");

    JTextField tfBinning = new JTextField("1,1,1", 10);
    JTextField tfCropZMinMax = new JTextField("1,all", 5);
    JTextField tfCropTMinMax = new JTextField("1,all", 5);
    JTextField tfIOThreads = new JTextField("5", 2);
    JTextField tfRowsPerStrip = new JTextField("10", 3);
    JTextField tfMapTo255 = new JTextField("65535",5);
    JTextField tfMapTo0 = new JTextField("0",5);
    JTextField tfGateMin = new JTextField("0",5);
    JTextField tfGateMax = new JTextField("255",5);
    JTextField tfChromaticShifts = new JTextField("0,0,0; 0,0,0", 20 );

    JComboBox filterPatternComboBox = new JComboBox(new String[] {
            ".*",
            ".*--C.*",
            ".*Left.*",
            ".*Right.*",
            ".*short.*",
            ".*long.*",
            ".*Target.*",
            ".*LSEA00.*",
            ".*LSEA01.*"});

    JComboBox namingSchemeComboBox = new JComboBox(new String[] {
            "None",
            LEICA_SINGLE_TIFF,
            LOAD_CHANNELS_FROM_FOLDERS,
            EM_TIFF_SLICES,
            ".*--C<c>--T<t>.tif",
            ".*--C<c>--T<t>.h5",
            ".*_C<c>_T<t>.tif",
            ".*--T<t>--Z<z>--C<c>.tif",
            "Cam_<c>_<t>.h5",
            "<Z0000-0009>.tif",
            ".*--C<C01-01>--T<T00001-00001>--Z<Z00001-01162>.tif",
            ".*--C<C00-00>--T<T00000-00000>--Z<Z00001-01162>.tif"
    });

    JComboBox hdf5DataSetComboBox = new JComboBox(new String[] {
            "None",
            "Data",
            "Data111",
            "Data222",
            "Data444",
            ImarisUtils.RESOLUTION_LEVEL +"0/Data",
            ImarisUtils.RESOLUTION_LEVEL +"1/Data",
            ImarisUtils.RESOLUTION_LEVEL +"2/Data",
            ImarisUtils.RESOLUTION_LEVEL +"3/Data",
            "ITKImage/0/VoxelData"
            });

    JComboBox comboFileTypeForSaving = new JComboBox( new Utils.FileType[]{
            Utils.FileType.TIFF_STACKS,
            Utils.FileType.TIFF_PLANES,
            Utils.FileType.HDF5,
            Utils.FileType.IMARIS } );

    final String SAVE = "Save";
    JButton save = new JButton(SAVE);

    final String SAVE_PLANES = "Save as Planes";
    JButton savePlanes = new JButton(SAVE_PLANES);

    final String STOP_SAVING = "Interrupt Saving";
    JButton stopSaving =  new JButton(STOP_SAVING);

    final String LOAD = "Open Virtual";
    JButton load =  new JButton( LOAD );

    final String BROWSE_FOLDER = "Image Data Folder";
    JButton browseFolder =  new JButton( BROWSE_FOLDER );

    final String STREAMfromInfoFile = "Stream from info file";
    JButton streamFromInfoFile =  new JButton(STREAMfromInfoFile);

    final String LOAD_FULLY_INTO_RAM = "Load into RAM";
    JButton duplicateToRAM =  new JButton(LOAD_FULLY_INTO_RAM);

    final String CROP = "Crop";
    JButton cropButton =  new JButton( CROP );

    final String APPLY_SHIFTS = "Apply shifts";
    JButton applyShifts =  new JButton( APPLY_SHIFTS );

    final String REPORT_ISSUE = "Report an issue";
    JButton reportIssue =  new JButton(REPORT_ISSUE);

    Logger logger = new IJLazySwingLogger();

    BigDataConverter bdc;

    JFileChooser fc;
    private int panelIdx;
    private int mainPanelIdx;
    private ArrayList< JPanel > mainPanels;
    private ArrayList< JPanel > panels;
    private JTabbedPane tabbedPane;
    private JTextField inputFolderTF;
    private String inputFolder;

    public void showDialog()
    {
        tabbedPane = new JTabbedPane();
        configureToolTips();
        initPanels();
        configureLoadingPanel();
        configureChromaticShiftCorrectionPanel();
        configureCroppingPanel();
        configureAdaptiveCropPanel();
        configureSavingPanel();
        configureMiscellaneousPanel();

        this.setTitle("Big Data Converter");
        this.add( tabbedPane );
        this.setVisible(true);
        this.pack();
    }

    private void configureAdaptiveCropPanel()
    {
        final AdaptiveCropUI adaptiveCropUI = new AdaptiveCropUI();
        final JPanel adaptiveCropUIPanel = adaptiveCropUI.getPanel();
        adaptiveCropUIPanel.setLayout( new BoxLayout( adaptiveCropUIPanel, BoxLayout.PAGE_AXIS ) );

        tabbedPane.add( adaptiveCropUIPanel, "Motion Correction" );
    }

    private void configureMiscellaneousPanel()
    {
        final JPanel miscPanel = new JPanel();
        mainPanels.add( miscPanel );
        setMainPanelLayout( miscPanel );
        addPanel( new JLabel("I/O threads"), tfIOThreads, mainPanelIdx );

        panels.add(new JPanel());
        panels.get( panelIdx ).add( cbVerboseLogging );
        cbVerboseLogging.setSelected(false);
        cbVerboseLogging.addItemListener(this);
        mainPanels.get( mainPanelIdx ).add( panels.get( panelIdx++));

        panels.add(new JPanel());
        reportIssue.setActionCommand(REPORT_ISSUE);
        reportIssue.addActionListener(this);
        panels.get( panelIdx ).add(reportIssue);
        mainPanels.get( mainPanelIdx ).add( panels.get( panelIdx++));

        tabbedPane.add("Misc", mainPanels.get( mainPanelIdx++));
    }

    private void configureChromaticShiftCorrectionPanel()
    {
        final JPanel colorShiftPanel = new JPanel();
        mainPanels.add( colorShiftPanel );
        setMainPanelLayout( colorShiftPanel );

        addPanel( new JLabel("Chromatic shifts in pixels for each channel [x,y,z; x,y,z; ...]:"), tfChromaticShifts, mainPanelIdx );

        panels.add(new JPanel());
        applyShifts.setActionCommand( APPLY_SHIFTS );
        applyShifts.addActionListener(this);
        panels.get( panelIdx ).add( applyShifts );
        mainPanels.get( mainPanelIdx ).add( panels.get( panelIdx++));

        tabbedPane.add("Channel Shift Correction", mainPanels.get( mainPanelIdx++));
    }

    private void configureSavingPanel()
    {
        final JPanel savingPanel = new JPanel();
        mainPanels.add( savingPanel );
        setMainPanelLayout( savingPanel );

        savingPanel.add(new JLabel("Save as:"));
        savingPanel.add(comboFileTypeForSaving);

        savingPanel.add(new JLabel("Binning [pixels]: x1,y1,z1"));
        savingPanel.add(tfBinning);


        savingPanel.add(cbLZW);
        savingPanel.add(new JLabel( " " ));
        cbLZW.setSelected(false);
        savingPanel.add(new JLabel( "LZW rows per strip [#]" ));
        savingPanel.add(tfRowsPerStrip);

        savingPanel.add(cbConvertTo8Bit);
        savingPanel.add(new JLabel( " " ));
        savingPanel.add(new JLabel("255 ="));
        savingPanel.add(tfMapTo255);
        savingPanel.add(new JLabel("0 ="));
        savingPanel.add(tfMapTo0);

        savingPanel.add(new JLabel( " " ));
        savingPanel.add(cbSaveVolume);
        cbSaveVolume.setSelected(true);
        savingPanel.add(new JLabel( " " ));
        savingPanel.add(cbSaveProjection);
        cbSaveProjection.setSelected(false);

        savingPanel.add(new JLabel( " " ));
        savingPanel.add(save);
        save.setActionCommand(SAVE);
        save.addActionListener(this);

        savingPanel.add(new JLabel( " " ));
        savingPanel.add(stopSaving);
        stopSaving.setActionCommand(STOP_SAVING);
        stopSaving.addActionListener(this);


        SpringUtilities.makeCompactGrid(
                savingPanel,
                11, 2, //rows, cols
                6, 6, //initX, initY
                6, 6); //xPad, yPad


        tabbedPane.add("Saving", mainPanels.get(mainPanelIdx++));
    }

    private void addPanel( JLabel jLabel, JTextField tfRowsPerStrip, int mainPanelIdx )
    {
        panels.add(new JPanel());
        panels.get( panelIdx ).add( jLabel );
        panels.get( panelIdx ).add( tfRowsPerStrip );
        mainPanels.get( mainPanelIdx ).add( panels.get( panelIdx++ ));
    }

    private void addComponents( JPanel panel, JComponent jComponent01, JComponent jComponent02 )
    {
        panel.add( jComponent01 );
        panel.add( jComponent02 );
    }

    private void addPanel( JComponent cbSaveVolume, int mainPanelIdx )
    {
        panels.add(new JPanel());
        panels.get(panelIdx).add( cbSaveVolume );
        mainPanels.get(mainPanelIdx).add(panels.get(panelIdx++));
    }

    private void configureCroppingPanel()
    {
        final JPanel croppingPanel = new JPanel();
        setMainPanelLayout( croppingPanel );

        addComponents( croppingPanel, new JLabel("x/y extend: "), new JLabel("from rectangle ROI") );
        addComponents( croppingPanel, new JLabel("z-min, z-max [slices]:"), tfCropZMinMax );
        addComponents( croppingPanel, new JLabel("t-min, t-max [frames]:"), tfCropTMinMax );
        addComponents( croppingPanel, new JLabel(""), cropButton);

        cropButton.setActionCommand( CROP );
        cropButton.addActionListener(this);

        SpringUtilities.makeCompactGrid(
                croppingPanel,
                4, 2, //rows, cols
                6, 6, //initX, initY
                6, 6); //xPad, yPad

        tabbedPane.add("Cropping", croppingPanel );
    }

    private void setMainPanelLayout( JPanel panel )
    {
        panel.setLayout( new SpringLayout() );
    }

    private void configureLoadingPanel()
    {
        final JPanel loadingPanel = new JPanel();
        setMainPanelLayout( loadingPanel );

        loadingPanel.add( browseFolder );
        browseFolder.setActionCommand( BROWSE_FOLDER );
        browseFolder.addActionListener( this );
        inputFolderTF = new JTextField( "" );
        loadingPanel.add( inputFolderTF );
        namingSchemeComboBox.setEditable(true);

        loadingPanel.add( new JLabel("File naming scheme:") );
        loadingPanel.add( namingSchemeComboBox );
        namingSchemeComboBox.setEditable(true);

        loadingPanel.add( new JLabel("Only load files matching:") );
        loadingPanel.add( filterPatternComboBox );
        filterPatternComboBox.setEditable(true);

        loadingPanel.add( new JLabel("Hdf5 data set name:") );
        loadingPanel.add( hdf5DataSetComboBox );
        hdf5DataSetComboBox.setEditable(true);

        loadingPanel.add( new JLabel( "" ) );
        loadingPanel.add( load );
        load.setActionCommand( LOAD );
        load.addActionListener(this);

        SpringUtilities.makeCompactGrid(
                loadingPanel,
                5, 2, //rows, cols
                6, 6, //initX, initY
                6, 6); //xPad, yPad

        tabbedPane.add("Loading", loadingPanel );
    }

    private void initPanels()
    {
        mainPanels = new ArrayList();
        panels = new ArrayList();
        panelIdx = 0;
        mainPanelIdx = 0;
    }

    private void configureToolTips()
    {
        String[] toolTipTexts = getToolTipFile( "BigDataConverterHelp.html" );
        ToolTipManager.sharedInstance().setDismissDelay(10000000);
    }

    public void focusGained( FocusEvent e )
    {
        //
    }

    public void focusLost( FocusEvent e )
    {
        JTextField tf = (JTextField) e.getSource();
        if ( tf != null )
        {
            tf.postActionEvent();
        }
    }

    public void itemStateChanged( ItemEvent e )
    {
        Object source = e.getItemSelectable();
        if (source == cbVerboseLogging )
        {
            if ( e.getStateChange() == ItemEvent.DESELECTED )
            {
                logger.setShowDebug(false);
            }
            else
            {
                logger.setShowDebug(true);
            }
        }
    }


    public boolean checkImageProperties()
    {

        GenericDialog gd = new NonBlockingGenericDialog("Check Image Properties");
        gd.addMessage(
                "Before saving please consider checking the image calibration in [Image > Properties].\n" +
                        " \nFor further analysis it can for instance be important that " +
                        "the pixel width, height and depth are set properly.\n" +
                        " \nYou can leave this dialog open. " +
                        "Simply press [OK] once you checked/corrected the meta-data.\n ");
        gd.showDialog();

        if ( gd.wasCanceled() ) return false;
        return true;
    }

    public void actionPerformed( ActionEvent e )
    {
        int i = 0;

        //
        // Get values from GUI
        //
        final BigDataConverter bigDataConverter = new BigDataConverter();
        final String h5DataSet = (String)hdf5DataSetComboBox.getSelectedItem();
        final int nIOthreads = new Integer(tfIOThreads.getText());
        final int rowsPerStrip = new Integer(tfRowsPerStrip.getText());
        final String filterPattern = (String)filterPatternComboBox.getSelectedItem();
        final String namingScheme = (String) namingSchemeComboBox.getSelectedItem();


        if (e.getActionCommand().equals(BROWSE_FOLDER))
        {
            inputFolder = IJ.getDirectory("Select Directory");

            if ( inputFolder == null ) return;

            inputFolderTF.setText( inputFolder );

        } else if (e.getActionCommand().equals( LOAD ) )  {

            Thread thread = new Thread( () -> bigDataConverter.openFromDirectory(
                    inputFolder,
					namingScheme,
					filterPattern,
					h5DataSet,
					new ImageDataInfo(),
					nIOthreads,
					true,
					false) );
            thread.start();
        }
        else if (e.getActionCommand().equals(STREAMfromInfoFile))
        {
            // Open from file
            //
            String filePath = IJ.getFilePath("Select *.ser file");
            if (filePath == null)
                return;
            File file = new File(filePath);
            ImagePlus imp = bigDataConverter.openFromInfoFile(file.getParent() + "/", file.getName());
            imp.show();
            imp.setPosition(1, imp.getNSlices()/2, 1);
            imp.updateAndDraw();
            imp.resetDisplayRange();
        }
        else if ( e.getActionCommand().equals( SAVE ) )
        {
            ImagePlus imp = IJ.getImage();

            if ( imp.getStack() instanceof VirtualStack2 )
            {
                VirtualStack2 vs2 = ( VirtualStack2 ) imp.getStack();

                if ( vs2.numberOfUnparsedFiles() > 0 )
                {
                    logger.error( "There are still " + vs2.numberOfUnparsedFiles() +
                            " files in the folder that have not been parsed yet.\n" +
                            "Please try again later (check ImageJ's status bar)." );
                    return;
                }
            }

            fc = new JFileChooser( );
            int returnVal = fc.showSaveDialog(BigDataConverterUI.this);
            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                final File file = fc.getSelectedFile();

                Utils.FileType fileType = ( Utils.FileType ) comboFileTypeForSaving.getSelectedItem();

               if ( fileType.equals( Utils.FileType.TIFF_STACKS )
                        || fileType.equals( Utils.FileType.HDF5 )
                        || fileType.equals( Utils.FileType.IMARIS ) )
               {
                   saveAsStacks( fileType, file, imp, rowsPerStrip );
               }
               else if ( fileType.equals( Utils.FileType.TIFF_PLANES ))
               {
                   saveAsTiffPlanes( fileType, file, imp, rowsPerStrip );
               }

            }
        }
        else if (e.getActionCommand().equals(STOP_SAVING))
        {
            if ( bdc != null )
            {
                bdc.cancelSaving();
            }

        }
        else if (e.getActionCommand().equals(APPLY_SHIFTS) )
        {

            ImagePlus imp = IJ.getImage();
            if ( ! Utils.instanceOfVS2( imp ) ) return;
            VirtualStack2 vs2 = (VirtualStack2 ) imp.getStack();

            setChromaticShifts( vs2 );

            updateVS2ImageDisplay( imp );
            //imp.updateAndRepaintWindow();


        }
        else if (e.getActionCommand().equals(LOAD_FULLY_INTO_RAM))
        {

            if( ! Utils.checkMemoryRequirements(IJ.getImage()) ) return;
            if( ! Utils.instanceOfVS2(IJ.getImage())) return;

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    ImagePlus impRAM = bigDataConverter.loadVS2FullyIntoRAM(IJ.getImage(), nIOthreads);
                    if (impRAM != null)
                    {
                        impRAM.show();
                    }

                }
            }); t1.start();

        }
        else if ( e.getActionCommand().equals( CROP ) )
        {
            //
            // Crop As New Stream
            //

            ImagePlus imp = IJ.getImage();
            if ( ! Utils.instanceOfVS2( imp ) ) return;
            VirtualStack2 vs2 = (VirtualStack2 ) imp.getStack();

            //settings.folderElastix + "bin/elastix",
            // Check that all image files have been parsed
            //

            int numberOfUnparsedFiles = vs2.numberOfUnparsedFiles();
            if( numberOfUnparsedFiles > 0 ) {
                logger.error("There are still " + numberOfUnparsedFiles +
                        " files in the folder that have not been parsed yet.\n" +
                        "Please try again later (check ImageJ's status bar).");
                return;
            }

            // get from gui
            //
            int[] zMinMax = getMinMaxFromTextField(imp, tfCropZMinMax, "z");
            int[] tMinMax = getMinMaxFromTextField(imp, tfCropTMinMax, "t");

            // check
            //
            if ( ! Utils.checkRange(imp, zMinMax[0], zMinMax[1], "z") ) return;
            if ( ! Utils.checkRange(imp, tMinMax[0], tMinMax[1], "t") ) return;

            // compute
            //
            ImagePlus imp2 = bigDataConverter.getCroppedVS2(
                    imp, imp.getRoi(),
                    zMinMax[0] - 1, zMinMax[1] - 1,
                    tMinMax[0] - 1, tMinMax[1] - 1);

            // publish
            //
            if (imp2 != null)
            {
                Utils.show(imp2);
            }


        }  else if (e.getActionCommand().equals(REPORT_ISSUE)) {

            //
            // Report issue
            //

            String url = "https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues";
            if (isDesktopSupported()) {
                try {
                    final URI uri = new URI(url);
                    getDesktop().browse(uri);
                } catch (URISyntaxException uriEx) {
                    logger.error(uriEx.toString());
                } catch (IOException ioEx) {
                    logger.error(ioEx.toString());
                }
            } else {
                logger.error("Could not open browser, please report issue here: \n" +
                        "https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues");

            }

        }
    }

    private void saveAsTiffPlanes( Utils.FileType fileType, File file, ImagePlus imp, int rowsPerStrip )
    {
        SavingSettings savingSettings = new SavingSettings();
        savingSettings.imp = imp;
        savingSettings.bin = tfBinning.getText();
        savingSettings.saveVolume = cbSaveVolume.isSelected();
        savingSettings.saveProjection = cbSaveProjection.isSelected();
        savingSettings.convertTo8Bit = cbConvertTo8Bit.isSelected();
        savingSettings.mapTo0 = Integer.parseInt(tfMapTo0.getText());
        savingSettings.mapTo255 = Integer.parseInt(tfMapTo255.getText());
        savingSettings.filePath = file.getAbsolutePath();
        savingSettings.fileType = fileType;
        savingSettings.compression = cbLZW.isSelected() ? "LZW" : "";
        savingSettings.rowsPerStrip = rowsPerStrip;
        savingSettings.nThreads = new Integer(tfIOThreads.getText());

        bdc = new BigDataConverter();
        bdc.saveVS2AsTiffPlanes(savingSettings);
    }

    private void saveAsStacks( Utils.FileType fileType, File file, ImagePlus imp, int rowsPerStrip )
    {
        final int ioThreads = new Integer( tfIOThreads.getText() );

        int safetyMargin = 3;
        if( ! Utils.checkMemoryRequirements( imp, safetyMargin, Math.min(ioThreads, imp.getNFrames())) ) return;

        String compression = "";
        if( cbLZW.isSelected() ) compression="LZW";

        SavingSettings savingSettings = new SavingSettings();
        savingSettings.imp = imp;
        savingSettings.bin = tfBinning.getText();
        savingSettings.saveVolume = cbSaveVolume.isSelected();
        savingSettings.saveProjection = cbSaveProjection.isSelected();
        savingSettings.convertTo8Bit = cbConvertTo8Bit.isSelected();
        savingSettings.convertTo16Bit = cbConvertTo16Bit.isSelected();
        savingSettings.gate = cbGating.isSelected();
        savingSettings.gateMin = Integer.parseInt(tfGateMin.getText());
        savingSettings.gateMax = Integer.parseInt(tfGateMax.getText());
        savingSettings.mapTo0 = Integer.parseInt(tfMapTo0.getText());
        savingSettings.mapTo255 = Integer.parseInt(tfMapTo255.getText());
        savingSettings.directory = file.getParent();
        savingSettings.fileBaseName = file.getName();
        savingSettings.filePath = file.getAbsolutePath();
        savingSettings.fileType = fileType;
        savingSettings.compression = compression;
        savingSettings.rowsPerStrip = rowsPerStrip;
        savingSettings.nThreads = ioThreads;

        new Thread(new Runnable() {
			public void run()
			{
				bdc = new BigDataConverter();
				bdc.saveAsStacks( savingSettings );
			}
		}).start();
    }

    private void updateVS2ImageDisplay( ImagePlus imp )
    {
        VirtualStack2 vs2 = ( VirtualStack2 ) imp.getStack();
        if ( imp instanceof CompositeImage )
        {
            // TODO: update does not work
            // one has to manage to somehow update the processors of all channels
            CompositeImage compositeImage = (CompositeImage) imp;
            compositeImage.updateAllChannelsAndDraw();
        }
        else
        {
            imp.setProcessor( vs2.getProcessor( vs2.getCurrentStackPosition() ) );
            imp.updateAndDraw();
        }
    }

    private void setChromaticShifts( VirtualStack2 vs2 )
    {
        String[] shifts = tfChromaticShifts.getText().split( ";" );

        if ( shifts.length != vs2.getChannels() )
        {
            logger.error( "Parsing of shift text did not yield the same number of channels as the input image has." );
            return;
        }

        for ( int c = 0; c < vs2.getChannels() ; ++c )
        {
            int[] pixelShifts = Utils.delimitedStringToIntegerArray( shifts[ c ], "," );
            vs2.setChromaticShift( c, new Point3D( pixelShifts[ 0 ], pixelShifts[ 1 ], pixelShifts[ 2 ] ) );
        }
    }

    private String[] getToolTipFile( String fileName ) {

        ArrayList<String> toolTipTexts = new ArrayList<String>();

        //Get file from resources folder
        //ClassLoader classLoader = getClass().getClassLoader();
        //File file = new File(classLoader.getResource(fileName).getFile());

        //try {

        InputStream in = getClass().getResourceAsStream("/"+fileName);
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        Scanner scanner = new Scanner(input);

        StringBuilder sb = new StringBuilder("");


        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.equals("###")) {
                toolTipTexts.add(sb.toString());
                sb = new StringBuilder("");
            } else {
                sb.append(line);
            }

        }

        scanner.close();

        //} catch (IOException e) {

        //    logger.info("Did not find tool tip file 2.");
        //    e.printStackTrace();

        //}

        return(toolTipTexts.toArray(new String[0]));
    }

    private int[] getMinMaxFromTextField(ImagePlus imp, JTextField tf, String dimension)
    {
        String[] s = tf.getText().split(",");
        if(s.length != 2) {
            logger.error("Something went wrong parsing the min, max values.\n" +
                    "Please check that there are two comma separated values.");
            return null;
        }

        int min = new Integer(s[0]);
        int max = 0;
        if ( s[1].equals(("all")) )
        {
            if ( dimension.equals("z") )
                max = imp.getNSlices();
            else if ( dimension.equals("t") )
                max = imp.getNFrames();

        }
        else
        {
            max = new Integer(s[1]);
        }

        return new int[]{min,max};
    }

}
