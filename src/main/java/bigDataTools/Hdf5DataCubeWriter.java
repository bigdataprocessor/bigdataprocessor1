package bigDataTools;

import bigDataTools.utils.Utils;
import ch.systemsx.cisd.hdf5.hdf5lib.H5F;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageStatistics;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import static bigDataTools.Hdf5Utils.writeDoubleAttribute;
import static bigDataTools.Hdf5Utils.writeStringAttribute;
import static bigDataTools.ImarisUtils.*;

public class Hdf5DataCubeWriter {

    int file_id;
    int image_memory_type;
    int image_file_type;


    private void setImageMemoryAndFileType( ImagePlus imp )
    {

        if ( imp.getBitDepth() == 8 )
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_UCHAR;
            image_file_type = HDF5Constants.H5T_STD_U8BE;
        }
        else if ( imp.getBitDepth() == 16 )
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_USHORT;
            image_file_type = HDF5Constants.H5T_STD_U16BE;
        }
        else if ( imp.getBitDepth() == 32 )
        {
            image_memory_type = HDF5Constants.H5T_NATIVE_FLOAT;
            image_file_type = HDF5Constants.H5T_IEEE_F32BE;
        }
        else
        {
            IJ.showMessage( "Image data type is not supported, " +
                    "only 8-bit, 16-bit and 32-bit floating point are possible." );
        }
    }


    public void writeImarisCompatibleResolutionPyramid(
            ImagePlus imp3d,
            ImarisDataSet idp,
            int c, int t )
    {


        file_id = createFile(
                idp.getDataSetDirectory( c, t, 0 ),
                idp.getDataSetFilename( c, t, 0 ) );

        setImageMemoryAndFileType( imp3d );

        ImagePlus impResolutionLevel = imp3d;

        for ( int r = 0; r < idp.getDimensions().size(); r++ )
        {
            if ( r > 0 )
            {
                // bin further down
                impResolutionLevel = Utils.bin( impResolutionLevel,
                        idp.getRelativeBinnings().get( r )
                        , "binned", "AVERAGE" );
            }

            writeDataCubeAndAttributes( impResolutionLevel,
                    RESOLUTION_LEVEL + r,
                    idp.getDimensions().get( r ), idp.getChunks().get( r ) );

            writeHistogramAndAttributes( impResolutionLevel, RESOLUTION_LEVEL + r );
        }

        H5F.H5Fclose( file_id );
    }


    private void writeDataCubeAndAttributes( ImagePlus imp, String group, long[] dimensionXYZ, long[] chunkXYZ )
    {

        // change dimension order to fit hdf5

        long[] dimension = new long[]{
                dimensionXYZ[ 2 ],
                dimensionXYZ[ 1 ],
                dimensionXYZ[ 0 ] };

        long[] chunk = new long[]{
                chunkXYZ[ 2 ],
                chunkXYZ[ 1 ],
                chunkXYZ[ 0 ] };


        int group_id = Hdf5Utils.createGroup( file_id, group );

        int dataspace_id = H5.H5Screate_simple( dimension.length, dimension, null );

        // create "dataset creation property list" (dcpl)
        int dcpl_id = H5.H5Pcreate( HDF5Constants.H5P_DATASET_CREATE );

        H5.H5Pset_chunk( dcpl_id, chunk.length, chunk );

        // create dataset
        int dataset_id = -1;
        try
        {
            dataset_id = H5.H5Dcreate( group_id,
                    DATA,
                    image_file_type,
                    dataspace_id,
                    HDF5Constants.H5P_DEFAULT,
                    dcpl_id,
                    HDF5Constants.H5P_DEFAULT );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        writeImagePlusData( chunkXYZ, dataspace_id, dataset_id, imp );

        // Attributes
        writeSizeAttribute( group_id, dimensionXYZ );
        writeCalibrationAttribute( dataset_id, imp.getCalibration() );

        H5.H5Sclose( dataspace_id );
        H5.H5Dclose( dataset_id );
        H5.H5Pclose( dcpl_id );
        H5.H5Gclose( group_id );

    }

    private void writeImagePlusData( long[] chunkXYZ, int dataspace_id, int dataset_id, ImagePlus imp )
    {

        // writeHeader data
        //
        if( imp.getBitDepth() == 8 )
        {


            byte[][] data = getByteData( imp, 0, 0 );

            if ( true ) // chunkXYZ[2] != 1 )
            {

                H5.H5Dwrite( dataset_id,
                        image_memory_type,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT,
                        data );
            }
            else
            {
                // experimental...does not work yet...

                // write plane wise

                int status = 0;
                //for ( int i = 0; i < imp.getNSlices()/2; ++i )
                //{
                    long[] start = new long[]{ 10, imp.getHeight()/2, imp.getWidth()/2 };
                    long[] count = new long[]{ 0, 100, 100};
                    //long[] block = new long[]{ 1, 1, 1 };

                    //dataspace_id = H5.H5Dget_space( dataset_id );

                    H5.H5Sselect_hyperslab( dataspace_id,
                            HDF5Constants.H5S_SELECT_SET,
                            start,
                            null,
                            count,
                            null
                    );

                    status = H5.H5Dwrite( dataset_id,
                            image_memory_type,
                            HDF5Constants.H5S_ALL,
                            HDF5Constants.H5S_ALL,
                            HDF5Constants.H5P_DEFAULT,
                            data );
                //}

            }
        }
        else if(imp.getBitDepth()==16)
        {

            short[][] data = getShortData( imp, 0, 0 );

            H5.H5Dwrite( dataset_id,
                    image_memory_type,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5P_DEFAULT,
                    data );

        }
        else if(imp.getBitDepth()==32)
        {
            float[][] data = getFloatData( imp, 0, 0 );

            H5.H5Dwrite( dataset_id,
                    image_memory_type,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5S_ALL,
                    HDF5Constants.H5P_DEFAULT,
                    data );
        }
        else
        {
            IJ.showMessage( "Image data type is not supported, " +
                    "only 8-bit, 16-bit and 32-bit are possible." );
        }

    }


    private void writeSizeAttribute( int group_id, long[] dimension )
    {
        for ( int d = 0; d < 3; ++d )
        {
            writeStringAttribute( group_id,
                    IMAGE_SIZE + XYZ[d],
                    String.valueOf( dimension[d]) );
        }
    }


    private void writeCalibrationAttribute( int object_id, Calibration calibration )
    {

        double[] calibrationXYZ = new double[]
                {
                        calibration.pixelWidth,
                        calibration.pixelHeight,
                        calibration.pixelDepth
                };

        writeDoubleAttribute(  object_id, "element_size_um", calibrationXYZ );

    }

    private void writeHistogramAndAttributes( ImagePlus imp, String group )
    {
        int group_id = Hdf5Utils.createGroup( file_id, group );

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


        writeStringAttribute( group_id,
                HISTOGRAM + "Min",
                String.valueOf( imageStatistics.min ) );

        writeStringAttribute( group_id,
                HISTOGRAM + "Max",
                String.valueOf( imageStatistics.max ) );

        H5.H5Dclose( histo_dataset_id );
        H5.H5Sclose( histo_dataspace_id );
        H5.H5Gclose( group_id );

    }

    private int createFile( String directory, String filename )
    {
        return ( Hdf5Utils.createFile( directory, filename  ) );
    }



    private byte[][] getByteData( ImagePlus imp, int c, int t )
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        byte[][] data = new byte[ size[2] ] [ size[1] * size[0] ];

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);
            data[z] = (byte[]) stack.getProcessor(n).getPixels();

            //System.arraycopy( stack.getProcessor(n).getPixels(), 0, data[z],
            //       0, size[0] * size[1] );

        }

        return ( data );

    }

    private short[][] getShortData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        short[][] data = new short[ size[2] ] [ size[1] * size[0] ];

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);
            data[z] = (short[]) stack.getProcessor(n).getPixels();

            //System.arraycopy( stack.getProcessor(n).getPixels(), 0, data[z],
            //        0, size[0] * size[1] );

        }

        return ( data );

    }

    private float[][] getFloatData(ImagePlus imp, int c, int t)
    {
        ImageStack stack = imp.getStack();

        int[] size = new int[]{
                imp.getWidth(),
                imp.getHeight(),
                imp.getNSlices()
        };

        float[][] data = new float[ size[2] ] [ size[1] * size[0] ];

        for (int z = 0; z < imp.getNSlices(); z++)
        {
            int n = imp.getStackIndex(c+1, z+1, t+1);
            data[z] = (float[]) stack.getProcessor(n).getPixels();

            //System.arraycopy( stack.getProcessor(n).getPixels(), 0, data[z],
            //        0, size[0] * size[1] );

        }
        return ( data );

    }


}
