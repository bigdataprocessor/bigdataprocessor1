package de.embl.cba.bigDataTools.imaris;

import de.embl.cba.bigDataTools.lazyratioviewer.LazyRatioViewer;
import de.embl.cba.bigDataTools.lazyratioviewer.LazyRatioViewerSettings;
import de.embl.cba.bigDataTools.utils.Utils;
import ij.IJ;
import ij.ImagePlus;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

@Plugin( type = Command.class, menuPath = "Plugins>BigDataTools>Imaris Writer" )
public class ImarisWriterCommand implements Command
{
	@Parameter
	public LogService logService;

	@Parameter( visibility = ItemVisibility.MESSAGE, persist = false )
	private String information1 = "Creates an Imaris readable file with channels and time-points as separate files.";

	@Parameter
	public ImagePlus imagePlus;

	@Parameter( label = "Directory", style = "directory" )
	public String directory;

	@Parameter( label = "Binning [ x, y, z ]")
	public String binningString = "1,1,1";

	@Override
	public void run()
	{
		if ( ! isInputValid() ) return;

		ImarisWriter writer = new ImarisWriter( imagePlus, directory );

		writer.setLogService( logService );

		setPreBinning( writer );

		writer.write();
	}

	private void setPreBinning( ImarisWriter writer )
	{
		final int[] binning = Utils.delimitedStringToIntegerArray( binningString, "," );
		writer.setPreBinning( binning );
	}

	private boolean isInputValid()
	{
		return true;
	}


}