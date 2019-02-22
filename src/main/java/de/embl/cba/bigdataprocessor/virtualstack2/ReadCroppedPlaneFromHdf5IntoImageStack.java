package de.embl.cba.bigdataprocessor.virtualstack2;

/**
 * Created by tischi on 21/06/17.
 */

import ch.systemsx.cisd.hdf5.*;
import ij.ImageStack;

/** Reads one plane into an ImageStack; **/
class ReadCroppedPlaneFromHdf5IntoImageStack implements Runnable {
    String dataset;
    int[] size;
    long[] offset;
    ImageStack stack;
    int slice;
    IHDF5Reader reader;
    String filePath;

    ReadCroppedPlaneFromHdf5IntoImageStack(String filePath, IHDF5Reader reader, String dataset, int[] size, long[] offset, ImageStack stack, int slice)
    {
        this.reader = reader;
        this.dataset = dataset;
        this.size = size;
        this.offset = offset;
        this.stack = stack;
        this.slice = slice;
        this.filePath = filePath;
    }

    @Override
    public void run()
    {
        /*
        H5.H5Dopen(loc_id, name, access_plist_id)

        //IHDF5ShortReader shortReader = new HDF5ShortReader
        this.baseReader.checkOpen();
        ICallableWithCleanUp readCallable = new ICallableWithCleanUp() {
            public MDShortArray call(ICleanUpRegistry registry) {
                int dataSetId = HDF5ShortReader.this.baseReader.h5.openDataSet(HDF5ShortReader.this.baseReader
                .fileId, objectPath, registry);

                try {
                    HDF5BaseReader.DataSpaceParameters ex = HDF5ShortReader.this.baseReader.getSpaceParameters
                    (dataSetId, offset, blockDimensions, registry);
                    short[] info1 = new short[ex.blockSize];
                    HDF5ShortReader.this.baseReader.h5.readDataSet(dataSetId, HDF5Constants.H5T_NATIVE_INT16, ex
                    .memorySpaceId, ex.dataSpaceId, info1);
                    return new MDShortArray(info1, ex.dimensions);
                    IHDF5Reader reader = HDF5Factory.openForReading(filePath);
                    stack.setPixels(reader.uint16().readMDArrayBlockWithOffset(dataset, size, offset).getAsFlatArray
                    (), slice);
                }



            }
            */
    }

}
