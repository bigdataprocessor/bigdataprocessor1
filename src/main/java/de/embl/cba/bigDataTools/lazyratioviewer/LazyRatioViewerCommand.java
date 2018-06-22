package de.embl.cba.bigDataTools.lazyratioviewer;


import ij.ImagePlus;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin( type = Command.class, menuPath = "Plugins>BigDataTools>Lazy Ratio Viewer" )
public class LazyRatioViewerCommand implements Command
{
	@Parameter
	public LogService logService;

	@Parameter
	public ImagePlus imagePlus;

	LazyRatioViewerSettings settings = new LazyRatioViewerSettings();

	@Parameter
	public int channel0 = settings.channel0;

	@Parameter
	public int channel1 = settings.channel1;

	@Parameter
	public double background0 = settings.background0;

	@Parameter
	public double background1 = settings.background1;

	@Parameter
	public double nanThreshold0 = settings.nanThreshold0;

	@Parameter
	public double nanThreshold1 = settings.nanThreshold1;


	public void run()
	{
		settings.imagePlus = imagePlus;
		settings.channel0 = channel0;
		settings.channel1 = channel1;
		settings.background0 = background0;
		settings.background1 = background1;
		settings.nanThreshold0 = nanThreshold0;
		settings.nanThreshold1 = nanThreshold1;

		LazyRatioViewer lazyRatioViewer = new LazyRatioViewer( settings );

		lazyRatioViewer.showRatioImageUsingImageJ1();

	}

}
