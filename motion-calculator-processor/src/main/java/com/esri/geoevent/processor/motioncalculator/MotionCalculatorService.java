package com.esri.geoevent.processor.motioncalculator;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.processor.GeoEventProcessor;
import com.esri.ges.processor.GeoEventProcessorServiceBase;

public class MotionCalculatorService extends GeoEventProcessorServiceBase
{
  private Messaging messaging;

  public MotionCalculatorService()
  {
    definition = new MotionCalculatorDefinition();
  }

  @Override
  public GeoEventProcessor create() throws ComponentException
  {
    MotionCalculator detector = new MotionCalculator(definition);
    detector.setMessaging(messaging);
    return detector;
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
  }
}