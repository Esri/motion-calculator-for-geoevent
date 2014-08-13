package com.esri.geoevent.processor.motioncalculator;

class Motion
{
  /**
   * 
   */
  Long cumulativeCounter = 0L;
  Long currentCounter = 0L;
  
  public Motion()
  {
  }

  public Long getCumulativeCounter()
  {
    return cumulativeCounter;
  }

  public void setCumulativeCounter(Long cumulativeCounter)
  {
    this.cumulativeCounter = cumulativeCounter;
  }

  public Long getCurrentCounter()
  {
    return currentCounter;
  }

  public void setCurrentCounter(Long currentCounter)
  {
    this.currentCounter = currentCounter;
  }   
}