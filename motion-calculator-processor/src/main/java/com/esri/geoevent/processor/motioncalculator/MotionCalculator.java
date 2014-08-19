package com.esri.geoevent.processor.motioncalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.esri.ges.core.Uri;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.DefaultFieldDefinition;
import com.esri.ges.core.geoevent.FieldDefinition;
import com.esri.ges.core.geoevent.FieldType;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventDefinition;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.validation.ValidationException;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManager;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManagerException;
import com.esri.ges.messaging.EventDestination;
import com.esri.ges.messaging.EventProducer;
import com.esri.ges.messaging.EventUpdatable;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.GeoEventProducer;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.processor.GeoEventProcessorBase;
import com.esri.ges.processor.GeoEventProcessorDefinition;
import com.esri.ges.spatial.Geometry;
import com.esri.ges.spatial.Point;
import com.esri.ges.spatial.Polyline;
import com.esri.ges.spatial.Spatial;
import com.esri.ges.util.Converter;
import com.esri.ges.util.Validator;

public class MotionCalculator extends GeoEventProcessorBase implements EventProducer, EventUpdatable
{
  private static final Log                  log                 = LogFactory.getLog(MotionCalculator.class);
  private Spatial                           spatial;

  private MotionCalculatorNotificationMode  notificationMode;
  private long                              reportInterval;

  private final Map<String, MotionElements> motionElementsCache = new ConcurrentHashMap<String, MotionElements>();

  private Messaging                         messaging;
  private GeoEventCreator                   geoEventCreator;
  private GeoEventProducer                  geoEventProducer;
  private EventDestination                  destination;

  private String                            distanceUnit;
  private String                            geometryType;
  //private boolean                           calcStat;
  //private boolean                           predictivePosition;
  private String                            predictiveGeometryType;
  private Integer                           predictiveTimespan;
  private Date                              resetTime;
  private boolean                           autoResetCache;
  private Timer                             clearCacheTimer;
  private boolean                           clearCache;
  private Uri                               definitionUri;
  private String                            definitionUriString;
  private boolean                           isReporting          = false;
  private String                            sourceGeoEventDefinitionGuid;
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
    private Double   distance              = 0.0; // distance defaulted to KMs,
                                                  // but may change to miles
                                                  // based on the distanceunit
    private Double   slope                 = 0.0;
    private Double   timespanSeconds       = 0.0;
    private Double   speed                 = 0.0;
    private Double   acceleration          = 0.0; // distances per second square
    private Double   headingDegrees        = 0.0;
    private Double   cumulativeDistance    = 0.0;
    private Double   cumulativeTimeSeconds = 0.0;
    private Double   minDistance           = Double.MAX_VALUE;
    private Double   maxDistance           = Double.MIN_VALUE;
    private Double   avgDistance           = 0.0;
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
    }

    public void setGeoEvent(GeoEvent geoevent)
    {
      this.previousGeoEvent = this.getCurrentGeoEvent();
      this.currentGeoEvent = geoevent;
    }
    
    public Long getCount()
    {
      return count;
    }

    public Double getCumulativeDistance()
    {
      return cumulativeDistance;
    }

    public Double getCumulativeTime()
    {
      return cumulativeTimeSeconds;
    }

    public Geometry getGeometry()
    {
      if (geometryType.equals("Point"))
      {
        //returns the original geometry -- don't care for type for now
        return this.getCurrentGeoEvent().getGeometry();
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
      timespanSeconds = timespanMilliSecs / 1000.0;
      if (timespanSeconds == 0.0)
      {
        timespanSeconds = 0.0000000001; // set to very small value to avoid
                                        // divisor is 0
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
      if (this.previousGeoEvent == null) {
        return;
      }
      count++;
      //Need to compute timespan first
      computeTimespan();
      
      Point from = (Point) getPreviousGeoEvent().getGeometry();
      Point to = (Point) getCurrentGeoEvent().getGeometry();
      // Double newDistance = halversineDistance(from.getX(), from.getY(),
      // to.getX(), to.getY());
      distance = lawOfCosineDistance(from.getX(), from.getY(), to.getX(), to.getY());
      Double dZ = to.getZ() - from.getZ();
      slope = dZ / distance;
      
      if (distanceUnit == "Miles")
      {
        this.distance *= 0.621371; // Convert KMs to Miles -- will affect all
                                 // subsequent calculation
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

      cumulativeDistance = cumulativeDistance + distance;
      avgDistance = cumulativeDistance / count;
      // avgSpeed = cumulativeDistance / (cumulativeTimeSeconds / 3600.0);
      avgSpeed = avgDistance / avgTimespan;
      avgAcceleration = avgSpeed / avgTimespan;

      headingDegrees = heading(from.getX(), from.getY(), to.getX(), to.getY());
      
      Polyline polyline = spatial.createPolyline();
      polyline.startPath(from.getX(), from.getY(), Double.NaN);
      polyline.lineTo(to.getX(), to.getY(), Double.NaN);
      
      this.lineGeometry = polyline;
      
      sendReport();
    }

    public String getId()
    {
      return getCurrentGeoEvent().getTrackId();
    }

    private void sendReport()
    {
      if (notificationMode != MotionCalculatorNotificationMode.OnChange)
      {
        return;
      }

      try
      {
        GeoEvent outGeoEvent = createMotionGeoEvent(); 
        send(outGeoEvent);
      }
      catch (MessagingException e)
      {
        log.error("Error sending update GeoEvent for " + id, e);
      }
    }

    private GeoEvent createMotionGeoEvent()
    {
      GeoEventDefinition edOut;
      GeoEvent geoEventOut = null;
      try
      {
        edOut = lookupAndCreateEnrichedDefinition();
        geoEventOut = geoEventCreator.create(edOut.getGuid(), new Object[] {getCurrentGeoEvent().getAllFields(), createMotionGeoEventFields(currentGeoEvent.getTrackId(), this)});
        geoEventOut.setProperty(GeoEventPropertyName.TYPE, "message");
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
      catch (Exception e1)
      {
        e1.printStackTrace();
      }
      return geoEventOut;
    }
    
    public Date getTimestamp()
    {
      //Should this be the timestamp of the incoming geoevent or the calculated time?
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

    public Geometry getPredictiveGeometry()
    {
      double predictiveDistance = speed * (predictiveTimespan/ 3600.0); // seconds to hours 
      
      double distRatioSine = Math.sin(predictiveDistance);
      double distRatioCosine = Math.cos(predictiveDistance);

      Point currentPoint = (Point)getCurrentGeoEvent().getGeometry();
      double startLonRad = toRadions(currentPoint.getX());
      double startLatRad = toRadions(currentPoint.getY());

      double startLatCos = Math.cos(startLatRad);
      double startLatSin = Math.sin(startLatRad);

      double endLatRads = Math.asin((startLatSin * distRatioCosine) + (startLatCos * distRatioSine * Math.cos(toRadions(headingDegrees))));
      double endLonRads = startLonRad + Math.atan2(Math.sin(toRadions(headingDegrees)) * distRatioSine * startLatCos,
              distRatioCosine - startLatSin * Math.sin(endLatRads));

      double newLat = toDegrees(endLatRads);
      double newLong = toDegrees(endLonRads);      

      if (predictiveGeometryType.equals("Point"))
      {
        return spatial.createPoint(newLong, newLat, currentPoint.getZ(), 4326);
      }
      else
      {
        Polyline polyline = spatial.createPolyline();
        polyline.startPath(currentPoint.getX(), currentPoint.getY(), currentPoint.getZ());
        polyline.lineTo(newLong, newLat, currentPoint.getZ()); //TODO: calculate new Z from Slope
        return polyline;
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
            try
            {

              send(motionEle.createMotionGeoEvent());
            }
            catch (MessagingException e)
            {
              log.error("Error sending update GeoEvent for " + trackId, e);
            }
          }
        }
        catch (InterruptedException e1)
        {
          log.error(e1);
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
    //calcStat = Converter.convertToBoolean(getProperty("calcStat").getValueAsString());
    newGeoEventDefinitionName = getProperty("newGeoEventDefinitionName").getValueAsString();
    distanceUnit = getProperty("distanceUnit").getValueAsString();
    geometryType = getProperty("geometryType").getValueAsString();
    notificationMode = Validator.validateEnum(MotionCalculatorNotificationMode.class, getProperty("notificationMode").getValueAsString(), MotionCalculatorNotificationMode.OnChange);
    reportInterval = Converter.convertToInteger(getProperty("reportInterval").getValueAsString(), 10) * 1000;
    autoResetCache = Converter.convertToBoolean(getProperty("autoResetCache").getValueAsString());
    //predictivePosition = Converter.convertToBoolean(getProperty("predictivePosition").getValueAsString());
    predictiveGeometryType = getProperty("predictiveGeometryType").getValueAsString();
    predictiveTimespan = Converter.convertToInteger(getProperty("predictiveTimespan").getValueAsString(), 10) * 1000; // convert
                                                                                                                      // to
                                                                                                                      // milliseconds

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
    destination = new EventDestination(getId() + ":event");
    geoEventProducer = messaging.createGeoEventProducer(destination.getName());
  }

  @Override
  public GeoEvent process(GeoEvent geoevent) throws Exception
  {
    sourceGeoEventDefinitionGuid = geoevent.getGeoEventDefinition().getGuid();

    String trackId = geoevent.getTrackId();
    MotionElements motionEle;
    if (motionElementsCache.containsKey(trackId) == false)
    {
      motionEle = new MotionElements(geoevent);
    }
    else
    {
      motionEle = motionElementsCache.get(trackId);
      motionEle.setGeoEvent(geoevent);
      motionEle.calculateAndSendReport();
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
    return Arrays.asList(destination);
  }

  @Override
  public void validate() throws ValidationException
  {
    super.validate();
    List<String> errors = new ArrayList<String>();
    if (reportInterval <= 0)
      errors.add("'" + definition.getName() + "' property 'reportInterval' is invalid.");
    if (errors.size() > 0)
    {
      StringBuffer sb = new StringBuffer();
      for (String message : errors)
        sb.append(message).append("\n");
      throw new ValidationException(this.getClass().getName() + " validation failed: " + sb.toString());
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
      // trackGeometryCache.clear();
      motionElementsCache.clear();
    }

    isReporting = true;
    if (definition != null)
    {
      definitionUri = definition.getUri();
      definitionUriString = definitionUri.toString();
    }

    ReportGenerator reportGen = new ReportGenerator(reportInterval);
    Thread t = new Thread(reportGen);
    t.setName("MotionCalculator Report Generator");
    t.start();
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
    return destination;
  }

  @Override
  public void send(GeoEvent geoEvent) throws MessagingException
  {
    // Try to get it again
    if (geoEventProducer == null)
    {
      destination = new EventDestination(getId() + ":event");
      geoEventProducer = messaging.createGeoEventProducer(destination.getName());
    }
    if (geoEventProducer != null && geoEvent != null)
    {
      geoEventProducer.send(geoEvent);
    }
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
    geoEventCreator = messaging.createGeoEventCreator();
  }

  public void setSpatial(Spatial spatial)
  {
    this.spatial = spatial;
  }

  public void setGeoEventDefinitionManager(GeoEventDefinitionManager geoEventDefinitionManager)
  {
    this.geoEventDefinitionManager = geoEventDefinitionManager;
  }

  private List<FieldDefinition> createFieldDefinitionList()
  {
    // GeoEventDefinition gedMC = new DefaultGeoEventDefinition();
    // gedMC.setName("MotionCalculator");
    List<FieldDefinition> fdsMC = new ArrayList<FieldDefinition>();
    try
    {
      fdsMC.add(new DefaultFieldDefinition("distance", FieldType.Double));
      fdsMC.add(new DefaultFieldDefinition("timespan", FieldType.Double));
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
      fdsMC.add(new DefaultFieldDefinition("predictiveTime", FieldType.Date));
      fdsMC.add(new DefaultFieldDefinition("predictivePosition", FieldType.Geometry));
    }
    catch (Exception e)
    {

    }
    return fdsMC;
    // gedMC.setFieldDefinitions(fdsMC);
    // geoEventDefinitions.put(gedMC.getName(), gedMC);

  }

  synchronized private GeoEventDefinition lookupAndCreateEnrichedDefinition() throws Exception
  {
    GeoEventDefinition edIn = geoEventDefinitionManager.getGeoEventDefinition(sourceGeoEventDefinitionGuid);
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

  private Object[] createMotionGeoEventFields(String trackId, MotionElements motionElements)
  {
    Object[] motionFields = new Object[22];
    motionFields[0] = motionElements.getDistance();
    motionFields[1] = motionElements.getTimespanSeconds();
    motionFields[2] = motionElements.getSpeed();
    motionFields[3] = motionElements.getHeadingDegrees();
    
    motionFields[4] = motionElements.getMinTime();
    motionFields[5] = motionElements.getMaxTime();
    motionFields[6] = motionElements.getAvgTime();

    motionFields[7] = motionElements.getMinDistance();
    motionFields[8] = motionElements.getMaxDistance();
    motionFields[9] = motionElements.getAvgDistance();

    motionFields[10] = motionElements.getMinSpeed();
    motionFields[11] = motionElements.getMaxSpeed();
    motionFields[12] = motionElements.getAvgSpeed();

    motionFields[13] = motionElements.getMinAcceleration();
    motionFields[14] = motionElements.getMaxAcceleration();
    motionFields[15] = motionElements.getAvgAcceleration();

    motionFields[16] = motionElements.getCumulativeDistance();
    motionFields[17] = motionElements.getCumulativeTime();
    motionFields[18] = motionElements.getTimestamp();

    motionFields[19] = motionElements.getPredictiveTime();
    motionFields[20] = motionElements.getPredictiveGeometry();
    return motionFields;
  }

  /*
   * Returns distance in KMs.
   */
  private static Double lawOfCosineDistance(Double lon1, Double lat1, Double lon2, Double lat2)
  {
    final Double R = 6356752.3142; // Radious of the earth in km
    Double radLon1 = toRadions(lon1);
    Double radLat1 = toRadions(lat1);
    Double radLon2 = toRadions(lon2);
    Double radLat2 = toRadions(lat2);

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
    final Double R = 6356752.3142; // Radious of the earth in km
    Double latDistance = toRadions(lat2 - lat1);
    Double lonDistance = toRadions(lon2 - lon1);
    Double a = Math.sin(latDistance / 2.0) * Math.sin(latDistance / 2.0) + Math.cos(toRadions(lat1)) * Math.cos(toRadions(lat2)) * Math.sin(lonDistance / 2.0) * Math.sin(lonDistance / 2.0);
    Double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    Double distance = R * c;
    return distance;
  }

  /*
   * Returns heading in degrees
   */
  private static Double heading(Double lon1, Double lat1, Double lon2, Double lat2)
  {
    Double radLon1 = toRadions(lon1);
    Double radLat1 = toRadions(lat1);
    Double radLon2 = toRadions(lon2);
    Double radLat2 = toRadions(lat2);
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

  private static Double toRadions(Double value)
  {
    return value * Math.PI / 180.0;
  }

  private static Double toDegrees(Double value)
  {
    return value * 180.0 / Math.PI;
  }
}
