package bigDataTools;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import java.io.File;
import java.util.ArrayList;

public class Hdf5Utils {


    public static void setH5IntegerAttribute( int dataset_id, String attrName, int[] attrValue )
    {

        long[] attrDims = { attrValue.length };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(attrDims.length, attrDims, null);

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate(dataset_id, attrName,
                HDF5Constants.H5T_STD_I32BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute data.
        H5.H5Awrite(attribute_id, HDF5Constants.H5T_NATIVE_INT, attrValue);

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    public static void setH5DoubleAttribute( int dataset_id, String attrName, double[] attrValue )
    {

        long[] attrDims = { attrValue.length };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(attrDims.length, attrDims, null);

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate(dataset_id, attrName,
                HDF5Constants.H5T_IEEE_F64BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute data.
        H5.H5Awrite(attribute_id, HDF5Constants.H5T_NATIVE_DOUBLE, attrValue);

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    public static void setH5StringAttribute( int dataset_id, String attrName, String attrValue )
    {

        long[] attrDims = { attrValue.getBytes().length };

        // Create the data space for the attribute.
        int dataspace_id = H5.H5Screate_simple(attrDims.length, attrDims, null);

        // Create the data space for the attribute.
        //int dataspace_id = H5.H5Screate( HDF5Constants.H5S_SCALAR );

        // Create attribute type
        //int type_id = H5.H5Tcopy( HDF5Constants.H5T_C_S1 );
        //H5.H5Tset_size(type_id, attrValue.length());

        int type_id = HDF5Constants.H5T_C_S1;

        // Create a dataset attribute.
        int attribute_id = H5.H5Acreate( dataset_id, attrName,
                type_id, dataspace_id,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // Write the attribute
        H5.H5Awrite(attribute_id, type_id, attrValue.getBytes());

        // Close the attribute.
        H5.H5Aclose(attribute_id);
    }

    public static void h5WriteLongArrayListAs32IntArray(int group_id, ArrayList< long[] > list, String name )
    {

        int[][] data = new int[ list.size() ][ list.get(0).length ];

        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[i][j] = (int) list.get(i)[j];
            }
        }

        long[] data_dims = { data.length, data[0].length };

        int dataspace_id = H5.H5Screate_simple( data_dims.length, data_dims, null );

        int dataset_id = H5.H5Dcreate(group_id, name,
                HDF5Constants.H5T_STD_I32LE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite( dataset_id, HDF5Constants.H5T_NATIVE_INT,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data );

        H5.H5Dclose(dataset_id);

        H5.H5Sclose(dataspace_id);

    }

    public static void h5WriteLongArrayListAsDoubleArray( int group_id, ArrayList < long[] > list, String name )
    {

        double[] data = new double[list.size() * list.get(0).length];

        int p = 0;
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[ p++ ] = list.get(i)[j];
            }
        }

        long[] data_dims = { list.size(), list.get(0).length };

        int dataspace_id = H5.H5Screate_simple( data_dims.length, data_dims, null );

        int dataset_id = H5.H5Dcreate( group_id, name,
                HDF5Constants.H5T_IEEE_F64BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite( dataset_id, HDF5Constants.H5T_NATIVE_DOUBLE,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data );

        H5.H5Dclose(dataset_id);

        H5.H5Sclose(dataspace_id);

    }

    public static void h5WriteIntArrayListAsDoubleArray( int group_id, ArrayList < int[] > list, String name )
    {

        double[] data = new double[list.size() * list.get(0).length];

        int p = 0;
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = 0; j < list.get(i).length; j++)
            {
                data[ p++ ] = list.get(i)[j];
            }
        }

        long[] data_dims = { list.size(), list.get(0).length };

        int dataspace_id = H5.H5Screate_simple( data_dims.length, data_dims, null );

        int dataset_id = H5.H5Dcreate( group_id, name,
                HDF5Constants.H5T_IEEE_F64BE, dataspace_id,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        H5.H5Dwrite( dataset_id, HDF5Constants.H5T_NATIVE_DOUBLE,
                HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data );

        H5.H5Dclose(dataset_id);

        H5.H5Sclose(dataspace_id);

    }

    public static int getGroup( int file_id, String groupName )
    {
        int group_id;
        group_id = H5.H5Gopen (file_id, groupName, HDF5Constants.H5P_DEFAULT);

        if ( group_id >= 0 )
        {
            // create group (and intermediate groups)
            int gcpl_id = H5.H5Pcreate(HDF5Constants.H5P_LINK_CREATE);
            H5.H5Pset_create_intermediate_group(gcpl_id, true);
            group_id = H5
                    .H5Gcreate(file_id, groupName, gcpl_id,
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
        }

        return ( group_id );

    }

    public static int createFile( String directory, String filename )
    {
        String filePathMaster = directory + File.separator + filename;
        File fileMaster = new File( filePathMaster );
        if (fileMaster.exists()) fileMaster.delete();

        int file_id = H5.H5Fcreate(filePathMaster,
                HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,
                HDF5Constants.H5P_DEFAULT);

        return ( file_id );

    }


}
