import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.imaris.ImarisWriter;
import de.embl.cba.bigDataTools.imaris.ImarisWriterCommand;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.patcher.LegacyInjector;

public class ImarisWriterCommandTest
{

	public static void main( String... args )
	{

		final net.imagej.ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		final DataStreamingTools dataStreamingTools = new DataStreamingTools();

		ImagePlus imp = dataStreamingTools.openFromDirectory(
				ImarisWriter.class.getResource( "/tiff-nc2-nt2/" ).getFile(),
				DataStreamingTools.LOAD_CHANNELS_FROM_FOLDERS,
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
