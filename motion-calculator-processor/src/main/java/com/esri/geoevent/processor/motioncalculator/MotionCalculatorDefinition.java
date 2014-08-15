package com.esri.geoevent.processor.motioncalculator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.esri.ges.core.geoevent.DefaultFieldDefinition;
import com.esri.ges.core.geoevent.DefaultGeoEventDefinition;
import com.esri.ges.core.geoevent.FieldDefinition;
import com.esri.ges.core.geoevent.FieldType;
import com.esri.ges.core.geoevent.GeoEventDefinition;
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
          "Distance Unit", "Distance unit to be used", false, false, "Kilometers", "Miles"));
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
					"Reset Cache at", "Reset Cache time", "autoResetCache=false", false, false));
      propertyDefinitions.put("clearCache", new PropertyDefinition("clearCache", PropertyType.Boolean, true, 
          "Clear in-memory Cache", "Clear in-memory Cache", "autoResetCache=true", false, false));
      
      propertyDefinitions.put("predictivePosition", new PropertyDefinition("predictivePosition", PropertyType.Boolean, false, 
          "Predictive Position", "Calculate predictive position based on timespan", true, false));
      propertyDefinitions.put("predictiveTimespan", new PropertyDefinition("predictiveTimespan", PropertyType.Integer, 10,
          "Predictive Timespan (seconds)", "Timespan in seconds to calculate the next position.", "predictivePosition=true", false, false));
      propertyDefinitions.put("predictiveGeometryType", new PropertyDefinition("predictiveGeometryType", PropertyType.String, "Kilometers",
          "Geometry Type", "The resulting Geometry Type", "predictivePosition=true", false, false, "Point", "Line"));
      
			// TODO: How about TrackId selection to potentially track only a
			// subset of geoevents ???
			GeoEventDefinition gedMC = new DefaultGeoEventDefinition();
			gedMC.setName("MotionCalculator");
			List<FieldDefinition> fdsMC = new ArrayList<FieldDefinition>();
			fdsMC.add(new DefaultFieldDefinition("trackId", FieldType.String, "TRACK_ID"));
			fdsMC.add(new DefaultFieldDefinition("distance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("speed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("heading", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minTimespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxTimespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgTimespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minSpeed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxSpeed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgSpeed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minAcceleration", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxAcceleration", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgAcceleration", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("cumulativeDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("cumulativeTime", FieldType.Double));
			fdsMC.add(new DefaultFieldDefinition("calculatedAt", FieldType.Date));
			fdsMC.add(new DefaultFieldDefinition("geometry", FieldType.Geometry, "GEOMETRY"));
      fdsMC.add(new DefaultFieldDefinition("predictiveTime", FieldType.Date));
      fdsMC.add(new DefaultFieldDefinition("predictivePosition", FieldType.Geometry, "GEOMETRY"));
			gedMC.setFieldDefinitions(fdsMC);
			geoEventDefinitions.put(gedMC.getName(), gedMC);
			
			/*
      GeoEventDefinition gedPM = new DefaultGeoEventDefinition();
      gedMC.setName("PredictiveMotion");
      List<FieldDefinition> fdsPM = new ArrayList<FieldDefinition>();
      fdsPM.add(new DefaultFieldDefinition("trackId", FieldType.String, "TRACK_ID"));
      fdsPM.add(new DefaultFieldDefinition("distance", FieldType.Double));
      fdsPM.add(new DefaultFieldDefinition("speed", FieldType.Double));
      fdsPM.add(new DefaultFieldDefinition("heading", FieldType.Double));
      fdsPM.add(new DefaultFieldDefinition("calculatedAt", FieldType.Date));
      fdsPM.add(new DefaultFieldDefinition("geometry", FieldType.Geometry, "GEOMETRY"));
      fdsPM.add(new DefaultFieldDefinition("predictiveTime", FieldType.Date));
      fdsPM.add(new DefaultFieldDefinition("predictivePosition", FieldType.Geometry, "GEOMETRY"));
      fdsPM.add(new DefaultFieldDefinition("predictivePath", FieldType.Geometry, "GEOMETRY"));
      gedPM.setFieldDefinitions(fdsPM);
      geoEventDefinitions.put(gedPM.getName(), gedPM);
      */
			
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