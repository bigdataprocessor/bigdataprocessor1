import de.embl.cba.bigDataTools.dataStreamingTools.DataStreamingTools;
import de.embl.cba.bigDataTools.lazyratioviewer.LazyRatioViewerCommand;
import ij.IJ;
import ij.ImagePlus;

public class LazyRatioViewerCommandTest
{
	public static void main( String... args )
	{

		final net.imagej.ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		String directory = "/Users/tischer/Documents/fiji-plugin-bigDataTools/src/test/resources/matt/";
		directory = "/Volumes/almfspim/Tischi Help/Matt/test";

		DataStreamingTools dataStreamingTools = new DataStreamingTools();

		dataStreamingTools.openFromDirectory(
				directory,
				DataStreamingTools.LEICA_SINGLE_TIFF, // DataStreamingTools.LOAD_CHANNELS_FROM_FOLDERS,
				".*",
				"ResolutionLevel 0/Data",
				null,
				10,
				true,
				false );

		IJ.wait( 5000 );

		ij.command().run( LazyRatioViewerCommand.class, true );




	}

}
