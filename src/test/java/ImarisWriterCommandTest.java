import de.embl.cba.bigdataconverter.BigDataConverter;
import de.embl.cba.bigdataconverter.BigDataConverterUI;
import de.embl.cba.bigdataconverter.imaris.ImarisWriter;
import de.embl.cba.bigdataconverter.imaris.ImarisWriterCommand;
import ij.ImagePlus;

public class ImarisWriterCommandTest
{

	public static void main( String... args )
	{

		final net.imagej.ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		final BigDataConverter bigDataConverter = new BigDataConverter();

		ImagePlus imp = bigDataConverter.openFromDirectory(
				ImarisWriter.class.getResource( "/tiff-nc2-nt2/" ).getFile(),
				BigDataConverterUI.LOAD_CHANNELS_FROM_FOLDERS,
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
