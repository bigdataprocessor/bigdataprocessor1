import de.embl.cba.bigdataprocessor.BigDataProcessor;
import de.embl.cba.bigdataprocessor.BigDataProcessorUserInterface;
import de.embl.cba.imaris.ImarisWriter;
import de.embl.cba.imaris.ImarisWriterCommand;
import ij.ImagePlus;

public class ImarisWriterCommandTest
{

	public static void main( String... args )
	{

		final net.imagej.ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		final BigDataProcessor bigDataProcessor = new BigDataProcessor();

		ImagePlus imp = bigDataProcessor.openFromDirectory(
				ImarisWriter.class.getResource( "/tiff-nc2-nt2/" ).getFile(),
				BigDataProcessorUserInterface.LOAD_CHANNELS_FROM_FOLDERS,
				".*",
			 	"",
			 	null,
			 	10,
			 	false,
				false );
 		imp.show();

// 		imp = IJ.openImage( ImarisWriter.class.getResource( "/mitosis.tif" ).getFile() );
// 		imp.show();

		ij.command().run( ImarisWriterCommand.class, true );
	}


}
