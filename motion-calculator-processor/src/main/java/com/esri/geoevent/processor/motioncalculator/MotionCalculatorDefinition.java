package com.esri.geoevent.processor.motioncalculator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.esri.ges.core.property.PropertyDefinition;
import com.esri.ges.core.property.PropertyType;
import com.esri.ges.processor.GeoEventProcessorDefinitionBase;

public class MotionCalculatorDefinition extends GeoEventProcessorDefinitionBase
{
	final private static Log LOG = LogFactory.getLog(MotionCalculatorDefinition.class);

	public MotionCalculatorDefinition()
	{
		try
		{
      propertyDefinitions.put("distanceUnit", new PropertyDefinition("distanceUnit", PropertyType.String, "Kilometers",
          "Distance Unit", "Distance unit to be used", false, false, "Kilometers", "Miles", "Nautical Miles"));
      propertyDefinitions.put("geometryType", new PropertyDefinition("geometryType", PropertyType.String, "Kilometers",
          "Geometry Type", "The resulting Geometry Type", false, false, "Point", "Line"));
      
      propertyDefinitions.put("notificationMode", new PropertyDefinition("notificationMode", PropertyType.String,
          "OnChange", "Count Notification Mode", "Count Notification Mode", true, false, "OnChange",
          "Continuous"));
      propertyDefinitions.put("reportInterval", new PropertyDefinition("reportInterval", PropertyType.Long, 10,
          "Report Interval (seconds)", "Report Interval (seconds)", "notificationMode=Continuous", false, false)); 
      
			propertyDefinitions.put("autoResetCache", new PropertyDefinition("autoResetCache", PropertyType.Boolean, false, 
					"Automatic Reset Cache", "Auto Reset Cache", true, false));
			propertyDefinitions.put("resetTime", new PropertyDefinition("resetTime", PropertyType.String, "00:00:00",
					"Reset Cache at", "Reset Cache time", "autoResetCache=true", false, false));
      propertyDefinitions.put("clearCache", new PropertyDefinition("clearCache", PropertyType.Boolean, true, 
          "Clear in-memory Cache", "Clear in-memory Cache", "autoResetCache=true", false, false));

      propertyDefinitions.put("predictiveTimespan", new PropertyDefinition("predictiveTimespan", PropertyType.Integer, 10,
          "Predictive Timespan (seconds)", "Timespan in seconds to calculate the next position.", false, false));
      propertyDefinitions.put("predictiveGeometryType", new PropertyDefinition("predictiveGeometryType", PropertyType.String, "Kilometers",
          "Geometry Type", "The resulting Geometry Type", false, false, "Point", "Line"));

      propertyDefinitions.put("newGeoEventDefinitionName", new PropertyDefinition("newGeoEventDefinitionName", PropertyType.String, "MotionCalculatorDef", "Resulting GeoEvent Definition Name", "Resulting GeoEvent Definition Name", false, false));
      			
		} catch (Exception e)
		{
			LOG.error("Error setting up Motion Calculator Definitions.", e);
		}
	}

	@Override
	public String getName()
	{
		return "MotionCalculator";
	}

	@Override
	public String getLabel()
	{
		return "Motion Calculator";
	}

	@Override
	public String getDescription()
	{
		return "Calculate distance, speed, minimum, maximum, average values, and heading.";
	}
}