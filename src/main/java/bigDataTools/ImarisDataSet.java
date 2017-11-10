package bigDataTools;

import bigDataTools.logging.IJLazySwingLogger;
import bigDataTools.logging.Logger;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;

import java.io.File;
import java.util.ArrayList;



public class ImarisDataSet {

    private ArrayList < long[] > dimensions;
    private ArrayList < int[] > relativeBinnings;
    private ArrayList < long[] > chunks;
    private ArrayList < String > channelColors;
    private ArrayList < String > channelNames;
    private RealInterval interval;
    private CTRDataSets ctrDataSets;
    private ArrayList < String > timePoints;

    Logger logger = new IJLazySwingLogger();

    public ImarisDataSet( )
    {}

    public ArrayList< String > getChannelNames()
    {
        return channelNames;
    }

    public String getDataSetDirectory( int c, int t, int r)
    {
        return ( ctrDataSets.get( c, t, r ).directory );
    }

    public String getDataSetFilename( int c, int t, int r )
    {
        return ( ctrDataSets.get( c, t, r ).filename );
    }

    public String getDataSetGroupName( int c, int t, int r)
    {
        return ( ctrDataSets.get( c, t, r ).h5Group );
    }

    public ArrayList< int[] > getRelativeBinnings()
    {
        return relativeBinnings;
    }

    public RealInterval getInterval()
    {
        return interval;
    }

    public ArrayList< String > getChannelColors()
    {
        return channelColors;
    }

    public ArrayList< String > getTimePoints()
    {
        return timePoints;
    }

    public ArrayList< long[] > getDimensions()
    {
        return dimensions;
    }

    public ArrayList< long[] > getChunks()
    {
        return chunks;
    }

    public void setLogger( Logger logger )
    {
        this.logger = logger;
    }

    private long[] getImageSize( ImagePlus imp, int[] primaryBinning )
    {

        long[] size = new long[3];

        // bin image to see how large it would be
        if ( primaryBinning[0] > 1 || primaryBinning[1] > 1 || primaryBinning[2] > 1 )
        {

            /*
            logger.info("Determining image size at " +
                    "highest resolution level after initial binning...");

            ImagePlus impBinned = null;
            if ( ! ( imp.getStack() instanceof VirtualStackOfStacks) )
            {
                logger.error( "This currently only works for streamed data." );
                return null;
            }
            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
            impBinned = vss.getFullFrame( 0, 0, 1 );

            Binner binner = new Binner();
            impBinned = binner.shrink( impBinned, primaryBinning[0], primaryBinning[1], primaryBinning[2], binner.AVERAGE );
            size[0] = impBinned.getWidth();
            size[1] = impBinned.getHeight();
            size[2] = impBinned.getNSlices();

            */

            size[0] = imp.getWidth();
            size[1] = imp.getHeight();
            size[2] = imp.getNSlices();

            for ( int d = 0; d < 3; ++d )
            {
                size[d] /= primaryBinning[d];
            }

        }
        else
        {
            size[0] = imp.getWidth();
            size[1] = imp.getHeight();
            size[2] = imp.getNSlices();
        }

        return ( size );

    }

    private void setDimensionsBinningsChunks( ImagePlus imp, int[] primaryBinning )
    {

        dimensions = new ArrayList<>();
        relativeBinnings = new ArrayList<>();
        chunks = new ArrayList<>();

        long[] size = getImageSize( imp, primaryBinning );
        int impByteDepth = imp.getBitDepth() / 8;

        // Resolution level 0
        dimensions.add( size );
        relativeBinnings.add( new int[]{ 1, 1, 1 } );

        long volume = size[0] * size[1] * size[2];

        // TODO: does 1 as z-chunking work for imaris?
        long[] firstChunk = new long[]{ 32, 32, 1 };

        if ( volume > Integer.MAX_VALUE - 100 )
        {
            firstChunk[2] = 1;
            // this forces plane wise writing and thus
            // avoids java indexing issues when loading the
            // whole dataset into RAM in the Hdf5DataCubeWriter
        }

        chunks.add(new long[]{ 32, 32, 1 });

        // Further resolution levels
        long voxelsAtCurrentResolution = 0;
        int iResolution = 0;

        while ( impByteDepth * voxelsAtCurrentResolution > ImarisUtils.MIN_VOXELS )
        {

            long[] lastSize = dimensions.get( iResolution );
            int[] lastBinning = relativeBinnings.get( iResolution );

            long[] newSize = new long[3];
            int[] newBinning = new int[3];

            long lastVolume = lastSize[0] * lastSize[1] * lastSize[2];

            for ( int d = 0; d < 3; d++)
            {
                long lastSizeThisDimensionSquared = lastSize[d] * lastSize[d];
                long lastPerpendicularPlaneSize = lastVolume / lastSize[d];

                if ( 100 * lastSizeThisDimensionSquared > lastPerpendicularPlaneSize )
                {
                    newSize[d] = lastSize[d] / 2;
                    newBinning[d] = 2;
                }
                else
                {
                    newSize[d] = lastSize[d];
                    newBinning[d] = 1;
                }

                newSize[d] = Math.max( 1, newSize[d] );

            }

            long[] newChunk = new long[] {16, 16, 16};
            for ( int i = 0; i < 3; i++ )
            {
                if( newChunk[i] > newSize[i] )
                {
                    newChunk[i] = newSize[i];
                }

            }

            long thisVolume = newSize[0] * newSize[1] * newSize[2];

            if ( thisVolume > Integer.MAX_VALUE - 100 )
            {
                newChunk[2] = 1;
                // this forces plane wise writing and thus
                // avoids java indexing issues when loading the
                // whole dataset into RAM in the Hdf5DataCubeWriter
            }

            dimensions.add( newSize );
            relativeBinnings.add( newBinning );
            chunks.add( newChunk );

            voxelsAtCurrentResolution = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        }

    }

    private void setTimePoints( ImagePlus imp )
    {
        timePoints = new ArrayList<>();

        for ( int t = 0; t < imp.getNFrames(); ++t )
        {
            // TODO: extract real information from imp?
            timePoints.add("2000-01-01 00:00:0" + t);
        }
    }

    private void setChannels( ImagePlus imp )
    {
        channelColors = new ArrayList<>();
        channelNames = new ArrayList<>();

        for ( int c = 0; c < imp.getNChannels(); ++c )
        {
            channelColors.add( ImarisUtils.DEFAULT_COLOR );
            channelNames.add( imp.getTitle() + "_channel_" + c );
        }
    }

    public void setChannelNames( ArrayList< String > channelNames )
    {
        this.channelNames = channelNames;
    }

    private void setInterval( ImagePlus imp )
    {
        double[] min = new double[3];
        double[] max = new double[3];

        Calibration calibration = imp.getCalibration();

        double conversionToMicrometer = 1.0;

        if ( calibration.getUnit().equals( "nm" )
                || calibration.getUnit().equals( "nanometer" )
                || calibration.getUnit().equals( "nanometre" ) )
        {
            conversionToMicrometer = 1.0 / 1000.0;
        }


        max[ 0 ] = imp.getWidth() * calibration.pixelWidth * conversionToMicrometer;
        max[ 1 ] = imp.getHeight() * calibration.pixelHeight * conversionToMicrometer;
        max[ 2 ] = imp.getNSlices() * calibration.pixelDepth * conversionToMicrometer;


        interval = new FinalRealInterval( min, max );
    }

    public void setFromImagePlus( ImagePlus imp,
                                  int[] primaryBinning,
                                  String directory,
                                  String filenameStump,
                                  String h5Group)
    {

        setDimensionsBinningsChunks( imp, primaryBinning );
        setTimePoints( imp );
        setChannels( imp );
        setInterval( imp );

        ctrDataSets = new CTRDataSets();

        for ( int c = 0; c < channelColors.size(); ++c )
        {
            for ( int t = 0; t < timePoints.size(); ++t )
            {
                for ( int r = 0; r < dimensions.size(); ++r )
                {
                    ctrDataSets.addExternal( c, t, r, directory, filenameStump);
                }
            }
        }

    }

    public void setFromImaris( File file )
    {
        setFromImaris( file.getParent(), file.getName() );
    }

    public void setFromImaris( String directory, String filename )
    {
        ImarisReader reader = new ImarisReader( directory, filename );

        channelColors = reader.readChannelColors();
        channelNames = reader.readChannelNames();
        timePoints = reader.readTimePoints();
        dimensions = reader.readDimensions();
        interval = reader.readInterval();

        ctrDataSets = new CTRDataSets();

        for ( int c = 0; c < channelColors.size(); ++c )
        {
            for ( int t = 0; t < timePoints.size(); ++t )
            {
                for ( int r = 0; r < dimensions.size(); ++r )
                {
                    ctrDataSets.addImaris( c, c, t, r, directory, filename );
                }
            }
        }

        reader.closeFile();
    }

    public void addChannelsFromImaris( File file )
    {
        addChannelsFromImaris( file.getParent(), file.getName() );
    }

    public void addChannelsFromImaris( String directory, String filename )
    {
        ImarisReader reader = new ImarisReader( directory, filename );

        int nc = reader.readChannelColors().size();
        int nt = reader.readTimePoints().size();
        int nr = reader.readDimensions().size();

        int currentNumChannelsInMetaFile = channelColors.size();

        for ( int c = 0; c < nc; ++c )
        {
            channelColors.add( reader.readChannelColors().get( c ) );
            channelNames.add( reader.readChannelNames().get( c ) );

            for ( int t = 0; t < nt; ++t )
            {
                for ( int r = 0; r < nr; ++r )
                {
                    ctrDataSets.addImaris( c + currentNumChannelsInMetaFile, c, t, r, directory, filename);
                }
            }
        }

    }

}
