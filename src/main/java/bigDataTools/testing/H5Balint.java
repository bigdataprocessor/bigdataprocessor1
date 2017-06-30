package bigDataTools.testing;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

/**
 * Created by tischi on 30/06/17
 **/


public class H5Balint {

    public static void main(String args[]) throws Exception {

        createFile("/Users/tischi/Desktop/balint_noFilter.h5", true);

    }

    public static void createFile(String fileName, boolean useFilter) throws Exception
    {
        // create a new file
        //
        // fid = H5Fcreate(outfile.c_str(),H5F_ACC_TRUNC,H5P_DEFAULT,H5P_DEFAULT);
        //
        int file_id = H5.H5Fcreate(fileName, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

        // create a simple dataspace - size in zyx order
        //
        // dimensions = 3;
        // hsize_t shape[3]= {20, 512, 512} ;
        // sid = H5Screate_simple(3, shape, NULL);
        //
        int rank = 3;
        long[] shape = new long[]{20,512,512};
        int space_id = H5.H5Screate_simple(rank, shape, null);

        // create "dataset creation property list" id (dcpl)
        // compression options have to be added to this property list
        //
        // plist = H5Pcreate(H5P_DATASET_CREATE);
        //
        int dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);

        // set chunked layout
        //
        // H5Pset_chunk(plist, 3, chunkshape);
        //
        long[] chunkshape = new long[]{1,shape[1],shape[2]};
        H5.H5Pset_chunk(dcpl_id, rank, chunkshape);

        // define options for filter
        //
        // unsigned int compressionType = 107;
        //
        int compressionType = 107;

        // since only int can be passed, quantStep multiplied by 1000
        // q=1 -> quantStep=1000 will give within noise level for Hamamatsu orca flash4
        // (new version  will be more flexible - very soon)
        //
        // unsigned int quantStep = 1000;
        //
        int quantStep = 1000;

        // background offset of the camera, it's usually 100, unless bg is already subtracted
        //
        // unsigned int bgLevel = 0;
        // unsigned int tiles = 64;
        //
        int bgLevel = 0;
        int tiles = 64;

        // cd_values has all settings, and will be passed to the compression filter
        //
        // define N_CD_VALUES 5
        // unsigned int cd_values[N_CD_VALUES]=
        // {
        //     0, compressionType, quantStep, bgLevel, tiles
        // };
        final int N_CD_VALUES = 5;
        int[] cd_values = new int[]{0, compressionType, quantStep, bgLevel, tiles};

        // set the filter
        //
        // H5Pset_filter(plist, 333, H5Z_FLAG_OPTIONAL, N_CD_VALUES, cd_values);
        //
        if( useFilter )
        {
            int plugin_filter_id = 333;
            H5.H5Pset_filter(dcpl_id, plugin_filter_id, HDF5Constants.H5Z_FLAG_OPTIONAL, N_CD_VALUES, cd_values);
        }

        // create dataset
        //
        // dset = H5Dcreate2(fid, filter_names[iFilter], types[f], sid, 0, plist, 0);
        //
        String name = "data";
        int type_id = HDF5Constants.H5T_NATIVE_SHORT;
        int dataset_id;
        if( useFilter )
        {
            dataset_id = H5.H5Dcreate(file_id, name, type_id, space_id, 0, dcpl_id, 0);
        }
        else
        {
            dataset_id = H5.H5Dcreate(file_id, name, type_id, space_id, 0, HDF5Constants.H5P_DEFAULT, 0);
        }

        // put stuff in data
        // ...
        int nx = (int)shape[2];
        int ny = (int)shape[1];
        int nz = (int)shape[0];

        short[] data = new short[ nz * ny * nx ];
        int i = 0;
        for (int x = 0; x < nx; x++)
            for (int y = 0; y < ny; y++)
                for (int z = 0; z < nz; z++)
                {
                    int pos = (int)(z * (shape[1]*shape[2]) + y * shape[1] + x);
                    data[ pos ] = (short) (x);
                }






        // write the data
        //
        // H5Dwrite(dset, types[f], H5S_ALL, H5S_ALL, H5P_DEFAULT, data);
        //
        H5.H5Dwrite(dataset_id,
                type_id,
                HDF5Constants.H5S_ALL,
                HDF5Constants.H5S_ALL,
                HDF5Constants.H5P_DEFAULT,
                data);

        // close file
        //
        // H5Fflush(fid, H5F_SCOPE_GLOBAL);
        // H5Dclose(dset);
        //
        H5.H5Fflush(file_id, HDF5Constants.H5F_SCOPE_GLOBAL);
        H5.H5Dclose(dataset_id);


    }
}

