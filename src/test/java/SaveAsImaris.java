import de.embl.cba.bigDataTools.Hdf5DataCubeWriter;
import de.embl.cba.bigDataTools.imaris.ImarisDataSet;
import de.embl.cba.bigDataTools.imaris.ImarisWriter;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;

import java.util.ArrayList;

public class SaveAsImaris
{
    public static void main(String[] args)
    {

        ImagePlus imp = IJ.openImage("/Users/tischer/Documents/fiji-plugin-bigDataTools/src/test/resources/1024pixels-5D-cube.zip" );

        String outputDirectory = "/Users/tischer/Documents/fiji-plugin-bigDataTools/src/test/resources/imaris-export";
        String fileNameStump = "image";
        int[] preBinning = new int[]{1,1,1};
        ArrayList< String > channelNames = new ArrayList<>(  );
        channelNames.add( "channel01" );

        ImarisDataSet imarisDataSet = new ImarisDataSet();

        imarisDataSet.setFromImagePlus(
                imp,
                preBinning,
                outputDirectory,
                fileNameStump,
                "/");

        imarisDataSet.setChannelNames( channelNames  );

        ImarisWriter.writeHeader( imarisDataSet, outputDirectory, fileNameStump + "-header" + ".ims" );

        Hdf5DataCubeWriter writer = new Hdf5DataCubeWriter();

        for ( int t = 0; t < imp.getNFrames(); ++t )
        {
            for ( int c = 0; c < imp.getNChannels(); ++c )
            {
                Duplicator duplicator = new Duplicator();
                ImagePlus impCT = duplicator.run( imp, c + 1, c + 1, 1, imp.getNSlices(), t + 1, t + 1 );
                System.out.println( "Writing " + fileNameStump + ", frame: " + ( t + 1 ) + ", channel: " + ( c + 1 ) + "..." );
                writer.writeImarisCompatibleResolutionPyramid( impCT, imarisDataSet, c, t );
            }
        }

        System.out.println( "..done!" );

    }

}
