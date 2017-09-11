package bigDataTools;

import ij.IJ;
import ij.ImagePlus;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Created by tischi on 13/07/17.
 */
public class HDF5Writer {

    public HDF5Writer()
    {

    }

    public void saveAsImaris(ImagePlus imp){

        ArrayList<int[]> binnings = new ArrayList<>();
        ArrayList<int[]> sizes = new ArrayList<>();
        ArrayList<int[]> chunks = new ArrayList<>();
        ArrayList<String> datasets = null;

        setImarisResolutionLevelsAndChunking(imp, binnings, sizes, chunks, datasets);

        IJ.log("sizes:");
        logArrayList(sizes);

        IJ.log("binnings:");
        logArrayList(binnings);

        IJ.log("chunks:");
        logArrayList(chunks);


        int frame = 0;
        int channel = 0;
        String directory = "/Users/tischi/Desktop/example-data/imaris-out/";
        String fileName = "aaa.h5";

        writeH5fileWithResolutionPyramid(imp, frame, channel, binnings, datasets, fileName, directory);



        // for loop time and channel: writeH5fileWithPyramid
        //

    }


    private void logArrayList( ArrayList<int[]> arrayList )
    {
        for ( int[] entry : arrayList )
        {
            IJ.log( "" + entry[0] + "," + entry[1] + "," + entry[2]);
        }
    }

    /*

    def get_imaris_downsampling_factors_and_chunk_sizes(size):

    """
    :param size: size in (x,y,z)
    :return: pseudocode from http://open.bitplane.com/Default.aspx?tabid=268
void getMultiResolutionPyramidalSizes( const size_t[3] aDataSize,
std::vector& aResolutionSizes)
{
const float mMinVolumeSizeMB = 1.f;
aResolutionSizes.clear();
size_t[3] vNewResolution = aDataSize;

float vVolumeMB;
do {
vResolutionSizes.push_back(vNewResolution);
size_t[3] vLastResolution = vNewResolution;
size_t vLastVolume = vLastResolution[0] * vLastResolution[1] * vLastResolution[2];
for (int d = 0; d < N; ++d) {
if ((10*vLastResolution[d]) * (10*vLastResolution[d]) > vLastVolume / vLastResolution[d])
vNewResolution[d] = vLastResolution[d] / 2;
else
vNewResolution[d] = vLastResolution[d];
// make sure we don't have zero-size dimension
vNewResolution[d] = std::max((size_t)1, vNewResolution[d]);
}
vVolumeMB = vNewResolution[0] * vNewResolution[1] * vNewResolution[2]) / (1024.f * 1024.f);
} while (vVolumeMB > mMinVolumeSizeMB);
}
    """

    # size in x,y,z
    resolutions = [np.array(size[::-1]).astype(np.uint64)]
    downSamplingFactors = [[1,1,1]]
    chunks = [[4,32,32]]
    # chunks = [[32,128,128]]

    # resolutions[-1] = resolutions[-1] - np.array(resolutions[-1]) % np.array(chunks[-1]) + ((np.array(resolutions[-1]) % np.array(chunks[-1]))!=0).astype(np.uint16) * np.array(chunks[-1])
    resolutions[-1] = resolutions[-1]

    # while (np.product(resolutions[-1]) > np.power(10,6)):
    while 1:
        new_downSamplingFactors = [1,1,1]
        new_resolution = np.array([0,0,0],dtype=np.uint64)
        for d in range(3):
            if 100 * np.power(resolutions[-1][d],3) > np.product(resolutions[-1]):
                new_resolution[d] = resolutions[-1][d] / 2
                new_downSamplingFactors[d] = downSamplingFactors[-1][d] * 2
            else:
                new_resolution[d] = resolutions[-1][d]
                new_downSamplingFactors[d] = downSamplingFactors[-1][d]

        if not (np.product(new_resolution)*2 > 1024*1024): break

        resolutions.append(new_resolution)

        # resolutions[-1] = resolutions[-1] - np.array(resolutions[-1]) % np.array(chunks[-1]) + ((np.array(resolutions[-1]) % np.array(chunks[-1]))!=0).astype(np.uint16) * np.array(chunks[-1])

        downSamplingFactors.append(new_downSamplingFactors)
        chunks.append([16,16,16])
        # chunks.append([32,128,128])

    return np.array(downSamplingFactors),np.array(chunks)



def produceImaris_from_scratch(format_config,parameterDict=None,createResolutions=False):
    """
    format_config: dictionary defining the dataset to link to with an ims master file
      the dict must contain the entries:
         outFilePattern: string pattern containing 'tp' and 'ch' placeholders
         channels:       list of channels
         time_pts:       list of tps
         outDir:         dir containing the image data
         spacing:        spacing of the image data

        """

    # multipleResolutionsExist = format_config['multipleResolutionsExist']
    outFilePattern      = format_config['outFilePattern']
    channels            = format_config['channels']
    time_pts            = format_config['time_pts']
    outDir              = format_config['outDir']
    spacing             = format_config['spacing'] # z,y,x

    if 'nameImaris' in format_config:
        outFilePath = os.path.join(outDir, format_config['nameImaris'])
    else:
        outFilePath = os.path.join(outDir, 'imaris.ims')


    if os.path.exists(outFilePath): os.remove(outFilePath)
    outFile = h5py.File(outFilePath,'w')
    outFile.clear()

    def setAttribute(file,group,name,string):
        string = str(string)
        file[group].attrs[name] = np.array([i for i in string],dtype='|S1')
        return

    # parameterDict = getFileParameters(format_config)
    if parameterDict is None:
        parameterDict = getFileParameters(os.path.join(outDir,outFilePattern %{'tp':time_pts[0],'ch':channels[0]}))
    stackSize               = parameterDict['stackSize']
    downSamplingFactors     = parameterDict['downSamplingFactors']
    downSamplingHierarchies = parameterDict['downSamplingHierarchies']
    # chunks                  = parameterDict['chunks']


    # write dataset
    for itime,time in enumerate(time_pts):
        print 'producing imaris tp %s/%s' %(itime,len(time_pts))
        for ichannel,channel in enumerate(channels):

            linkFile = os.path.join(outDir, outFilePattern %{'tp': time,'ch': channel})

            for ires,res in enumerate(downSamplingFactors):
                tmpDict = {'itime' : itime, 'ires' : ires, 'res' : res, 'time' : time, 'channel' : channel}
                hierarchy = 'DataSet/ResolutionLevel %(ires)s/TimePoint %(itime)s/Channel %(channel)s' \
                            % tmpDict
                dataHierarchy = hierarchy+'/Data'
                histogramHierarchy = hierarchy+'/Histogram'
                # linkFile = datasetDict['filePattern%(channel)s' %tmpDict] %tmpDict

                # linkPath = 'Data%(res)s' %tmpDict
                linkPath = downSamplingHierarchies[ires]
                # relativeFile = os.path.relpath(linkFile,start=['.',os.path.dirname(outFilePath)][bool(len(os.path.dirname(outFilePath)))])
                relativeFile = os.path.relpath(linkFile,start=os.path.dirname(outFilePath))

                outFile[dataHierarchy] = 0
                del outFile[dataHierarchy]

                outFile[dataHierarchy] = h5py.ExternalLink(relativeFile,linkPath)

                outFile[histogramHierarchy] = np.ones(3,dtype= np.uint64)
                # outFile[histogramHierarchy] = histogram / np.product(downSamplingFactors[ires])

                # pdb.set_trace()
                shape = stackSize/downSamplingFactors[ires]
                # shape = h5py.File(linkFile)[linkPath].shape[::-1]

                setAttribute(outFile,hierarchy,'ImageSizeX',shape[0])
                setAttribute(outFile,hierarchy,'ImageSizeY',shape[1])
                setAttribute(outFile,hierarchy,'ImageSizeZ',shape[2])

                setAttribute(outFile,hierarchy,'HistogramMin','%.3f' %0)
                setAttribute(outFile,hierarchy,'HistogramMax','%.3f' %1000)

    # np.array(['5', '0'],dtype='|S1')
    tmpHierarchy = 'DataSetInfo/Image'
    imageGroup = outFile.create_group(tmpHierarchy)
    setAttribute(outFile,tmpHierarchy,'Description','description')
    setAttribute(outFile,tmpHierarchy,'ExtMax1',stackSize[0]*spacing[0])
    setAttribute(outFile,tmpHierarchy,'ExtMax0',stackSize[1]*spacing[1])
    setAttribute(outFile,tmpHierarchy,'ExtMax2',stackSize[2]*spacing[2])
    setAttribute(outFile,tmpHierarchy,'ExtMin0',0)
    setAttribute(outFile,tmpHierarchy,'ExtMin1',0)
    setAttribute(outFile,tmpHierarchy,'ExtMin2',0)
    setAttribute(outFile,tmpHierarchy,'Unit',"um")
    setAttribute(outFile,tmpHierarchy,'X',stackSize[0])
    setAttribute(outFile,tmpHierarchy,'Y',stackSize[1])
    setAttribute(outFile,tmpHierarchy,'Z',stackSize[2])


    # for ichan,chan in enumerate(datasetDict['channels']):
    for ichannel,channel in enumerate(channels):
        tmpHierarchy = 'DataSetInfo/Channel %s' %ichannel
        tmpChannelGroup = outFile.create_group(tmpHierarchy)
        setAttribute(outFile,tmpHierarchy,'Color',[['1 0 0'],['1 0 0'],['1 0 0']][ichannel])
        setAttribute(outFile,tmpHierarchy,'ColorMode','BaseColor')
        setAttribute(outFile,tmpHierarchy,'ColorOpacity',1)
        # setAttribute(outFile,tmpHierarchy,'Description','description')

    tmpHierarchy = 'DataSetInfo/TimeInfo'
    timeInfoGroup = outFile.create_group(tmpHierarchy)
    # timeInfoGroup.attrs['TimePoint1'] = "2000-01-01 00:00:00"

    setAttribute(outFile,tmpHierarchy,'DataSetTimePoints',len(time_pts))
    setAttribute(outFile,tmpHierarchy,'FileTimePoints',len(time_pts))
    setAttribute(outFile,tmpHierarchy,'TimePoint1',"2000-01-01 00:00:00")

    tmpHierarchy = '.'

    setAttribute(outFile,tmpHierarchy,'DataSetDirectoryName','DataSet')
    setAttribute(outFile,tmpHierarchy,'DataSetInfoDirectoryName','DataSetInfo')
    setAttribute(outFile,tmpHierarchy,'ImarisDataSet','ImarisDataSet')
    # setAttribute(outFile,tmpHierarchy,'ImarisVersion','8.4.0') # renders file unrecognizable
    setAttribute(outFile,tmpHierarchy,'ImarisVersion','5.5.0')
    setAttribute(outFile,tmpHierarchy,'NumberOfDataSets',1)
    setAttribute(outFile,tmpHierarchy,'ThumbnailDirectoryName','Thumbnail')

    outFile.close()
     */



    public void setImarisResolutionLevelsAndChunking(ImagePlus imp,
                                                     ArrayList<int[]> binnings,
                                                     ArrayList<int[]> sizes,
                                                     ArrayList<int[]> chunks,
                                                     ArrayList<String> datasets)
    {

        long minVoxelVolume = 1024 * 1024;

        int[] size = new int[3];
        size[0] = imp.getWidth();
        size[1] = imp.getHeight();
        size[2] = imp.getNSlices();

        sizes.add( size );

        binnings.add( new int[]{1,1,1} );

        chunks.add( new int[]{4,32,32} );

        long voxelVolume = 0;
        int iResolution = 0;

        do
        {
            int[] lastSize = sizes.get( iResolution );
            int[] lastBinning = binnings.get( iResolution );

            int[] newSize = new int[3];
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
            chunks.add( new int[]{16,16,16} );

            voxelVolume = newSize[0] * newSize[1] * newSize[2];

            iResolution++;

        } while ( 2 * voxelVolume > minVoxelVolume );

    }


    /**
     * Write *.ims master files for Imaris
     */
    public void writeImarisMasterH5(ArrayList<String> datasets,
                                    ArrayList<String> fileNames)
    {

    }

    /**
     * Write *.xml and *.h5 master files for BigDataViewer
     */
    public void writeBdvMasterH5()
    {

    }


    /**
     * Writes an hdf5 file with resolution pyramid for the
     * given frame and channel
     */
    public void writeH5fileWithResolutionPyramid(ImagePlus imp,
                                       int frame,
                                       int channel,
                                       ArrayList<int[]> binnings,
                                       ArrayList<String> datasets,
                                       String fileName,
                                       String directory)
    {
        /*
        // check whether this
        String filePath = directory + File.separator + fileName;
        File file = new File( filePath );
        if ( file.exists() ) file.delete();

        int h5FileID = H5.H5Fcreate( filePath, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT );

        for ( int iResolution = 0 ; iResolution < binnings.size(); iResolution++ )
        {
            String hierarchy =
        }

        hierarchy = 'DataSet/ResolutionLevel %(ires)s/TimePoint %(itime)s/Channel %(channel)s' \
        % tmpDict
            dataHierarchy = hierarchy+'/Data'
        histogramHierarchy = hierarchy+'/Histogram'
        # linkFile = datasetDict['filePattern%(channel)s' %tmpDict] %tmpDict

        # linkPath = 'Data%(res)s' %tmpDict
        linkPath = downSamplingHierarchies[ires]
        # relativeFile = os.path.relpath(linkFile,start=['.',os.path.dirname(outFilePath)][bool(len(os.path.dirname(outFilePath)))])
        relativeFile = os.path.relpath(linkFile,start=os.path.dirname(outFilePath))

        outFile[dataHierarchy] = 0
        del outFile[dataHierarchy]

        // https://support.hdfgroup.org/HDF5/doc/RM/RM_H5L.html#Link-CreateExternal
        // https://support.hdfgroup.org/ftp/HDF5/current/src/unpacked/examples/h5_extlink.c
        H5.H5Lcreate_external();
        outFile[dataHierarchy] = h5py.ExternalLink(relativeFile,linkPath)

        outFile[histogramHierarchy] = np.ones(3,dtype= np.uint64)
        # outFile[histogramHierarchy] = histogram / np.product(downSamplingFactors[ires])

        # pdb.set_trace()
        shape = stackSize/downSamplingFactors[ires]
        # shape = h5py.File(linkFile)[linkPath].shape[::-1]

        setAttribute(outFile,hierarchy,'ImageSizeX',shape[0])
        setAttribute(outFile,hierarchy,'ImageSizeY',shape[1])
        setAttribute(outFile,hierarchy,'ImageSizeZ',shape[2])

        setAttribute(outFile,hierarchy,'HistogramMin','%.3f' %0)
        setAttribute(outFile,hierarchy,'HistogramMax','%.3f' %1000)

        */

    }



}
