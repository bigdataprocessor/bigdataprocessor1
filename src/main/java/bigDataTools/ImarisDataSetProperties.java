package bigDataTools;

import bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import bigDataTools.logging.Logger;
import bigDataTools.utils.Utils;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.Binner;
import net.imglib2.RealInterval;

import java.util.ArrayList;



public class ImarisDataSetProperties {

    private ArrayList < long[] > dimensions;
    private ArrayList < int[] > relativeBinnings;
    private ArrayList < long[] > chunks;
    private ArrayList < String > channels = new ArrayList<>();
    private RealInterval interval = null;
    private ArrayList < ArrayList < String[] > > timeChannelDataSets = new ArrayList<>();

    final static long MIN_VOXELS = 1024 * 1024;

    Logger logger;

    public ImarisDataSetProperties()
    {

    }

    public String getDataSetDirectory( int c, int t)
    {
        return ( timeChannelDataSets.get( t ).get( c )[DIRECTORY] );
    }

    public String getDataSetFilename( int c, int t)
    {
        return ( timeChannelDataSets.get( t ).get( c )[FILENAME] );
    }

    public String getDataSetGroupName( int c, int t)
    {
        return ( timeChannelDataSets.get( t ).get( c )[GROUP] );
    }



    public ArrayList< int[] > getRelativeBinnings()
    {
        return relativeBinnings;
    }

    public RealInterval getInterval()
    {
        return interval;
    }



    public ArrayList< String > getChannels()
    {
        return channels;
    }

    public ArrayList< String > getTimePoints()
    {
        return timePoints;
    }

    ArrayList < String > timePoints;

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
            logger.info("Determining image size at " +
                    "highest resolution level after initial binning...");

            ImagePlus impBinned = null;
            // TODO: implement for non-vss
            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
            impBinned = vss.getFullFrame( 0, 0, 1 );

            Binner binner = new Binner();
            impBinned = binner.shrink( impBinned, primaryBinning[0], primaryBinning[1], primaryBinning[2], binner.AVERAGE );
            size[0] = impBinned.getWidth();
            size[1] = impBinned.getHeight();
            size[2] = impBinned.getNSlices();

            logger.info("nx: " + size[0]);
            logger.info("ny: " + size[1]);
            logger.info("nz: " + size[2]);

        }
        else
        {
            size[0] = imp.getWidth();
            size[1] = imp.getHeight();
            size[2] = imp.getNSlices();
        }

        return ( size );

    }

    private void initialiseResolutionLevels ( ImagePlus imp, int[] primaryBinning )
    {

        dimensions = new ArrayList<>();
        relativeBinnings = new ArrayList<>();
        chunks = new ArrayList<>();

        long[] size = getImageSize( imp, primaryBinning );
        int impByteDepth = imp.getBitDepth() / 8;

        // Resolution level 0
        dimensions.add( size );
        relativeBinnings.add( new int[]{ 1, 1, 1 } );
        chunks.add(new long[]{32, 32, 4});

        // Further resolution levels
        long voxelsAtCurrentResolution = 0;
        int iResolution = 0;

        while ( impByteDepth * voxelsAtCurrentResolution > MIN_VOXELS )
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

            dimensions.add( newSize );
            relativeBinnings.add( newBinning );
            chunks.add( newChunk );

            voxelsAtCurrentResolution = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        }

    }

    private void initialiseTimePoints( ImagePlus imp )
    {
        timePoints = new ArrayList<>();

        for ( int t = 0; t < imp.getNFrames(); ++t )
        {
            // TODO: extract real information from imp?
            timePoints.add("2000-01-01 00:00:0" + t);
        }
    }

    private void initialiseChannels( ImagePlus imp )
    {
        channels = new ArrayList<>();

        for ( int c = 0; c < imp.getNChannels(); ++c )
        {
            // TODO: extract real information from imp?
            channels.add("1 1 1");
        }
    }

    public void initialiseFromImagePlus( ImagePlus imp,
                                         int[] primaryBinning,
                                         String directory,
                                         String filename,
                                         String h5Group)
    {

        initialiseResolutionLevels( imp, primaryBinning );
        initialiseTimePoints( imp );
        initialiseChannels( imp );

        timeChannelDataSets = new ArrayList<>();
        for ( int t = 0; t < timePoints.size(); ++t )
        {
            addExternalHdf5DataSet( directory, filename, h5Group, t );
        }


    }

    final int DIRECTORY = 0;
    final int FILENAME = 1;
    final int GROUP = 2;

    public void addExternalHdf5DataSet( String directory,
                                        String filename,
                                        String h5Group,
                                        int t)
    {
        ArrayList< String[] > channelList = new ArrayList<>();

        for ( int c = 0; c < channels.size(); ++c )
        {
            String[] dirFileGroup = new String[ 3 ];
            dirFileGroup[ DIRECTORY ] = directory;
            dirFileGroup[ FILENAME ] = filename + Utils.getChannelTimeString( t, c ) + ".h5";
            dirFileGroup[ GROUP ] = h5Group;
            channelList.add( dirFileGroup );
        }

        timeChannelDataSets.add( channelList );
    }

    public void initialiseFromImarisFile( String directory, String filename )
    {

    }

    public void addChannelFromImarisFile ( String directory, String filename )
    {

    }


}
