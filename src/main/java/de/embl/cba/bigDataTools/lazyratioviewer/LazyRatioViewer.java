package de.embl.cba.bigDataTools.lazyratioviewer;

import de.embl.cba.bigDataTools.Viewers;
import ij.ImagePlus;
import ij.VirtualStack;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.VirtualStackAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class LazyRatioViewer
{
	public static int imagePlusChannelDimension = 2;

	final LazyRatioViewerSettings settings;
	final double background0;
	final double background1;
	final double nanThreshold0;
	final double nanThreshold1;


	public LazyRatioViewer( LazyRatioViewerSettings settings )
	{
		this.settings = settings;

		// putting them into local final variables in the hope that this makes the computations faster
		background0 = settings.backgroundA;
		background1 = settings.backgroundB;
		nanThreshold0 = settings.thresholdA;
		nanThreshold1 = settings.thresholdB;

	}

	public  < T extends RealType< T > > void showRatioImageUsingImageJ1()
	{
		final RandomAccessibleInterval< T > wrap = wrap( settings.imagePlus );

		RandomAccessibleInterval< T > channel0 = Views.hyperSlice( wrap, imagePlusChannelDimension, settings.channelA );
		RandomAccessibleInterval< T > channel1 = Views.hyperSlice( wrap, imagePlusChannelDimension, settings.channelB );

		final RandomAccessibleInterval ratioView = createRatioView( channel0, channel1 );

		ImageJFunctions.show( ratioView ).setTitle( "ratio" );

	}

	private < T extends RealType< T > > RandomAccessibleInterval createRatioView( RandomAccessibleInterval< T > channel0, RandomAccessibleInterval< T > channel1 )
	{
		RandomAccessibleInterval< Pair< T, T > > paired = Views.interval( Views.pair( channel0, channel1 ), channel0 );

		RandomAccessibleInterval< DoubleType > ratioView = Converters.convert( paired,
				( pair, out)  -> out.set( getRatio( pair ) ),
				new DoubleType());

		return Viewers.insertDimension( ratioView, imagePlusChannelDimension );
	}

	public < T extends RealType< T > >  double getRatio( Pair< T, T > pair )
	{
		final double v0 = pair.getA().getRealDouble();
		final double v1 = pair.getB().getRealDouble();

		if ( v0 < nanThreshold0 || v1 < nanThreshold1 )
		{
			return 0.0D;
		}
		else
		{
			return ( v0 - background0 ) / ( v1 - background1 );
		}

	}

	private static < T extends RealType< T > > RandomAccessibleInterval< T > wrap( ImagePlus imagePlus )
	{
		final RandomAccessibleInterval< T > wrap;

		if ( imagePlus.getStack() instanceof VirtualStack )
		{
			wrap = ( RandomAccessibleInterval< T > ) ( VirtualStackAdapter.wrap( imagePlus ) );
		}
		else
		{
			wrap = ImageJFunctions.wrapReal( imagePlus );
		}

		return wrap;
	}
}
