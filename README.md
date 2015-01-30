# motion-calculator-for-geoevent

The ArcGIS 10.3.x GeoEvent Extension for Server sample Motion Calculator Processor can be added to a GeoEvent Service to compute distance, timespan, height, speed, acceleration, heading, and some statistics (for example min, max, average) on GeoEvents. It currently supports coordinates in longitude, latitude (WGS84). The supported distance units are kilometers, miles, and nautical miles. The supported height units are meters for distances in kilometers and feet for distance in miles or nautical miles.

Note: Use Field Reducer if only subset of fields are needed for the output.

![App](motion-calculator-for-geoevent.png?raw=true)

## Features
* Event Count by Category Processor

## Instructions

Building the source code:

1. Make sure Maven and ArcGIS GeoEvent Extension SDK are installed on your machine.
2. Run 'mvn install -Dcontact.address=[YourContactEmailAddress]'

Installing the built jar files:

1. Copy the *.jar files under the 'target' sub-folder(s) into the [ArcGIS-GeoEvent-Extension-Install-Directory]/deploy folder.

## Requirements

* ArcGIS GeoEvent Extension for Server.
* ArcGIS GeoEvent Extension SDK.
* Java JDK 1.7 or greater.
* Maven.

## Resources

* [ArcGIS GeoEvent Extension for Server Resources](http://links.esri.com/geoevent)
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

[](ArcGIS, GeoEvent, Processor)
[](Esri Tags: ArcGIS GeoEvent Extension for Server)
[](Esri Language: Java)
