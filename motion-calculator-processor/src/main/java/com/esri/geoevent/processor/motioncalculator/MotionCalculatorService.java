package com.esri.geoevent.processor.motioncalculator;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManager;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.processor.GeoEventProcessor;
import com.esri.ges.processor.GeoEventProcessorServiceBase;
import com.esri.ges.spatial.Spatial;

public class MotionCalculatorService extends GeoEventProcessorServiceBase
{
  private Messaging messaging;
  private Spatial spatial;
  private GeoEventDefinitionManager geoEventDefinitionManager;

  public MotionCalculatorService()
  {
    definition = new MotionCalculatorDefinition();
  }

  @Override
  public GeoEventProcessor create() throws ComponentException
  {
    MotionCalculator motionCalc = new MotionCalculator(definition);
    motionCalc.setMessaging(messaging);
    motionCalc.setSpatial(spatial);
    motionCalc.setGeoEventDefinitionManager(geoEventDefinitionManager);
    return motionCalc;
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
  }
  
  public void setSpatial(Spatial spatial)
  {
    this.spatial = spatial;
  }

  public void setGeoEventDefinitionManager(GeoEventDefinitionManager geoEventDefinitionManager)
  {
    this.geoEventDefinitionManager = geoEventDefinitionManager;
  }
}