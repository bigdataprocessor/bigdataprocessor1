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




def produceXMLAndMasterH5(format_config,parameterDict=None,createResolutions=False):

    """
    Multiview Reconstruction convention comments:
    - spacing irrelevant for fusing (image is regarded in pixel space)
    - transformation goes from global to image
    - working example:
                        toPhys = np.append(np.diag(np.array(1./outSpacing)).flatten(), np.array([0, 0, 0]), 0)
                        toPix = np.append(np.diag(np.array(outSpacing)).flatten(), np.array([0, 0, 0]), 0)
                        pParams1 = fusion.composeAffineTransformations([toPhys, tmpParams, toPix])
                        pParams2 = [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0]

    this function takes parameters in preprocessing convention and converts them to fiji convention
    """


    if 'nameXML' in format_config:
        outFileXML = os.path.join(format_config['outDir'], format_config['nameXML'])
    else:
        outFileXML = os.path.join(format_config['outDir'], 'bdv_dataset.xml')

    if 'nameMasterHDF5' in format_config:
        outFileH5 = os.path.join(format_config['outDir'], format_config['nameMasterHDF5'])
    else:
        outFileH5 = os.path.join(format_config['outDir'], 'bdv_dataset.h5')

    # if 'time_pts' in format_config:
    time_points = format_config['time_pts']
    # elif 'startTime' in format_config:
    #     time_points = range(format_config['startTime'], format_config['endTime'] + 1, format_config['stepTime'])
    #     format_config['time_pts'] = time_points

    if os.path.exists(outFileH5): os.remove(outFileH5)

    # outFilePattern = format_config['outFilePattern']
    # if createResolutions:
    #     produceBasicFormatFromMicroscopeFormat(format_config,
    #         set_attrs=False)
    #     outFilePattern = outFilePattern.split('.')[0] + '.lux'
    # else:
    #     outFilePattern = outFilePattern.split('.')[0] + '.h5'
    outFilePattern = format_config['outFilePattern']

    first_file_name = os.path.join(format_config['outDir'],format_config['outFilePattern'] %{'tp': format_config['time_pts'][0],
        'ch': format_config['channels'][0]})

    if parameterDict is None:
        parameterDict = getFileParameters(first_file_name)

    stackSize = parameterDict['stackSize']
    downSamplingFactors = parameterDict['downSamplingFactors']
    downSamplingHierarchies = parameterDict['downSamplingHierarchies']
    chunks = parameterDict['chunks']

    spacingParams = np.array([format_config['spacing'][0], 0, 0, 0, format_config['spacing'][1], 0, 0, 0, format_config['spacing'][2], 0, 0, 0])

    # spacing = np.array(images[0].GetSpacing())
    spacing = format_config['spacing']

    # parameters = [[1,0,0,0,1,0,0,0,1,0,0,0]]

    viewSetupsString = ""
    angleAttributeString = ""
    viewRegistrationString = ""
    channelAttributeString = ""

    for iview in range(len(format_config['channels'])):

        viewDict = dict()
        viewDict['viewId'] = iview
        # viewDict['size'] = "%s %s %s" %tuple(np.array(images[iview].GetSize()))
        # viewDict['size'] = "%s %s %s" %tuple(format_config['size'])
        viewDict['size'] = "%s %s %s" %tuple(stackSize)
        viewDict['spacing'] = "%s %s %s" %tuple(spacing)
        viewDict['angle'] = 0
        # viewDict['name'] = iview+1
        # viewDict['name'] = 0
        viewDict['parameters'] = "%s %s %s %s %s %s %s %s %s %s %s %s" %tuple(spacingParams[[0,1,2,9,3,4,5,10,6,7,8,11]])

        viewDict['channel'] = iview
        viewDict['channelId'] = iview
        viewDict['channelName'] = iview
        viewDict['angleId'] = iview
        viewDict['angleName'] = iview


        # if not itime:
        viewSetupsString += viewSetupStringTemplate %viewDict
        angleAttributeString += angleAttributeStringTemplate %viewDict
        channelAttributeString += channelAttributeStringTemplate %viewDict

        for itime,time in enumerate(time_points):
            viewDict['time'] = time
            viewRegistrationString += viewRegistrationStringTemplate %viewDict

    tmpDict = dict()
    tmpDict['filePattern'] = "time000000_{a}.tif"
    tmpDict['viewSetups'] = viewSetupsString
    tmpDict['angles'] = angleAttributeString
    tmpDict['registrations'] = viewRegistrationString
    tmpDict['channels'] = channelAttributeString

    tmpDict['firstTP'] = time_points[0]
    tmpDict['lastTP'] = time_points[-1]
    tmpDict['outFileH5'] = os.path.basename(outFileH5)

    xml = xmlStringTemplateBDV %tmpDict

    tmpFile = open(outFileXML,'w')
    tmpFile.write(xml)
    tmpFile.close()

    # produce hdf5
    tmpFile = h5py.File(outFileH5)
    # for iview in range(len(images)):
    for iview in range(len(format_config['channels'])):
        tmpGroup = tmpFile.create_group('s%02d' %iview)
        # tmpGroup['resolutions'] = np.array(format_config['downSamplingFactors']).astype(np.float64)
        tmpGroup['resolutions'] = np.array(downSamplingFactors).astype(np.float64)
        # tmpGroup['subdivisions'] = np.array(format_config['chunks'])
        tmpGroup['subdivisions'] = np.array(chunks)

    for itime,time in enumerate(time_points):
        timeGroup = tmpFile.create_group('t%05d' %time)
        filePatternDict = dict()
        # filePatternDict['time'] = time
        filePatternDict['tp'] = time
        # for iview in range(len(images)):
        for iview in range(len(format_config['channels'])):
            viewGroup = timeGroup.create_group('s%02d' %iview)
            # filePatternDict['iview'] = iview
            filePatternDict['ch'] = iview
            # for idsf,dsf in enumerate(format_config['downSamplingFactors']):
            for idsf,dsf in enumerate(downSamplingFactors):
                dsfGroup = viewGroup.create_group(str(idsf))
                filePatternDict['dsf'] = '%s%s%s' %tuple(dsf)
                # relDir = os.path.relpath(format_config['outDir'][iview],format_config['outDir'][0])
                relDir = '.'
                # dsfGroup['cells'] = h5py.ExternalLink(os.path.join(relDir,format_config['outFilePattern'] %filePatternDict),
                #                                                   "Data%(dsf)s" %filePatternDict)

                # relPath = os.path.join(relDir,outFilePattern.split('.')[0] %filePatternDict).replace('\\','/') + '.h5'
                relPath = os.path.join(relDir,outFilePattern %filePatternDict)
                dsfGroup['cells'] = h5py.ExternalLink(relPath,downSamplingHierarchies[idsf])
                # pdb.set_trace()
    tmpFile.close()
    return
