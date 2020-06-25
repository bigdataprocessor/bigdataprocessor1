package de.embl.cba.bigdataprocessor.save;

import de.embl.cba.bigdataprocessor.utils.Utils;
import ij.ImagePlus;

/**
 * Created by tischi on 22/05/17.
 */
public class SavingSettings {

    public ImagePlus imp;
    public String bin;
    public boolean saveVolumes;
    public boolean saveProjections;
    public boolean convertTo8Bit;
    public int mapTo0, mapTo255;
    public boolean convertTo16Bit;
    public boolean gate;
    public int gateMin, gateMax;
    public String filePath;
    public String fileBaseName;
    public String directory;
    public Utils.FileType fileType;
    public String compression;
    public int rowsPerStrip;
    public int nThreads;

}
