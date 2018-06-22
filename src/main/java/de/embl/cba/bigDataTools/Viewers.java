package de.embl.cba.bigDataTools;

import ij.ImagePlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.view.Views;

import java.util.ArrayList;

public class Viewers
{


	public static ArrayList< AxisType > ImageJAxes = new ArrayList< AxisType > ()
	{
		{
			add( Axes.X );
			add( Axes.Y );
			add( Axes.CHANNEL );
			add( Axes.Z );
			add( Axes.TIME );
		}
	};


	public static void showRaiUsingImageJFunctions( RandomAccessibleInterval rai,
													ArrayList< AxisType > axisTypes,
													String title )
	{

		rai = addDimensionsToConformWithImageJ1AxesOrder( rai, axisTypes );
		ImagePlus imp = ImageJFunctions.show( rai );
		imp.setTitle( title );

	}

	private static RandomAccessibleInterval addDimensionsToConformWithImageJ1AxesOrder( RandomAccessibleInterval rai, ArrayList< AxisType > axisTypes )
	{
		RandomAccessibleInterval raiConformingImageJ = rai;

		for ( int i = 0; i < ImageJAxes.size(); ++i )
		{
			if ( ! axisTypes.contains( ImageJAxes.get( i ) ) )
			{
				raiConformingImageJ = insertDimension( raiConformingImageJ, i );
			}
		}

		return raiConformingImageJ;
	}

	public static RandomAccessibleInterval insertDimension( RandomAccessibleInterval rai, int insertDim )
	{
		RandomAccessibleInterval raiWithInsertedDimension = Views.addDimension( rai, 0,0  );

		for ( int d = raiWithInsertedDimension.numDimensions() - 1; d > insertDim; --d )
		{
			raiWithInsertedDimension = Views.permute( raiWithInsertedDimension, d, d - 1  );
		}

		return raiWithInsertedDimension;
	}

}
