package de.embl.cba.bigDataTools.lazyratioviewer;


import ij.IJ;
import ij.ImagePlus;
import org.scijava.ItemVisibility;
import org.scijava.command.InteractiveCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

@Plugin( type = InteractiveCommand.class, menuPath = "Plugins>BigDataTools>Lazy Ratio Viewer" )
public class LazyRatioViewerCommand extends InteractiveCommand
{
	@Parameter
	public LogService logService;


	@Parameter( visibility = ItemVisibility.MESSAGE, persist = false )
	private String information1 = "Ratio = ( ChannelA - BackgroundA ) / ( ChannelB - BackgroundB )";

	@Parameter( visibility = ItemVisibility.MESSAGE, persist = false )
	private String information2 = "Pixels below threshold in either channel are set to zero.";

	@Parameter
	public ImagePlus imagePlus;

	LazyRatioViewerSettings settings = new LazyRatioViewerSettings();

	@Parameter
	public int channelA = settings.channelA;

	@Parameter
	public int channelB = settings.channelB;

	@Parameter
	public double backgroundA = settings.backgroundA;

	@Parameter
	public double backgroundB = settings.backgroundB;

	@Parameter
	public double thresholdA = settings.thresholdA;

	@Parameter
	public double thresholdB = settings.thresholdB;

	@Parameter( label = "Create ratio view", callback = "createView" )
	private Button viewButton;

	public void createView()
	{
		if ( ! isInputValid() ) return;

		settings.imagePlus = imagePlus;
		settings.channelA = channelA - 1;
		settings.channelB = channelB - 1;
		settings.backgroundA = backgroundA;
		settings.backgroundB = backgroundB;
		settings.thresholdA = thresholdA;
		settings.thresholdB = thresholdB;

		LazyRatioViewer lazyRatioViewer = new LazyRatioViewer( settings );

		lazyRatioViewer.showRatioImageUsingImageJ1();
	}

	private boolean isInputValid()
	{
		if ( imagePlus.getNChannels() < Math.max( channelA, channelB ) )
		{
			IJ.showMessage( "The input image only has " + imagePlus.getNChannels() + " channels.\n" +
					"You however asked for channel number " + Math.max( channelA, channelB ) + "." );
			return false;
		}

		return true;
	}

}
