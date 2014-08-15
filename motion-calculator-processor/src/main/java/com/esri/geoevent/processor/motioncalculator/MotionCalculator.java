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
import com.esri.ges.core.geoevent.FieldException;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.validation.ValidationException;
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
  private static final Log                     log                      = LogFactory.getLog(MotionCalculator.class);
  private Spatial spatial;
 
  private MotionCalculatorNotificationMode     notificationMode;
  private long                                 reportInterval;

  private final Map<String, MotionElements>    motionElementsCache      = new ConcurrentHashMap<String, MotionElements>();

  private Messaging                            messaging;
  private GeoEventCreator                      geoEventCreator;
  private GeoEventProducer                     geoEventProducer;
  private EventDestination                     destination;
  
  private boolean                              copyFieldsFromSource;  
  private String                               distanceUnit;
  private String                               geometryType;
  private boolean                              predictivePosition;
  private String                               predictiveGeometryType;
  private Integer                              predictiveTimespan;
  private Date                                 resetTime;
  private boolean                              autoResetCache;  
  private Timer                                clearCacheTimer;
  private boolean                              clearCache;
  private Uri                                  definitionUri;
  private String                               definitionUriString;
  private boolean                              isCounting = false;
   
  final   Object                               lock1 = new Object();
  
  class MotionElements
  {
    private String id;
    private Geometry lineGeometry;
    private Geometry prevGeometry;
    private Date timestamp;
    private Double distance = 0.0; //distance defaulted to KMs, but may change to miles based on the distanceunit
    private Double timespanSeconds = 0.0;
    private Double speed = 0.0;
    private Double acceleration = 0.0; //distances per second square
    private Double headingDegrees = 0.0;
    private Double cumulativeDistance = 0.0;
    private Double cumulativeTimeSeconds = 0.0;
    private Double minDistance = 0.0;
    private Double maxDistance = 0.0;
    private Double avgDistance = 0.0;
    private Double minSpeed = 0.0;
    private Double maxSpeed = 0.0;
    private Double avgSpeed = 0.0;
    private Double minAcceleration = 0.0;
    private Double maxAcceleration = 0.0;
    private Double avgAcceleration = 0.0;
    private Double minTimespan = 0.0;
    private Double maxTimespan = 0.0;
    private Double avgTimespan = 0.0;
    private Long count = 0L;
    private Date predictiveTime;
       
    public MotionElements()
    {
      
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
      if (geometryType.equals("Point")) {
        return this.prevGeometry;
      }
      else
      {
        return lineGeometry;
      }
    }

    public void setGeometry(Geometry geometry)
    {
      count++;
      if (this.prevGeometry == null) 
      {
        this.prevGeometry = geometry;
        return;
      }
      Point from = (Point)this.prevGeometry;
      Point to = (Point)geometry;
      //Double newDistance = halversineDistance(from.getX(), from.getY(), to.getX(), to.getY());
      Double newDistance = lawOfCosineDistance(from.getX(), from.getY(), to.getX(), to.getY());
      if (distanceUnit == "Miles")
      {
        newDistance *= 0.621371; //Convert KMs to Miles -- will affect all subsequent calculation 
      }
      Double timespanHours = timespanSeconds / (3600.0);
      Double newSpeed = newDistance / timespanHours;
      Double newAcceleration = (newSpeed - speed) / timespanHours;
      if (minDistance > newDistance) {
        minDistance = newDistance;
      }
      if (maxDistance < newDistance) {
        maxDistance = newDistance;
      }
      if (minSpeed > newSpeed) {
        minSpeed = newSpeed;
      }
      if (maxSpeed < newSpeed) {
        maxSpeed = newSpeed;
      }
      if (minAcceleration > newAcceleration) {
        minAcceleration = newAcceleration;
      }
      if (maxAcceleration < newAcceleration) {
        maxAcceleration = newAcceleration;
      }
      
      cumulativeDistance = cumulativeDistance + newDistance;
      //avgSpeed = cumulativeDistance / cumulativeTimeSeconds;
      avgSpeed = avgDistance / avgTimespan;
      avgAcceleration = avgSpeed / avgTimespan;
      
      headingDegrees = heading(from.getX(), from.getY(), to.getX(), to.getY());
      distance = newDistance;
      speed = newSpeed;
      acceleration = newAcceleration;
      Polyline polyline = spatial.createPolyline();
      polyline.startPath(from.getX(), from.getY(), Double.NaN);
      polyline.lineTo(to.getX(), to.getY(), Double.NaN);

      this.prevGeometry = geometry;
    }

    public String getId()
    {
      return id;
    }

    public void setId(String id)
    {
      this.id = id;
    }
    
    public void sendReport()
    {
      if (notificationMode != MotionCalculatorNotificationMode.OnChange)
      {
        return;
      }
      
      try
      {
        send(createMotionGeoEvent(id, this));
      }
      catch (MessagingException e)
      {
        log.error("Error sending update GeoEvent for " + id, e);
      }
    }
   
    public Date getTimestamp()
    {
      return timestamp;    
    }

    public void setTimestamp(Date startTime)
    {
      Long timespanMilliSecs = 0L;
      if (timestamp != null)
      {
        timespanMilliSecs = startTime.getTime() - timestamp.getTime();
      }
      Double timespanSecs = timespanMilliSecs / 1000.0;
      if (timespanSecs == 0.0)
      {
        timespanSecs = 0.0000000001; // set to very small value to avoid divisor is 0
      }
      if (minTimespan > timespanSecs)
      {
        minTimespan = timespanSecs;
      }
      if (maxTimespan < timespanSecs)
      {
        maxTimespan = timespanSecs;
      }
      cumulativeTimeSeconds = cumulativeTimeSeconds + timespanSecs;
      if (count > 0)
      {
        avgTimespan = cumulativeTimeSeconds / count;
      }
      else
      {
        avgTimespan = cumulativeTimeSeconds;
      }
      
      timestamp = startTime;
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
      Long timespan = timestamp.getTime() + predictiveTimespan;
      Date pt = new Date();
      pt.setTime(timespan);
      predictiveTime = pt;
      return predictiveTime;
    }

    public Geometry getPredictiveGeometry()
    {
      //TODO: calculate point for this based on velocity and timespan
      Geometry predictiveGeometry = null;
      return predictiveGeometry;
    }
  }
 
  class ClearCacheTask extends TimerTask
  {
    public void run()
    {
      if (autoResetCache == true)
      {
        for (String catId : motionElementsCache.keySet())
        {
          MotionElements motionEles = new MotionElements();
          motionElementsCache.put(catId, motionEles);
        }
      }
      // clear the cache
      if (clearCache == true)
      {
        motionElementsCache.clear();
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
      while (isCounting)
      {
        try
        {
          Thread.sleep(reportInterval);
          if (notificationMode != MotionCalculatorNotificationMode.Continuous)
          {
            continue;
          }
          
          for (String catId : motionElementsCache.keySet())
          {
            MotionElements counters = motionElementsCache.get(catId);
            try
            {
              send(createMotionGeoEvent(catId, counters));
            }
            catch (MessagingException e)
            {
              log.error("Error sending update GeoEvent for " + catId, e);
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
    distanceUnit = getProperty("distanceUnit").getValueAsString();
    geometryType = getProperty("geometryType").getValueAsString();
    notificationMode = Validator.validateEnum(MotionCalculatorNotificationMode.class, getProperty("notificationMode").getValueAsString(), MotionCalculatorNotificationMode.OnChange);
    reportInterval = Converter.convertToInteger(getProperty("reportInterval").getValueAsString(), 10) * 1000;
    autoResetCache = Converter.convertToBoolean(getProperty("autoResetCache").getValueAsString());
    predictivePosition = Converter.convertToBoolean(getProperty("predictivePosition").getValueAsString());
    predictiveGeometryType = getProperty("predictiveGeometryType").getValueAsString();
    predictiveTimespan = Converter.convertToInteger(getProperty("predictiveTimespan").getValueAsString(), 10) * 1000; //convert to milliseconds

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
  public GeoEvent process(GeoEvent geoEvent) throws Exception
  {
    String trackId = geoEvent.getTrackId();
    Geometry geometry = geoEvent.getGeometry();
    
    MotionElements motionEle;
    if (motionElementsCache.containsKey(trackId) == false) 
    {
      motionEle = new MotionElements();
    }
    else
    {
      motionEle = motionElementsCache.get(trackId);
    }
    motionEle.setId(trackId);
    //Need to set timestamp before geometry to ensure order of calculation
    motionEle.setTimestamp(geoEvent.getStartTime());
    motionEle.setGeometry(geometry);
    // Need to synchronize the Concurrent Map on write to avoid wrong counting
    synchronized(lock1)
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
        Long dayInMilliSeconds = 60*60*24*1000L;
        clearCacheTimer.scheduleAtFixedRate(new ClearCacheTask(), time1, dayInMilliSeconds);
      }
      //trackGeometryCache.clear();
      motionElementsCache.clear();
    }
   
    isCounting = true;
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
    isCounting = false;
  }

  @Override
  public void shutdown()
  {
    super.shutdown();
    
    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
  }

  @Override
  public EventDestination getEventDestination()
  {
    return destination;
  }

  @Override
  public void send(GeoEvent geoEvent) throws MessagingException
  {
    //Try to get it again
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
  
  private GeoEvent createMotionGeoEvent(String trackId, MotionElements motionElements) throws MessagingException
  {
    GeoEvent motionEvent = null;
    if (geoEventCreator != null && definitionUriString != null && definitionUri != null)
    {
      try
      {
        motionEvent = geoEventCreator.create("MotionCalculator", definitionUriString);
        motionEvent.setField(0, trackId);
        motionEvent.setField(1, motionElements.getDistance());
        motionEvent.setField(2, motionElements.getSpeed());
        motionEvent.setField(3, motionElements.getHeadingDegrees());
        
        motionEvent.setField(4, motionElements.getMinTime());
        motionEvent.setField(5, motionElements.getMaxTime());
        motionEvent.setField(6, motionElements.getAvgTime());
        
        motionEvent.setField(7, motionElements.getMinDistance());
        motionEvent.setField(8, motionElements.getMaxDistance());
        motionEvent.setField(9, motionElements.getAvgDistance());
        
        motionEvent.setField(10, motionElements.getMinSpeed());
        motionEvent.setField(11, motionElements.getMaxSpeed());
        motionEvent.setField(12, motionElements.getAvgSpeed());

        motionEvent.setField(13, motionElements.getMinAcceleration());
        motionEvent.setField(14, motionElements.getMaxAcceleration());
        motionEvent.setField(15, motionElements.getAvgAcceleration());
        
        motionEvent.setField(16, motionElements.getCumulativeDistance());
        motionEvent.setField(17, motionElements.getCumulativeTime());
        motionEvent.setField(18, motionElements.getTimestamp());
        
        motionEvent.setField(19, motionElements.getGeometry());
        motionEvent.setField(20, motionElements.getPredictiveTime());
        motionEvent.setField(21, motionElements.getPredictiveGeometry());
       
        motionEvent.setProperty(GeoEventPropertyName.TYPE, "event");
        motionEvent.setProperty(GeoEventPropertyName.OWNER_ID, getId());
        motionEvent.setProperty(GeoEventPropertyName.OWNER_URI, definitionUri);
      }
      catch (FieldException e)
      {
        motionEvent = null;
        log.error("Failed to create Motion Calculator GeoEvent: " + e.getMessage());
      }
    }
    return motionEvent;
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
     
     return Math.acos(Math.sin(radLat1)*Math.sin(radLat2)+Math.cos(radLat1)*Math.cos(radLat2)*Math.cos(radLon2-radLon1))*R;    
  }
  
  /*
   * This is the implementation Haversine Distance Algorithm between two locations
   *  R = earth’s radius (mean radius = 6,371km)
      Δlat = lat2− lat1
      Δlong = long2− long1
      a = sin²(Δlat/2) + cos(lat1).cos(lat2).sin²(Δlong/2)
      c = 2.atan2(√a, √(1−a))
      d = R.c
   *
   * Returns distance in KMs.
   */
  private static Double halversineDistance(Double lon1, Double lat1, Double lon2, Double lat2) {
    final Double R = 6356752.3142; // Radious of the earth in km
    Double latDistance = toRadions(lat2-lat1);
    Double lonDistance = toRadions(lon2-lon1);
    Double a = Math.sin(latDistance / 2.0) * Math.sin(latDistance / 2.0) + 
               Math.cos(toRadions(lat1)) * Math.cos(toRadions(lat2)) * 
               Math.sin(lonDistance / 2.0) * Math.sin(lonDistance / 2.0);
    Double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
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
    Double y = Math.sin(radLon2-radLon1)*Math.cos(radLat2);
    Double x = Math.cos(radLat1)*Math.sin(radLat2)-Math.sin(radLat1)*Math.cos(radLat2)*Math.cos(radLon2-radLon1);
    /* Without using Math.atan2()
    Double headingDegrees = 0.0;
    if (y > 0) 
    {
      if (x > 0) { headingDegrees = toDegrees(Math.atan(y/x)); }
      if (x < 0) { headingDegrees = 180.0 - toDegrees(Math.atan(-y/x)); }
      if (x == 0){ headingDegrees = 90.0; }  
    }
    if (y < 0) 
    {
      if (x > 0) { headingDegrees = toDegrees(-Math.atan(-y/x));}
      if (x < 0) { headingDegrees = toDegrees(Math.atan(y/x))-180.0; }
      if (x == 0){ headingDegrees = 270.0; }
    }
    if (y == 0) 
    {
      if (x > 0) { headingDegrees = 0.0; }
      if (x < 0) { headingDegrees = 180.0; }
      if (x == 0){ headingDegrees = Double.NaN; } //the 2 points are the same
    }
    */
    Double headingDegrees = toDegrees(Math.atan2(y, x) % (2.0*Math.PI));
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

