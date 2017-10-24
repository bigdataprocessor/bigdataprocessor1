package bigDataTools;

import bigDataTools.utils.Utils;
import ch.systemsx.cisd.hdf5.hdf5lib.H5F;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageStatistics;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

public class Hdf5DataCubeWriter {
    
    int file_id;
    int image_memory_type;
    int image_file_type;

    final String RESOLUTION = "Resolution";
    final String DATA_CUBE = "Data";
    final String HISTOGRAM = "Histogram";


    private void setImageMemoryAndFileType( ImagePlus imp )
    {

        if ( imp.getBitDepth() == 8)
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_UCHAR ;
            image_file_type = HDF5Constants.H5T_STD_U8BE;
        }
        else if ( imp.getBitDepth() == 16)
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_USHORT;
            // image_file_type = HDF5Constants.H5T_STD_U16BE;
            image_file_type = HDF5Constants.H5T_STD_U16LE;

        }
        else if ( imp.getBitDepth() == 32)
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_FLOAT;
            image_file_type = HDF5Constants.H5T_IEEE_F32BE;
        }
        else
        {
            IJ.showMessage("Image data type is not supported, " +
                    "only 8-bit, 16-bit and 32-bit floating point are possible.");
        }
    }


    public void writeImarisCompatibleResolutionPyramid(
            ImagePlus imp,
            ImarisDataSetProperties idp,
            int c, int t)
    {


        file_id = createFile( idp.getDataSetDirectory( c, t ),
                idp.getDataSetFilename( c, t ));

        setImageMemoryAndFileType( imp );

        ImagePlus impResolutionLevel = imp;

        for ( int r = 0; r < idp.getDimensions().size(); r++ )
        {
            if ( r > 0 )
            {
                // bin further down
                impResolutionLevel = Utils.bin( impResolutionLevel,
                        idp.getRelativeBinnings().get( r )
                        , "binned", "AVERAGE" );
            }

            writeDataCube( impResolutionLevel, RESOLUTION + r,
                    idp.getDimensions().get( r ), idp.getChunks().get( r ) );

            writeHistogram( impResolutionLevel, RESOLUTION + r );
        }

        H5F.H5Fclose( file_id );
    }


    private void writeDataCube( ImagePlus imp, String group, long[] dimension, long[] chunk )
    {
        int group_id = Hdf5Utils.getGroup( file_id, group );

        int space_id = H5.H5Screate_simple( dimension.length, dimension, null );

        // create "dataset creation property list" (dcpl)
        int dcpl_id = H5.H5Pcreate( HDF5Constants.H5P_DATASET_CREATE );

        H5.H5Pset_chunk(dcpl_id, chunk.length, chunk);

        // create dataset
        int dataset_id = -1;
        try
        {
            dataset_id = H5.H5Dcreate(group_id,
                    DATA_CUBE,
                    image_file_type,
                    space_id,
                    HDF5Constants.H5P_DEFAULT,
                    dcpl_id,
                    HDF5Constants.H5P_DEFAULT);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        // write data
        //
        if ( imp.getBitDepth() == 8 )
        {
            byte[] data = getByteData( imp, 0,0 );

            H5.H5Dwrite(dataset_id,
                    image_memory_type,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5P_DEFAULT,
                    data );

        }
        else if ( imp.getBitDepth() == 16 )
        {

            short[] data = getShortData( imp,0,0 );

            H5.H5Dwrite( dataset_id,
                    image_memory_type,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5P_DEFAULT,
                    data );

        }
        else if ( imp.getBitDepth() == 32 )
        {
            float[] data = getFloatData( imp,0,0 );

            H5.H5Dwrite( dataset_id,
                    image_memory_type,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5P_DEFAULT,
                    data );
        }
        else
        {
            IJ.showMessage("Image data type is not supported, " +
                    "only 8-bit, 16-bit and 32-bit are possible.");
        }


        H5.H5Sclose( space_id );
        H5.H5Dclose( dataset_id );
        H5.H5Pclose( dcpl_id );

    }

    private void writeHistogram( ImagePlus imp, String group )
    {
        int group_id = Hdf5Utils.getGroup( file_id, group );

        ImageStatistics imageStatistics = imp.getStatistics();

        /*
        imaris expects 64bit unsigned int values:
        - http://open.bitplane.com/Default.aspx?tabid=268
        thus, we are using as memory type: H5T_NATIVE_ULLONG
        and as the corresponding dataset type: H5T_STD_U64LE
        - https://support.hdfgroup.org/HDF5/release/dttable.html
        */
        long[] histogram = new long[ imageStatistics.histogram.length ];
        for ( int i = 0; i < imageStatistics.histogram.length; ++i )
        {
            histogram[i] = imageStatistics.histogram[i];
        }

        long[] histo_dims = { histogram.length };

        int histo_dataspace_id = H5.H5Screate_simple(
                histo_dims.length, histo_dims, null);

        int histo_dataset_id = H5.H5Dcreate( group_id, HISTOGRAM,
                HDF5Constants.H5T_STD_U64LE, histo_dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite(histo_dataset_id,
                HDF5Constants.H5T_NATIVE_ULLONG,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                HDF5Constants.H5P_DEFAULT, histogram);


        H5.H5Dclose( histo_dataset_id );
        H5.H5Sclose( histo_dataspace_id );
        H5.H5Gclose( group_id );

    }

    private int createFile( String directory, String filename )
    {
        return ( Hdf5Utils.createFile( directory, filename  ) );
    }

    private byte[] getByteData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        byte[] data = new byte[ size[0] * size[1] * size[2] ];

        int pos = 0;

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);

            System.arraycopy( stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1] );

            pos += size[0] * size[1];
        }

        return ( data );

    }

    private short[] getShortData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        short[] data = new short[ size[0] * size[1] * size[2] ];

        int pos = 0;

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);

            System.arraycopy( stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1] );

            pos += size[0] * size[1];
        }

        return ( data );

    }

    private float[] getFloatData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        float[] data = new float[ size[0] * size[1] * size[2] ];

        int pos = 0;

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);

            System.arraycopy( stack.getProcessor(n).getPixels(), 0, data,
                    pos, size[0] * size[1] );

            pos += size[0] * size[1];
        }

        return ( data );

    }


}
