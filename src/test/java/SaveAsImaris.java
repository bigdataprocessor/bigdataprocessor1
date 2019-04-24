import de.embl.cba.bigdataprocessor.hdf5.H5DataCubeWriter;
import de.embl.cba.imaris.ImarisDataSet;
import de.embl.cba.imaris.ImarisWriter;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;

public class SaveAsImaris
{
    public static void main(String[] args)
    {

        ImagePlus imp = IJ.openImage(SaveAsImaris.class.getResource( "1024pixels-5D-cube.zip" ).getFile() );

        String outputDirectory = SaveAsImaris.class.getResource(  "/imaris-export" ).getFile() ;

        String name = "image";

        ImarisDataSet imarisDataSet = new ImarisDataSet( imp, new int[]{1,1,1}, outputDirectory, name );

        ImarisWriter.writeHeaderFile( imarisDataSet, outputDirectory, name + "-header" + ".ims" );

        H5DataCubeWriter writer = new H5DataCubeWriter();

        for ( int t = 0; t < imp.getNFrames(); ++t )
        {
            for ( int c = 0; c < imp.getNChannels(); ++c )
            {
                Duplicator duplicator = new Duplicator();
                ImagePlus impCT = duplicator.run( imp, c + 1, c + 1, 1, imp.getNSlices(), t + 1, t + 1 );
                System.out.println( "Writing " + name + ", frame: " + ( t + 1 ) + ", channel: " + ( c + 1 ) + "..." );
                writer.writeImarisCompatibleResolutionPyramid( impCT, imarisDataSet, c, t );
            }
        }

        System.out.println( "..done!" );

    }

}
