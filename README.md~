# BigDataTools

Analyzing and manipulating terabyte-sized image data produced by light-sheet microscopy poses several major challenges. Two examples are: 1) Loading the whole data set into RAM is very time consuming or may not even be possible; 2) Performing computations on the whole data set can take several hours using traditional approaches. We have developed two ImageJ plugins that mitigate these challenges. Our “Data
Streaming Tools” plugin enables fast streaming of disk-resident data by employing ImageJ’s virtual stack functionality where only the currently visible plane is loaded into RAM and currently supports (compressed) Tiff and Hdf5-based data. Our “Big Data Tracker” plugin enables efficient multi-threaded object tracking, only loading the data portions required to track the selected objects. Moreover, both plugins enable creation of cropped (manually selected and tracking-based) views on the original data, without any
data duplication.

## Supported data formats

Currently we support data saved as Tiff or HDF5 stacks, the latter is for instance produced by Luxendo light sheet microscopes.

## Relation to the Bio-Formats plugin 

- Fiji's Bio-Formats plugin does currently not support streaming from Hdf5 stacks; this is possible using the Data Streaming Tools (DST).
- Bio-Formats does support streaming from Tiff stacks, however the DST improve this functionality by "lazy" and faster Tiff header parsing. For example, opening a folder with 500 Tiff stacks comprising 1918x1916x157 voxels each (~ 0.5TB in total) takes less than 5 seconds. 
- Data streamed via the Bio-Formats plugin cannot be cropped; this is possible using the DST.

## Use cases

- Drift correction of a 2.4 TB data set in 10-30 minutes.

## Installation

Please place below file into Fiji's plugins folder:

https://github.com/tischi/fiji-plugin-bigDataTools/raw/master/out/artifacts/fiji--bigDataTools_.jar

...and restart Fiji.

## Data Streaming Tools

[Fiji > Plugins > BigDataTools > Data Streaming Tools]

You will be in the "Streaming" tab. 

[Load files matching]:
- [.*] you do not filter, all files will be loaded
- [.*.tif] would filter for files ending in tif
 
[Load multiple channels]:
- [None] you will only load one channel
- [from sub-folders] assumes that the channels are in different folders, below you have to chose the parent folder
- [.*\_C&lt;c>\_T&lt;t>.tif] regular expression matching for the case that the different channels are in the same folder; you can edit this field to adapt it to your naming scheme.

"Hdf5 data set": 
- for elastix output data select: [ITKImage/0/VoxelData]
- for Luxendo data select: [Data]
- you can edit this field for other formats
 
[Stream from folder]
- select a folder containing a sequence of Tiff or Hdf5 files, where each file is contains the z-stack of one time-point. 

## Big Data Tracker

The Big Data Tracker (BDT) plugin enables efficient and fast tracking in and drift correction of terabyte-sized data sets. The key feature is that only the minimal data cube needed to track an object or to drift correct the data set is loaded at every time-point.
  

