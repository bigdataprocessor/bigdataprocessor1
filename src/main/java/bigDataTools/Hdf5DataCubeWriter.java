package bigDataTools;

import bigDataTools.utils.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import java.io.File;
import java.util.ArrayList;

public class Hdf5DataCubeWriter {
    
    int file_id;
    int image_memory_type;
    int image_file_type;

    final String RESOLUTION = "Resolution";
    final String DATA_CUBE = "Data";


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


    private void writeResolutionPyramid( ImagePlus imp,
                                         ArrayList < long[] > dimensions,
                                         ArrayList < int[] > relativeBinnings,
                                         ArrayList < long[] > chunks
                                         )
    {
        setImageMemoryAndFileType( imp );

        ImagePlus impResolutionLevel = imp;

        for ( int r = 0; r < dimensions.size(); r++ )
        {
            if ( r > 0 )
            {
                impResolutionLevel = Utils.bin( impResolutionLevel,
                        relativeBinnings.get( r )
                        , "binned", "AVERAGE" );
            }
            writeDataCube( impResolutionLevel, RESOLUTION + r,
                    dimensions.get( r ), chunks.get( r ) );
        }
    }


    private void writeDataCube( ImagePlus imp, String groupName, long[] dimension, long[] chunk )
    {
        //
        // Create resolution group
        //
        int group_id = Hdf5Utils.getGroup( file_id,
                groupName );

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

    private void writeHistogram( ImagePlus imp )
    {

    }

    private int createFile( String directory, String filename )
    {
        return ( Hdf5Utils.createFile( directory, filename  ) );
    }

    public void writeImagePlusDataCubeAndHistogram( String directory, String filename, 
                                                  ImagePlus imp )
    {
        file_id = createFile( directory, filename );

        writeResolutionPyramid( imp );
        writeHistogram( imp );

        H5.H5Fclose(file_id);

        
        ArrayList<long[]> sizes = imarisH5Settings.sizes;
        ArrayList<int[]> binnings = imarisH5Settings.binnings;
        ArrayList<long[]> chunks = imarisH5Settings.chunks;


        int image_memory_type = 0;
        int image_file_type = 0;

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

        //
        // Prepare file
        //
        String fileName = baseFileName + getChannelTimeString( c , t ) + ".h5";
        String filePath = directory + File.separator + fileName;
        File file = new File( filePath );
        if (file.exists()) file.delete();

        int file_id = H5.H5Fcreate(filePath, HDF5Constants.H5F_ACC_TRUNC,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        ImagePlus impBinned = null;

        for ( int r = 0; r < sizes.size(); r++ )
        {

            //
            // Create resolution group
            //
            int resolution_group_id = H5.H5Gcreate(file_id,
                    "Resolution " + r,
                    HDF5Constants.H5P_DEFAULT,
                    HDF5Constants.H5P_DEFAULT,
                    HDF5Constants.H5P_DEFAULT);

            //
            // Create data
            //

            // create data space
            long[] dims = new long[] {
                    sizes.get(r)[2],
                    sizes.get(r)[1],
                    sizes.get(r)[0]
            };

            int space_id = H5.H5Screate_simple( dims.length, dims, null );

            // create "dataset creation property list" (dcpl)
            int dcpl_id = H5.H5Pcreate( HDF5Constants.H5P_DATASET_CREATE );

            // add chunking to dcpl
            long[] chunk = new long[] {
                    chunks.get(r)[2],
                    chunks.get(r)[1],
                    chunks.get(r)[0],
            };
            H5.H5Pset_chunk(dcpl_id, chunk.length, chunk);

            // create dataset
            int dataset_id = -1;
            try
            {
                dataset_id = H5.H5Dcreate(resolution_group_id,
                        "Data",
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

            // do the binning
            //
            int[] binning = new int[3];

            if ( r > 0 )
            {
                for ( int i = 0; i < 3; i++ )
                {
                    binning[i] = binnings.get(r)[i] / binnings.get(r-1)[i] ;
                }
                impBinned  = Utils.bin( impBinned, binning , "binned", "AVERAGE");
            }
            else
            {
                impBinned = imp;
            }

            // write data
            //
            if ( imp.getBitDepth() == 8 )
            {
                byte[] data = getByteData( impBinned, c, t );

                H5.H5Dwrite(dataset_id,
                        image_memory_type,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data );

            }
            else if ( imp.getBitDepth() == 16 )
            {

                short[] data = getShortData( impBinned, c, t );

                H5.H5Dwrite( dataset_id,
                        image_memory_type,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data );

            }
            else if ( imp.getBitDepth() == 32 )
            {
                float[] data = getFloatData( impBinned, c, t );

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
            H5.H5Gclose( resolution_group_id );

        }


        H5.H5Fclose( file_id );
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
