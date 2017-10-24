package bigDataTools;

import bigDataTools.VirtualStackOfStacks.VirtualStackOfStacks;
import ij.ImagePlus;
import ij.plugin.Binner;
import net.imglib2.RealInterval;

import java.util.ArrayList;

public class ImarisDataSetProperties {

    ArrayList < String > timePoints = new ArrayList<>();
    ArrayList < long[] > dimensions = new ArrayList<>();
    ArrayList < int[] > relativeBinnings = new ArrayList<>();
    ArrayList < long[] > chunks = new ArrayList<>();

    ArrayList < String > channels = new ArrayList<>();
    RealInterval interval = null;
    ArrayList < ArrayList < String[] > > timeChannelData = new ArrayList<>();

    public void initialisePropertiesFromImagePlus ( ImagePlus imp,
                                                    int[] primaryBinning )
    {
        long minVoxelVolume = 1024 * 1024;

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




        int impByteDepth = imp.getBitDepth() / 8;

        sizes.add( size );

        binnings.add( new int[]{ 1, 1, 1 } );

        chunks.add(new long[]{32, 32, 4});

        long voxelVolume = 0;
        int iResolution = 0;

        do
        {
            long[] lastSize = sizes.get( iResolution );
            int[] lastBinning = binnings.get( iResolution );

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
                    newBinning[d] = lastBinning[d] * 2;
                }
                else
                {
                    newSize[d] = lastSize[d];
                    newBinning[d] = lastBinning[d];
                }

                newSize[d] = Math.max( 1, newSize[d] );

            }

            sizes.add( newSize );
            binnings.add( newBinning );

            long[] newChunk = new long[] {16, 16, 16};
            for ( int i = 0; i < 3; i++ )
            {
                if( newChunk[i] > newSize[i] )
                {
                    newChunk[i] = newSize[i];
                }

            }
            chunks.add( newChunk );

            voxelVolume = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        } while ( impByteDepth * voxelVolume > minVoxelVolume );

    }



    public void initialisePropertiesFromImarisFile ( String directory, String filename )
    {

    }

    public void addChannelFromImarisFile ( String directory, String filename )
    {

    }


}
