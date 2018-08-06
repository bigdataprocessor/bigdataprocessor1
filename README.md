# BigDataTools

The inspection and manipulation of TB sized image data as produced by light-sheet and electron microscopy poses several major challenges. 

Two examples are: 

1) Loading the whole data set into RAM is very time consuming or may not even be possible.
2) Performing computations on the whole data set can take several hours using traditional approaches. 

We have developed two ImageJ plugins that mitigate these challenges. 

The “Data Streaming Tools” plugin enables fast streaming of disk-resident data by employing ImageJ’s virtual stack functionality, where only the currently visible plane is loaded into RAM. Using this technology TB sized data sets can be opened and interactively browsed in few seconds.

The “Big Data Tracker” plugin enables efficient multi-threaded object tracking, only loading the data portions (sub-volumes) required to track the selected objects. Tracking of objects in arbirtray large data sets can be performed at a rate of about 1 seconds per time-point.

Moreover, both plugins enable creation of cropped (manually selected and tracking-based) views on the original data, without any data duplication.

## Supported data formats

Currently, we support data Tiff and HDF5 based data.

## Installation

- Install Fiji (fiji.sc)
- Enable update site: EMBL-CBA

## Data Streaming Tools

[Fiji > Plugins > BigDataTools > Data Streaming Tools]

## Big Data Tracker

[Fiji > Plugins > BigDataTools > Big Data Tracker]
  
##  Example use cases

- Viewing, cropping and binning of TB-sized Leica light sheet, Luxendo light sheet, and electron microscopy data sets.
- Computing drift correction of a 2.4 TB data set in 10-30 minutes (excluding resaving the corrected data).

## Help

Please contact Christian.Tischer@EMBL.DE 

