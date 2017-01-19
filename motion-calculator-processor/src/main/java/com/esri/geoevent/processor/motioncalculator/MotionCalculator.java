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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.MapGeometry;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SpatialReference;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.DefaultFieldDefinition;
import com.esri.ges.core.geoevent.FieldDefinition;
import com.esri.ges.core.geoevent.FieldType;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventDefinition;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.validation.ValidationException;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManager;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManagerException;
import com.esri.ges.messaging.EventDestination;
import com.esri.ges.messaging.EventUpdatable;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.GeoEventProducer;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.processor.GeoEventProcessorBase;
import com.esri.ges.processor.GeoEventProcessorDefinition;
import com.esri.ges.util.Converter;
import com.esri.ges.util.Validator;

public class MotionCalculator extends GeoEventProcessorBase implements GeoEventProducer, EventUpdatable
{
  private static final BundleLogger         LOGGER              = BundleLoggerFactory.getLogger(MotionCalculator.class);

  private MotionCalculatorNotificationMode  notificationMode;
  private long                              reportInterval;

  private final Map<String, MotionElements> motionElementsCache = new ConcurrentHashMap<String, MotionElements>();

  private Messaging                         messaging;
  private GeoEventCreator                   geoEventCreator;
  private GeoEventProducer                  geoEventProducer;

  private String                            distanceUnit;
  private String                            geometryType;
  private String                            predictiveGeometryType;
  private Integer                           predictiveTimespan;
  private Date                              resetTime;
  private boolean                           autoResetCache;
  private Timer                             clearCacheTimer;
  private boolean                           clearCache;
  private boolean                           isReporting         = false;
  private GeoEventDefinitionManager         geoEventDefinitionManager;
  private Map<String, String>               edMapper            = new ConcurrentHashMap<String, String>();
  private String                            newGeoEventDefinitionName;

  final Object                              lock1               = new Object();

  class MotionElements
  {
    private GeoEvent previousGeoEvent;
    private GeoEvent currentGeoEvent;
    private String   id;
    private Geometry lineGeometry;
   
    // distance defaulted to KMs, but may change to miles based on the distance unit
    private Double   distance              = 0.0;             
    private Double   height                = 0.0;
    private Double   slope                 = 0.0;
    private Double   timespanSeconds       = 0.0;
    private Double   speed                 = 0.0;

    // distances per second^2
    private Double   acceleration          = 0.0;             
    private Double   headingDegrees        = 0.0;
    private Double   cumulativeDistance    = 0.0;
    private Double   cumulativeHeight      = 0.0;
    private Double   cumulativeTimeSeconds = 0.0;
    private Double   minDistance           = Double.MAX_VALUE;
    private Double   maxDistance           = Double.MIN_VALUE;
    private Double   avgDistance           = 0.0;
    private Double   minHeight             = Double.MAX_VALUE;
    private Double   maxHeight             = Double.MIN_VALUE;
    private Double   avgHeight             = 0.0;
    private Double   minSpeed              = Double.MAX_VALUE;
    private Double   maxSpeed              = Double.MIN_VALUE;
    private Double   avgSpeed              = 0.0;
    private Double   minAcceleration       = Double.MAX_VALUE;
    private Double   maxAcceleration       = Double.MIN_VALUE;
    private Double   avgAcceleration       = 0.0;
    private Double   minTimespan           = Double.MAX_VALUE;
    private Double   maxTimespan           = Double.MIN_VALUE;
    private Double   avgTimespan           = 0.0;
    private Double   minSlope              = Double.MAX_VALUE;
    private Double   maxSlope              = Double.MIN_VALUE;
    private Double   avgSlope              = 0.0;
    private Long     count                 = 0L;
    private Date     predictiveTime;

    public MotionElements(GeoEvent geoevent)
    {
      this.currentGeoEvent = geoevent;
      LOGGER.debug("MotionElements");
      LOGGER.debug(geoevent.toString());
    }

    public boolean setGeoEvent(GeoEvent geoevent)
    {
      LOGGER.debug("setGeoEvent");
      LOGGER.debug(geoevent.toString());
      // Check if the time stamp of the incoming geoevent is out of temporal
      // order then don't replace the this.currentGeoEvent
      Long timespanMilliSecs = 0L;
      timespanMilliSecs = geoevent.getStartTime().getTime() - getCurrentGeoEvent().getStartTime().getTime();
      if (timespanMilliSecs < 0)
      {
        // Don't set to the currentGeoEvent if the incoming geoevent is older
        // than the currentGeoEvent
        return false;
      }

      this.previousGeoEvent = this.getCurrentGeoEvent();
      this.currentGeoEvent = geoevent;
      return true;
    }

    public Long getCount()
    {
      return count;
    }

    public Double getCumulativeDistance()
    {
      return cumulativeDistance;
    }

    public Double getCumulativeHeight()
    {
      return cumulativeHeight;
    }

    public Double getCumulativeTime()
    {
      return cumulativeTimeSeconds;
    }

    public Geometry getGeometry()
    {
      if (geometryType.equals("Point"))
      {
        // returns the original geometry -- don't care for type for now
        return this.getCurrentGeoEvent().getGeometry().getGeometry();
      }
      else
      {
        return lineGeometry;
      }
    }

    public void computeTimespan()
    {
      Long timespanMilliSecs = 0L;
      timespanMilliSecs = getCurrentGeoEvent().getStartTime().getTime() - getPreviousGeoEvent().getStartTime().getTime();
      /*
       * wrongTemporalOrder = (timespanMilliSecs < 0); if (wrongTemporalOrder) {
       * timespanMilliSecs = Math.abs(timespanMilliSecs); }
       */
      timespanSeconds = timespanMilliSecs / 1000.0;
      if (timespanSeconds == 0.0)
      {
        // set to very small value to avoid divisor is 0
        timespanSeconds = 0.0000000001; 
      }
      if (minTimespan > timespanSeconds)
      {
        minTimespan = timespanSeconds;
      }
      if (maxTimespan < timespanSeconds)
      {
        maxTimespan = timespanSeconds;
      }
      cumulativeTimeSeconds = cumulativeTimeSeconds + timespanSeconds;
      if (count > 0)
      {
        avgTimespan = cumulativeTimeSeconds / count;
      }
      else
      {
        avgTimespan = cumulativeTimeSeconds;
      }
    }

    public void calculateAndSendReport()
    {
      if (this.previousGeoEvent == null)
      {
        return;
      }
      count++;
      // Need to compute timespan first
      computeTimespan();

      Point from = (Point) getPreviousGeoEvent().getGeometry().getGeometry();
      Point to = (Point) getCurrentGeoEvent().getGeometry().getGeometry();
      // distance = halversineDistance(from.getX(), from.getY(), to.getX(), to.getY());
      distance = lawOfCosineDistance(from.getX(), from.getY(), to.getX(), to.getY());
      // assuming Z unit is the same as domain as distance unit, e.g. KM-Meter, Miles-feet
      height = to.getZ() - from.getZ(); 
      /*
       * if (wrongTemporalOrder) { distance *= -1.0; height *= -1.0; }
       */
       
       if (distance == 0.0)
      {
        // Adding same safe guard as with timespan to avoid dividing by 0 but for when only height values have changed
        distance = 0.0000000001; 
      }
      
      slope = height / (distance * 1000.0); // make KM distance into meters

      if (distanceUnit.compareTo("Miles") == 0)
      {
        this.distance *= 0.621371; // Convert KMs to Miles -- will affect all the subsequent calculations
        slope = height / (distance * 5280.0); // make mile distance into feet
      }
      else if (distanceUnit.compareTo("Nautical Miles") == 0)
      {
        this.distance *= 0.539957; // Convert KMs to Nautical Miles
        slope = height / (distance * 6076.12); // make nautical mile distance into feet
      }

      Double timespanHours = timespanSeconds / (3600.0);
      Double newSpeed = distance / timespanHours;
      acceleration = (newSpeed - speed) / timespanHours;
      speed = newSpeed;

      if (minDistance > distance)
      {
        minDistance = distance;
      }
      if (maxDistance < distance)
      {
        maxDistance = distance;
      }

      if (minHeight > height)
      {
        minHeight = height;
      }
      if (maxHeight < height)
      {
        maxHeight = height;
      }

      if (minSlope > slope)
      {
        minSlope = slope;
      }
      if (maxSlope < slope)
      {
        maxSlope = slope;
      }

      if (minSpeed > speed)
      {
        minSpeed = speed;
      }
      if (maxSpeed < speed)
      {
        maxSpeed = speed;
      }
      if (minAcceleration > acceleration)
      {
        minAcceleration = acceleration;
      }
      if (maxAcceleration < acceleration)
      {
        maxAcceleration = acceleration;
      }

      if (Double.isNaN(distance) == false)
      {
        cumulativeDistance += distance;
      }
      if (Double.isNaN(height) == false)
      {
        cumulativeHeight += height;
      }
      avgDistance = cumulativeDistance / count;
      avgHeight = cumulativeHeight / count;
      // avgSpeed = cumulativeDistance / (cumulativeTimeSeconds / 3600.0);
      avgSpeed = avgDistance / avgTimespan;
      avgAcceleration = avgSpeed / avgTimespan;

      /*
       * if (wrongTemporalOrder) { headingDegrees = heading(to.getX(),
       * to.getY(), from.getX(), from.getY()); } else
       */
      {
        headingDegrees = heading(from.getX(), from.getY(), to.getX(), to.getY());
      }

      Polyline polyline = new Polyline();
      /*
       * if (wrongTemporalOrder) { polyline.startPath(to.getX(), to.getY());
       * polyline.lineTo(from.getX(), from.getY()); } else
       */
      {
        polyline.startPath(from.getX(), from.getY());
        polyline.lineTo(to.getX(), to.getY());
      }
      this.lineGeometry = polyline;

      sendReport();
    }

    private void sendReport()
    {
      if (notificationMode != MotionCalculatorNotificationMode.OnChange)
      {
        return;
      }

      LOGGER.debug("sendReport");

      try
      {
        GeoEvent outGeoEvent = createMotionGeoEvent();
        if (outGeoEvent == null)
        {
          LOGGER.debug("outGeoEvent is null");
          return;
        }
        LOGGER.debug(outGeoEvent.toString());
        send(outGeoEvent);
      }
      catch (MessagingException e)
      {
        LOGGER.error("Error sending update GeoEvent for " + id, e);
      }
    }

    private GeoEvent createMotionGeoEvent()
    {
      GeoEventDefinition edOut;
      GeoEvent geoEventOut = null;
      try
      {
        edOut = lookupAndCreateEnrichedDefinition(this.currentGeoEvent.getGeoEventDefinition());
        if (edOut == null)
        {
          LOGGER.debug("edOut is null");
          return null;
        }
        geoEventOut = geoEventCreator.create(edOut.getGuid(), new Object[] { getCurrentGeoEvent().getAllFields(), createMotionGeoEventFields(currentGeoEvent.getTrackId(), this) });
        if (geometryType.equals("Line"))
        {
          geoEventOut.setGeometry(new MapGeometry(this.lineGeometry, SpatialReference.create(4326)));
        }

        // need to use "event" instead of "message" otherwise the resulting GeoEvent will come back in the process() method
        geoEventOut.setProperty(GeoEventPropertyName.TYPE, "event"); 
        geoEventOut.setProperty(GeoEventPropertyName.OWNER_ID, getId());
        geoEventOut.setProperty(GeoEventPropertyName.OWNER_URI, definition.getUri());
        for (Map.Entry<GeoEventPropertyName, Object> property : getCurrentGeoEvent().getProperties())
        {
          if (!geoEventOut.hasProperty(property.getKey()))
          {
            geoEventOut.setProperty(property.getKey(), property.getValue());
          }
        }
      }
      catch (Exception error)
      {
        LOGGER.error("CREATE_GEOEVENT_FAILED", error.getMessage());
        LOGGER.info(error.getMessage(), error);
      }
      return geoEventOut;
    }

    public Date getTimestamp()
    {
      // Should this be the timestamp of the incoming geoevent or the calculated time?
      return getCurrentGeoEvent().getStartTime();
    }

    public Double getDistance()
    {
      return distance;
    }

    public Double getTimespanSeconds()
    {
      return timespanSeconds;
    }

    public Double getSpeed()
    {
      return speed;
    }

    public Double getHeadingDegrees()
    {
      return headingDegrees;
    }

    public Double getMinDistance()
    {
      return minDistance;
    }

    public Double getMaxDistance()
    {
      return maxDistance;
    }

    public Double getAvgDistance()
    {
      return avgDistance;
    }

    public Double getMinSpeed()
    {
      return minSpeed;
    }

    public Double getMaxSpeed()
    {
      return maxSpeed;
    }

    public Double getAvgSpeed()
    {
      return avgSpeed;
    }

    public Double getMinTime()
    {
      return minTimespan;
    }

    public Double getAvgTime()
    {
      return avgTimespan;
    }

    public Double getMaxTime()
    {
      return maxTimespan;
    }

    public Double getMinAcceleration()
    {
      return minAcceleration;
    }

    public Double getAvgAcceleration()
    {
      return avgAcceleration;
    }

    public Double getMaxAcceleration()
    {
      return maxAcceleration;
    }

    public Double getAcceleration()
    {
      return acceleration;
    }

    public Date getPredictiveTime()
    {
      Long timespan = getCurrentGeoEvent().getStartTime().getTime() + (predictiveTimespan * 1000);
      Date pt = new Date();
      pt.setTime(timespan);
      predictiveTime = pt;
      return predictiveTime;
    }

    public MapGeometry getPredictiveGeometry()
    {
      final Double R = 6356752.3142 / 1000.0; // Radious of the earth in kms
      double earthRadius = R;

      double predictiveDistance = speed * (predictiveTimespan / 3600.0); // convert seconds to hours

      if ("miles".equalsIgnoreCase(distanceUnit))
      {
        predictiveDistance *= 0.621371; // Convert KMs to Miles
        earthRadius *= 0.621371;
      }
      if ("nautical miles".equalsIgnoreCase(distanceUnit))
      {
        predictiveDistance *= 0.539957; // Convert KMs to Nautical Miles
        earthRadius *= 0.539957;
      }

      if (notificationMode == MotionCalculatorNotificationMode.Continuous)
      {
        LOGGER.debug("continuous prediction");
        Date currentDate = new Date();
        double timespanToCurrentTime = (currentDate.getTime() - getCurrentGeoEvent().getStartTime().getTime()) / 1000.0; // convert to seconds
        predictiveDistance = speed * (timespanToCurrentTime / 3600.0); // seconds to hours
      }

      double distRatio = predictiveDistance / earthRadius;
      double distRatioSine = Math.sin(distRatio);
      double distRatioCosine = Math.cos(distRatio);

      Point currentPoint = (Point) getCurrentGeoEvent().getGeometry().getGeometry();
      double startLonRad = toRadians(currentPoint.getX());
      double startLatRad = toRadians(currentPoint.getY());

      double startLatCos = Math.cos(startLatRad);
      double startLatSin = Math.sin(startLatRad);

      double endLatRads = Math.asin((startLatSin * distRatioCosine) + (startLatCos * distRatioSine * Math.cos(toRadians(headingDegrees))));
      double endLonRads = startLonRad + Math.atan2(Math.sin(toRadians(headingDegrees)) * distRatioSine * startLatCos, distRatioCosine - startLatSin * Math.sin(endLatRads));

      double newLat = toDegrees(endLatRads);
      double newLong = toDegrees(endLonRads);

      if ("point".equalsIgnoreCase(predictiveGeometryType))
      {
        Point point = new Point(newLong, newLat, currentPoint.getZ());
        return new MapGeometry(point, SpatialReference.create(4326));
      }
      else
      {
        Polyline polyline = new Polyline();
        polyline.startPath(new Point(currentPoint.getX(), currentPoint.getY(), currentPoint.getZ()));
        polyline.lineTo(new Point(newLong, newLat, currentPoint.getZ())); // TODO: calculate new Z from Slope
        return new MapGeometry(polyline, SpatialReference.create(4326));
      }
    }

    public GeoEvent getPreviousGeoEvent()
    {
      return previousGeoEvent;
    }

    public GeoEvent getCurrentGeoEvent()
    {
      return currentGeoEvent;
    }

    public Double getSlope()
    {
      return slope;
    }

    public void setSlope(Double slope)
    {
      this.slope = slope;
    }

    public Double getMinSlope()
    {
      return minSlope;
    }

    public void setMinSlope(Double minSlope)
    {
      this.minSlope = minSlope;
    }

    public Double getMaxSlope()
    {
      return maxSlope;
    }

    public void setMaxSlope(Double maxSlope)
    {
      this.maxSlope = maxSlope;
    }

    public Double getAvgSlope()
    {
      return avgSlope;
    }

    public void setAvgSlope(Double avgSlope)
    {
      this.avgSlope = avgSlope;
    }

    public Double getHeight()
    {
      return height;
    }

    public Double getMinHeight()
    {
      return minHeight;
    }

    public Double getAvgHeight()
    {
      return avgHeight;
    }

    public Double getMaxHeight()
    {
      return maxHeight;
    }
  }

  class ClearCacheTask extends TimerTask
  {
    public void run()
    {
      if (autoResetCache == true)
      {
        // clear the cache
        if (clearCache == true)
        {
          motionElementsCache.clear();
        }
      }
    }
  }

  class ReportGenerator implements Runnable
  {
    private Long reportInterval = 5000L;

    public ReportGenerator(Long reportInterval)
    {
      this.reportInterval = reportInterval;
    }

    @Override
    public void run()
    {
      while (isReporting)
      {
        try
        {
          Thread.sleep(reportInterval);
          if (notificationMode != MotionCalculatorNotificationMode.Continuous)
          {
            continue;
          }

          for (String trackId : motionElementsCache.keySet())
          {
            MotionElements motionEle = motionElementsCache.get(trackId);
            GeoEvent outGeoEvent = null;
            try
            {
              outGeoEvent = motionEle.createMotionGeoEvent();
              if (outGeoEvent == null)
              {
                LOGGER.debug("outGeoEvent is null");
                continue;
              }
              LOGGER.debug("send");
              LOGGER.debug(outGeoEvent.toString());
              send(outGeoEvent);
            }
            catch (MessagingException error)
            {
              LOGGER.error("SEND_ERROR", outGeoEvent, error.getMessage());
              LOGGER.info(error.getMessage(), error);
            }
          }
        }
        catch (InterruptedException error)
        {
          LOGGER.error(error.getMessage(), error);
        }
      }
    }
  }

  protected MotionCalculator(GeoEventProcessorDefinition definition) throws ComponentException
  {
    super(definition);
  }

  public void afterPropertiesSet()
  {
    newGeoEventDefinitionName = getProperty("newGeoEventDefinitionName").getValueAsString();
    distanceUnit = getProperty("distanceUnit").getValueAsString();
    geometryType = getProperty("geometryType").getValueAsString();
    notificationMode = Validator.valueOfIgnoreCase(MotionCalculatorNotificationMode.class, getProperty("notificationMode").getValueAsString(), MotionCalculatorNotificationMode.OnChange);
    reportInterval = Converter.convertToInteger(getProperty("reportInterval").getValueAsString(), 10) * 1000;
    autoResetCache = Converter.convertToBoolean(getProperty("autoResetCache").getValueAsString());
    clearCache = Converter.convertToBoolean(getProperty("clearCache").getValueAsString());

    predictiveGeometryType = getProperty("predictiveGeometryType").getValueAsString();
  // kept conversion to Int but removed multiplier. Time values were already in milliseconds
    predictiveTimespan = Converter.convertToInteger(getProperty("predictiveTimespan").getValueAsString(), 10) ;

    String[] resetTimeStr = getProperty("resetTime").getValueAsString().split(":");
    // Get the Date corresponding to 11:01:00 pm today.
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(resetTimeStr[0]));
    calendar.set(Calendar.MINUTE, Integer.parseInt(resetTimeStr[1]));
    calendar.set(Calendar.SECOND, Integer.parseInt(resetTimeStr[2]));
    resetTime = calendar.getTime();
  }

  @Override
  public void setId(String id)
  {
    super.setId(id);
    geoEventProducer = messaging.createGeoEventProducer(new EventDestination(id + ":event"));
  }

  @Override
  public GeoEvent process(GeoEvent geoevent) throws Exception
  {
    String trackId = geoevent.getTrackId();
    MotionElements motionEle;
    if (motionElementsCache.containsKey(trackId) == false)
    {
      motionEle = new MotionElements(geoevent);
    }
    else
    {
      motionEle = motionElementsCache.get(trackId);
      boolean correctTemporalOrder = motionEle.setGeoEvent(geoevent);
      // Only compute and send report if the temporal order is correct
      if (correctTemporalOrder)
      {
        motionEle.calculateAndSendReport();
      }
      else
      {
        LOGGER.error("Wrong temporal order detected: " + geoevent.toString());
      }
    }
    // Need to synchronize the Concurrent Map on write to avoid wrong counting
    synchronized (lock1)
    {
      motionElementsCache.put(trackId, motionEle);
    }

    return null;
  }

  @Override
  public List<EventDestination> getEventDestinations()
  {
    return (geoEventProducer != null) ? Arrays.asList(geoEventProducer.getEventDestination()) : new ArrayList<EventDestination>();
  }

  @Override
  public void validate() throws ValidationException
  {
    super.validate();
    List<String> errors = new ArrayList<String>();
    if (reportInterval <= 0)
      errors.add(LOGGER.translate("VALIDATION_INVALID_REPORT_INTERVAL", definition.getName()));
    if (errors.size() > 0)
    {
      StringBuffer sb = new StringBuffer();
      for (String message : errors)
        sb.append(message).append("\n");
      throw new ValidationException(LOGGER.translate("VALIDATION_ERROR", this.getClass().getName(), sb.toString()));
    }
  }

  @Override
  public void onServiceStart()
  {
    if (this.autoResetCache == true || this.clearCache == true)
    {
      if (clearCacheTimer == null)
      {
        // Get the Date corresponding to 11:01:00 pm today.
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(resetTime);
        Date time1 = calendar1.getTime();

        clearCacheTimer = new Timer();
        Long dayInMilliSeconds = 60 * 60 * 24 * 1000L;
        clearCacheTimer.scheduleAtFixedRate(new ClearCacheTask(), time1, dayInMilliSeconds);
      }
      motionElementsCache.clear();
    }

    isReporting = true;

    ReportGenerator reportGen = new ReportGenerator(reportInterval);
    Thread thread = new Thread(reportGen);
    thread.setName("MotionCalculator Report Generator");
    thread.start();
  }

  @Override
  public void onServiceStop()
  {
    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
    isReporting = false;
  }

  @Override
  public void shutdown()
  {
    super.shutdown();

    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
    clearGeoEventDefinitionMapper();
  }

  @Override
  public EventDestination getEventDestination()
  {
    return (geoEventProducer != null) ? geoEventProducer.getEventDestination() : null;
  }

  @Override
  public void send(GeoEvent geoEvent) throws MessagingException
  {
    if (geoEventProducer != null && geoEvent != null)
      geoEventProducer.send(geoEvent);
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
    geoEventCreator = messaging.createGeoEventCreator();
  }

  public void setGeoEventDefinitionManager(GeoEventDefinitionManager geoEventDefinitionManager)
  {
    this.geoEventDefinitionManager = geoEventDefinitionManager;
  }

  @Override
  public void disconnect()
  {
    if (geoEventProducer != null)
      geoEventProducer.disconnect();
  }

  @Override
  public String getStatusDetails()
  {
    return (geoEventProducer != null) ? geoEventProducer.getStatusDetails() : "";
  }

  @Override
  public void init() throws MessagingException
  {
    afterPropertiesSet();
  }

  @Override
  public boolean isConnected()
  {
    return (geoEventProducer != null) ? geoEventProducer.isConnected() : false;
  }

  @Override
  public void setup() throws MessagingException
  {
    ;
  }

  @Override
  public void update(Observable o, Object arg)
  {
    ;
  }

  private List<FieldDefinition> createFieldDefinitionList()
  {
    List<FieldDefinition> fdsMC = new ArrayList<FieldDefinition>();
    try
    {
      fdsMC.add(new DefaultFieldDefinition("distance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("height", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("timespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("speed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("heading", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("slope", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minTimespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxTimespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgTimespan", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minHeight", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxHeight", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgHeight", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minSpeed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxSpeed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgSpeed", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minAcceleration", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxAcceleration", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgAcceleration", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("minSlope", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("maxSlope", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("avgSlope", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("cumulativeDistance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("cumulativeHeight", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("cumulativeTime", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("calculatedAt", FieldType.Date));
      fdsMC.add(new DefaultFieldDefinition("predictiveTime", FieldType.Date));
      fdsMC.add(new DefaultFieldDefinition("predictivePosition", FieldType.Geometry));
    }
    catch (Exception e)
    {

    }
    return fdsMC;
  }

  private Object[] createMotionGeoEventFields(String trackId, MotionElements motionElements)
  {
    List<Object> motionFieldList = new ArrayList<Object>();
    motionFieldList.add(motionElements.getDistance());
    motionFieldList.add(motionElements.getHeight());
    motionFieldList.add(motionElements.getTimespanSeconds());
    motionFieldList.add(motionElements.getSpeed());
    motionFieldList.add(motionElements.getHeadingDegrees());
    motionFieldList.add(motionElements.getSlope());
    motionFieldList.add(motionElements.getMinTime());
    motionFieldList.add(motionElements.getMaxTime());
    motionFieldList.add(motionElements.getAvgTime());
    motionFieldList.add(motionElements.getMinDistance());
    motionFieldList.add(motionElements.getMaxDistance());
    motionFieldList.add(motionElements.getAvgDistance());
    motionFieldList.add(motionElements.getMinHeight());
    motionFieldList.add(motionElements.getMaxHeight());
    motionFieldList.add(motionElements.getAvgHeight());
    motionFieldList.add(motionElements.getMinSpeed());
    motionFieldList.add(motionElements.getMaxSpeed());
    motionFieldList.add(motionElements.getAvgSpeed());
    motionFieldList.add(motionElements.getMinAcceleration());
    motionFieldList.add(motionElements.getMaxAcceleration());
    motionFieldList.add(motionElements.getAvgAcceleration());
    motionFieldList.add(motionElements.getMinSlope());
    motionFieldList.add(motionElements.getMaxSlope());
    motionFieldList.add(motionElements.getAvgSlope());
    motionFieldList.add(motionElements.getCumulativeDistance());
    motionFieldList.add(motionElements.getCumulativeHeight());
    motionFieldList.add(motionElements.getCumulativeTime());
    motionFieldList.add(motionElements.getTimestamp());
    motionFieldList.add(motionElements.getPredictiveTime());
    motionFieldList.add(motionElements.getPredictiveGeometry());

    return motionFieldList.toArray();
  }

  synchronized private GeoEventDefinition lookupAndCreateEnrichedDefinition(GeoEventDefinition edIn) throws Exception
  {
    if (edIn == null)
    {
      LOGGER.debug("edIn is null");
      return null;
    }
    GeoEventDefinition edOut = edMapper.containsKey(edIn.getGuid()) ? geoEventDefinitionManager.getGeoEventDefinition(edMapper.get(edIn.getGuid())) : null;
    if (edOut == null)
    {
      edOut = edIn.augment(createFieldDefinitionList());
      edOut.setName(newGeoEventDefinitionName);
      edOut.setOwner(getId());
      geoEventDefinitionManager.addTemporaryGeoEventDefinition(edOut, newGeoEventDefinitionName.isEmpty());
      edMapper.put(edIn.getGuid(), edOut.getGuid());
    }
    return edOut;
  }

  synchronized private void clearGeoEventDefinitionMapper()
  {
    if (!edMapper.isEmpty())
    {
      for (String guid : edMapper.values())
      {
        try
        {
          geoEventDefinitionManager.deleteGeoEventDefinition(guid);
        }
        catch (GeoEventDefinitionManagerException e)
        {
          ;
        }
      }
      edMapper.clear();
    }
  }

  /*
   * Returns distance in KMs.
   */
  private static Double lawOfCosineDistance(Double lon1, Double lat1, Double lon2, Double lat2)
  {
    final Double R = 6356752.3142 / 1000.0; // Radious of the earth in kms
    Double radLon1 = toRadians(lon1);
    Double radLat1 = toRadians(lat1);
    Double radLon2 = toRadians(lon2);
    Double radLat2 = toRadians(lat2);

    return Math.acos(Math.sin(radLat1) * Math.sin(radLat2) + Math.cos(radLat1) * Math.cos(radLat2) * Math.cos(radLon2 - radLon1)) * R;
  }

  /*
   * This is the implementation Haversine Distance Algorithm between two
   * locations R = earth’s radius (mean radius = 6,371km) Δlat = lat2− lat1
   * Δlong = long2− long1 a = sin²(Δlat/2) + cos(lat1).cos(lat2).sin²(Δlong/2) c
   * = 2.atan2(√a, √(1−a)) d = R.c
   * 
   * Returns distance in KMs.
   */
  @SuppressWarnings("unused")
  private static Double halversineDistance(Double lon1, Double lat1, Double lon2, Double lat2)
  {
    final Double R = 6356752.3142 / 1000.0; // Radious of the earth in kms
    Double latDistance = toRadians(lat2 - lat1);
    Double lonDistance = toRadians(lon2 - lon1);
    Double a = Math.sin(latDistance / 2.0) * Math.sin(latDistance / 2.0) + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(lonDistance / 2.0) * Math.sin(lonDistance / 2.0);
    Double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    Double distance = R * c;
    return distance;
  }

  /*
   * Returns heading in degrees
   */
  private static Double heading(Double lon1, Double lat1, Double lon2, Double lat2)
  {
    Double radLon1 = toRadians(lon1);
    Double radLat1 = toRadians(lat1);
    Double radLon2 = toRadians(lon2);
    Double radLat2 = toRadians(lat2);
    Double y = Math.sin(radLon2 - radLon1) * Math.cos(radLat2);
    Double x = Math.cos(radLat1) * Math.sin(radLat2) - Math.sin(radLat1) * Math.cos(radLat2) * Math.cos(radLon2 - radLon1);
    /*
     * Without using Math.atan2() Double headingDegrees = 0.0; if (y > 0) { if
     * (x > 0) { headingDegrees = toDegrees(Math.atan(y/x)); } if (x < 0) {
     * headingDegrees = 180.0 - toDegrees(Math.atan(-y/x)); } if (x == 0){
     * headingDegrees = 90.0; } } if (y < 0) { if (x > 0) { headingDegrees =
     * toDegrees(-Math.atan(-y/x));} if (x < 0) { headingDegrees =
     * toDegrees(Math.atan(y/x))-180.0; } if (x == 0){ headingDegrees = 270.0; }
     * } if (y == 0) { if (x > 0) { headingDegrees = 0.0; } if (x < 0) {
     * headingDegrees = 180.0; } if (x == 0){ headingDegrees = Double.NaN; }
     * //the 2 points are the same }
     */
    Double headingDegrees = toDegrees(Math.atan2(y, x) % (2.0 * Math.PI));
    return headingDegrees;
  }

  private static Double toRadians(Double value)
  {
    return value * Math.PI / 180.0;
  }

  private static Double toDegrees(Double value)
  {
    return value * 180.0 / Math.PI;
  }
}
