# Big Data Processor

## Citation

This github repository can be cited (registered at [ZENODO](https://zenodo.org/)):
- Tischer, C., Norlin, N., and R. Pepperkok (2019) BigDataProcessor: Fiji plugin for visual inspection and processing of big image data. http://doi.org/10.5281/zenodo.2574702

## Overview

The inspection and manipulation of TB sized image data as produced by light-sheet and electron microscopy poses a challenge, because loading the whole data set from disc into RAM is very time consuming or may not even be possible. Is is thus necessary to employ lazy-loading strategies that, e.g., only load the currently visible fraction of the data set into RAM (see e.g., Ref BigDataViewer).  

The Big Data Processor (BDC) enables fast lazy-loading of Tiff and Hdf5 based image data employing ImageJ’s VirtualStack class (LINK), where only the currently displayed image plane is loaded into RAM. Using this technology, TB sized data sets can be readily opened and interactively browsed. The data is presented in ImageJ's Hyperstack Viewer, which is well known to many life scientists. All ImageJ measurement tools, such as line profile or regions of interest based measurements are available. In fact, essentially all of ImageJ's functionality is available, however one has to pay attention, because some operations will attempt to copy the data into RAM, which, of course, will fail is the data are too big.

In addition to viewing big image data, the BDC supports cropping and saving of big image data including binning and bit-depth conversion. This functionality is useful, because raw microscopy data is often not in an ideal state for image analysis. For example, only part of the acquired data may be of actual interest, either because larger fields of view have been acquired to compensate for unpredictable sample motion, or scientifically interesting phenomena have only occurred in specific parts of the imaged sample. Moreover, pixel density and bit-depth can be unnecessarily high, e.g., because camera based microscope systems with fixed pixel size and bit-depth have been used. Or the raw data file-format might simply not be compatible with the analysis software.

In addition to conventional "static" cropping, the BDC also provides an "adaptive" cropping functionality, based on center-of-mass or cross-correlation based tracking. This is useful when the sample moves during acquisition (e.g., due to microscope drift or biological motility), rendering a static crop of the image suboptimal.

Finally, chromatic shifts can be interactively corrected by specifying x and y pixel offsets.

## Supported file formats

Currently, for both reading and writing we support Tiff and hdf5 based image data. To our knowledge those are currently the most popular (open-source) file formats.

### Reading 

For reading, the BDC supports pattern matching based file parsing to accommodate different naming schemes, specifying z-slice, channels, and time-points.

Example use-cases include:

- Luxendo hdf5 based light-sheet data.
- Leica Tiff based light-sheet data.
- Electron microscopy Tiff based data.
- Custom-built light-sheet microscope Tiff based data.

### Writing

The BDC supports writing to Tiff and Hdf5 files. For Tiff writing one can choose between one file per plane or one file per channel and time-point. For Hdf5 writing, an Imaris compatible multi-resolution file format is supported with channels and time-points in separate files, linked together by one "header" hdf5 file. 

## Installation

The Big Data Processor runs as a PlugIn within Fiji.

- Please install [Fiji](fiji.sc)
- Within Fiji, please enable the [Update Site](https://imagej.net/Update_Sites): 
    - [X] EMBL-CBA

## Running Big Data Processor

The Big Data Processor can be found in Fiji's menu:

- [Fiji > Plugins > BigDataTools > Big Data Processor]
