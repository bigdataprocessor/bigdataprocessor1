import de.embl.cba.bigDataTools.imaris.ImarisWriter;
import de.embl.cba.bigDataTools.imaris.ImarisWriterCommand;
import de.embl.cba.bigDataTools.lazyratioviewer.LazyRatioViewerCommand;
import ij.IJ;
import ij.ImagePlus;

public class ImarisWriterCommandTest
{

	public static void main( String... args )
	{
		final net.imagej.ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		ImagePlus imp = IJ.openImage( ImarisWriterCommandTest.class.getResource( "/1024pixels-5D-cube.zip" ).getFile() );
		imp.show();

		ij.command().run( ImarisWriterCommand.class, true );
	}
}
