/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.counter;

import java.util.Arrays;
import java.util.Date;

import net.floodlightcontroller.counter.ICounter.DateSpan;

/**
 * Simple immutable class to store a series of historic counter values
 * 
 * This could probably use a re-think...
 * 
 * @author kyle
 *
 */
public class CountSeries {  
  protected long[] counterValues;
  protected Date startDate;
  protected DateSpan dateSpan;
  
  public CountSeries(Date startDate, DateSpan dateSpan, long[] counterValues) {
    this.counterValues = counterValues.clone();
    this.dateSpan = dateSpan;    
    this.startDate = startDate;
  }
  

  public long[] getSeries() { //synchronized here should lock on 'this', implying that it shares the lock with increment
    return this.counterValues.clone();
  }
  
  /**
   * Returns the startDate of this series.  The first long in getSeries represents the sum of deltas from increment calls with dates
   * that correspond to >= startDate and < startDate + DateSpan.
   * @return
   */
  public Date getStartDate() {//synchronized here should lock on 'this', implying that it shares the lock with increment
    return this.startDate;
  }
  
  public String toString() {
    String ret = "{start: " + this.startDate + ", span: " + this.dateSpan + ", series: " + Arrays.toString(getSeries()) + "}";
    return ret;
  }
  
  /**
   * Return a long that is the number of milliseconds in a ds (second/minute/hour/day/week).  (Utility method.)
   * 
   * @param d
   * @param ds
   * @return
   */
  public static final long dateSpanToMilliseconds(DateSpan ds) {
    long delta = 1;
    switch(ds) {
    case WEEKS:
      delta *= 7;
    case DAYS:
      delta *= 24;
    case HOURS:
      delta *= 60;
    case MINUTES:
      delta *= 60;
    case SECONDS:
      delta *= 1000;
    }
    return delta;
  }

}
