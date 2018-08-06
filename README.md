# BigDataTools

Analyzing and manipulating terabyte-sized image data produced by light-sheet microscopy poses several major challenges. 

Two examples are: 

1) Loading the whole data set into RAM is very time consuming or may not even be possible.
2) Performing computations on the whole data set can take several hours using traditional approaches. 

We have developed two ImageJ plugins that mitigate these challenges. 

The “Data Streaming Tools” plugin enables fast streaming of disk-resident data by employing ImageJ’s virtual stack functionality where only the currently visible plane is loaded into RAM and currently supports (compressed) Tiff and Hdf5-based data. 

The “Big Data Tracker” plugin enables efficient multi-threaded object tracking, only loading the data portions required to track the selected objects. 

Moreover, both plugins enable creation of cropped (manually selected and tracking-based) views on the original data, without anydata duplication.

## Supported data formats

Currently we support data Tiff and HDF5 based data.

## Installation

- Install Fiji (fiji.sc)
- Enable update site: EMBL-CBA

## Data Streaming Tools

[Fiji > Plugins > BigDataTools > Data Streaming Tools]

### "Streaming" tab

Example use cases:

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

[Fiji > Plugins > BigDataTools > Big Data Tracker]

The Big Data Tracker (BDT) plugin enables efficient and fast tracking in and drift correction of terabyte-sized data sets. The key feature is that only the minimal data cube needed to track an object or to drift correct the data set is loaded at every time-point.
  
### Parameter settings

#### Image feature enhancement

Image feature enhancement was implemented to help the "correlation" tracking method and we think it is less useful for the center-of-mass based tracking.

In correlation trackin one looks for the shift between subsequent images that maximises the sum of the pixel-wise product of both images; thereby basically trying to match bright pixels in image1 onto bright pixels in image2. One issue with this approach is the situation when, e.g., image2 features a really bright region that was not present already in image2. The correlation will be maximal if this really bright region falls onto some bright region of image1, even if pixel-wise match is not perfect. In other words the correlation has the tendency to simply find a shift that puts the two brightest regions of both images on top of each other, even if they don't look exactly the same. That is why we implemented the "threshold" image feature enhancement method, which replaces all pixel values by 0 or 1, thereby counteracting the overly strong weight of very bright image regions.  

In general, a good way to check whether the enhancement works for your data is to check how the images look after the feature enhancement, using the "Show N first processed image pairs" setting. 

##### Threshold

For each image plane this method runs an automated tresholding algorithm, currently it is the "Default" method of ImageJ (https://imagej.net/Auto_Threshold#Default). We might add other thresholding methods in the future. Please let us know if you prefer another one. 


##  Example use cases

- Viewing, cropping and binning of TB-sized Leica light sheet, Luxendo light sheet, and electron microscopy data sets.
- Computing drift correction of a 2.4 TB data set in 10-30 minutes (excluding resaving the corrected data).
