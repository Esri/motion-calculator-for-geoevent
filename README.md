# motion-calculator-for-geoevent

ArcGIS GeoEvent Server sample Motion Calculator Processor to compute distance, height, speed, acceleration and their statistics (e.g. min, max, average). It is limited to coordinate in longitude, latitude (WGS84). The supported distance units are Kilometers, Miles, and Nautical Miles. The height unit will be in meters for distance in Kilometers and feet for distance in Miles or Nautical Miles.

Note: Use a Field Reducer Processor if only a subset of the fields are needed in the output.

![App](motion-calculator-for-geoevent.png?raw=true)

## Features
* Motion Calculator Processor

## Instructions

Building the source code:

1. Make sure Maven and ArcGIS GeoEvent Server SDK are installed on your machine.
2. Run 'mvn install -Dcontact.address=[YourContactEmailAddress]'

Installing the built jar files:

1. Copy the *.jar files under the 'target' sub-folder(s) into the [ArcGIS-GeoEvent-Server-Install-Directory]/deploy folder.

## Requirements

* ArcGIS GeoEvent Server.
* ArcGIS GeoEvent Server SDK.
* Java JDK 1.8 or greater.
* Maven.

## Resources

* [Download the connector's tutorial](http://www.arcgis.com/home/item.html?id=03c440742bbd4686b16420164d30e448) from the ArcGIS GeoEvent Gallery
* [ArcGIS GeoEvent Server Resources](http://links.esri.com/geoevent)
* [ArcGIS Blog](http://blogs.esri.com/esri/arcgis/)
* [twitter@esri](http://twitter.com/esri)

## Issues

Find a bug or want to request a new feature?  Please let us know by submitting an issue.

## Contributing

Esri welcomes contributions from anyone and everyone. Please see our [guidelines for contributing](https://github.com/esri/contributing).

## Licensing
Copyright 2013 Esri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

A copy of the license is available in the repository's [license.txt](license.txt?raw=true) file.
