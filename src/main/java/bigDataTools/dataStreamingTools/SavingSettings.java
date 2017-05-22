package bigDataTools.dataStreamingTools;

import bigDataTools.utils.Utils;
import ij.ImagePlus;

/**
 * Created by tischi on 22/05/17.
 */
public class SavingSettings {

    public ImagePlus imp;
    public String bin;
    public boolean saveVolume;
    public boolean saveProjection;
    public boolean convertTo8Bit;
    public int mapTo0, mapTo255;
    public String filePath;
    public Utils.FileType fileType;
    public String compression;
    public int rowsPerStrip;
    public int nThreads;

}
