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
	private static final BundleLogger	LOGGER	= BundleLoggerFactory.getLogger(MotionCalculatorDefinition.class);

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
