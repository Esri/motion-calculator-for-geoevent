/*
  Copyright 1995-2016 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
*/

package com.esri.geoevent.processor.motioncalculator;

import java.util.ArrayList;
import java.util.List;

import com.esri.ges.core.property.LabeledValue;
import com.esri.ges.core.property.PropertyDefinition;
import com.esri.ges.core.property.PropertyType;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.processor.GeoEventProcessorDefinitionBase;

public class MotionCalculatorDefinition extends GeoEventProcessorDefinitionBase
{
  private static final BundleLogger LOGGER = BundleLoggerFactory.getLogger(MotionCalculatorDefinition.class);

  public MotionCalculatorDefinition()
  {
    try
    {
      List<LabeledValue> distanceUnitsAllowedValues = new ArrayList<>();
      distanceUnitsAllowedValues.add(new LabeledValue("Kilometers", "Kilometers"));
      distanceUnitsAllowedValues.add(new LabeledValue("Miles", "Miles"));
      distanceUnitsAllowedValues.add(new LabeledValue("Nautical Miles", "Nautical Miles"));
      propertyDefinitions.put("distanceUnit", new PropertyDefinition("distanceUnit", PropertyType.String, "Kilometers", "Distance Unit", "Distance unit to be used", false, false, distanceUnitsAllowedValues));

      List<LabeledValue> geometryTypeAllowedValues = new ArrayList<>();
      geometryTypeAllowedValues.add(new LabeledValue("Point", "Point"));
      geometryTypeAllowedValues.add(new LabeledValue("Line", "Line"));
      propertyDefinitions.put("geometryType", new PropertyDefinition("geometryType", PropertyType.String, "Kilometers", "Geometry Type", "The resulting Geometry Type", false, false, geometryTypeAllowedValues));

      List<LabeledValue> notificationModeAllowedValues = new ArrayList<>();
      notificationModeAllowedValues.add(new LabeledValue("OnChange", "OnChange"));
      notificationModeAllowedValues.add(new LabeledValue("Continuous", "Continuous"));
      propertyDefinitions.put("notificationMode", new PropertyDefinition("notificationMode", PropertyType.String, "OnChange", "Count Notification Mode", "Count Notification Mode", true, false, notificationModeAllowedValues));

      propertyDefinitions.put("reportInterval", new PropertyDefinition("reportInterval", PropertyType.Long, 10, "Report Interval (seconds)", "Report Interval (seconds)", "notificationMode=Continuous", false, false));
      propertyDefinitions.put("autoResetCache", new PropertyDefinition("autoResetCache", PropertyType.Boolean, false, "Automatic Reset Cache", "Auto Reset Cache", true, false));
      propertyDefinitions.put("resetTime", new PropertyDefinition("resetTime", PropertyType.String, "00:00:00", "Reset Cache at", "Reset Cache time", "autoResetCache=true", false, false));
      propertyDefinitions.put("clearCache", new PropertyDefinition("clearCache", PropertyType.Boolean, true, "Clear in-memory Cache", "Clear in-memory Cache", "autoResetCache=true", false, false));
      propertyDefinitions.put("predictiveTimespan", new PropertyDefinition("predictiveTimespan", PropertyType.Integer, 10, "Predictive Timespan (seconds)", "Timespan in seconds to calculate the next position.", false, false));

      List<LabeledValue> predictiveGeometryTypeAllowedValues = new ArrayList<>();
      predictiveGeometryTypeAllowedValues.add(new LabeledValue("Point", "Point"));
      predictiveGeometryTypeAllowedValues.add(new LabeledValue("Line", "Line"));
      propertyDefinitions.put("predictiveGeometryType", new PropertyDefinition("predictiveGeometryType", PropertyType.String, "Kilometers", "Geometry Type", "The resulting Geometry Type", false, false, predictiveGeometryTypeAllowedValues));
      propertyDefinitions.put("newGeoEventDefinitionName", new PropertyDefinition("newGeoEventDefinitionName", PropertyType.String, "MotionCalculatorDef", "Resulting GeoEvent Definition Name", "Resulting GeoEvent Definition Name", false, false));

    }
    catch (Exception error)
    {
      LOGGER.error("INIT_ERROR", error.getMessage());
      LOGGER.info(error.getMessage(), error);
    }
  }

  @Override
  public String getName()
  {
    return "MotionCalculator";
  }

  @Override
  public String getDomain()
  {
    return "com.esri.geoevent.processor";
  }

  @Override
  public String getVersion()
  {
    return "10.3.0";
  }

  @Override
  public String getLabel()
  {
    return "${com.esri.geoevent.processor.motion-calculator-processor.PROCESSOR_LABEL}";
  }

  @Override
  public String getDescription()
  {
    return "${com.esri.geoevent.processor.motion-calculator-processor.PROCESSOR_DESC}";
  }
}
