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

import java.util.Date;

import net.floodlightcontroller.counter.ICounter.DateSpan;


/**
 * Implements a circular buffer to store the last x time-based counter values.  This is pretty crumby
 * implementation, basically wrapping everything with synchronized blocks, in order to ensure that threads
 * which will be updating the series don't result in a thread which is reading the series getting stuck with
 * a start date which does not correspond to the count values in getSeries.
 * 
 * This could probably use a re-think...
 * 
 * @author kyle
 *
 */
public class CountBuffer {
  protected long[] counterValues;
  protected Date startDate;
  protected DateSpan dateSpan;
  protected int currentIndex;
  protected int seriesLength;


  public CountBuffer(Date startDate, DateSpan dateSpan, int seriesLength) {
    this.seriesLength = seriesLength;
    this.counterValues = new long[seriesLength];
    this.dateSpan = dateSpan;
    
    this.startDate = startDate;
    this.currentIndex = 0;
  }
  
  /**
   * Increment the count associated with Date d, forgetting some of the older count values if necessary to ensure
   * that the total span of time covered by this series corresponds to DateSpan * seriesLength (circular buffer).
   * 
   * Note - fails silently if the Date falls prior to the start of the tracked count values.
   * 
   * Note - this should be a reasonably fast method, though it will have to block if there is another thread reading the
   * series at the same time.
   * 
   * @param d
   * @param delta
   */
  public synchronized void increment(Date d, long delta) {

    long dsMillis = CountSeries.dateSpanToMilliseconds(this.dateSpan);
    Date endDate = new Date(startDate.getTime() + seriesLength * dsMillis - 1);

    if(d.getTime() < startDate.getTime()) {
      return; //silently fail rather than insert a count at a time older than the history buffer we're keeping
    }
    else if (d.getTime() >= startDate.getTime() && d.getTime() <= endDate.getTime()) {
        int index = (int)  (( d.getTime() - startDate.getTime() ) / dsMillis); // java rounds down on long/long
        int modIndex = (index + currentIndex) % seriesLength;
        long currentValue = counterValues[modIndex];
        counterValues[modIndex] = currentValue + delta;
    }
    else if (d.getTime() > endDate.getTime()) {
      //Initialize new buckets
      int newBuckets = (int)((d.getTime() - endDate.getTime()) / dsMillis) + 1; // java rounds down on long/long
      for(int i = 0; i < newBuckets; i++) {
        int modIndex = (i + currentIndex) % seriesLength;
        counterValues[modIndex] = 0;
      }
      //Update internal vars
      this.startDate = new Date(startDate.getTime() + dsMillis * newBuckets);
      this.currentIndex = (currentIndex + newBuckets) % this.seriesLength;    

      //Call again (date should be in the range this time)
      this.increment(d, delta);
    }
  }
  
  /**
   * Relatively slow method, expected to be called primarily from UI rather than from in-packet-path.
   * 
   * @return the count values associated with each time interval starting with startDate and demarc'ed by dateSpan
   */
  public long[] getSeries() { //synchronized here should lock on 'this', implying that it shares the lock with increment
    long[] ret = new long[this.seriesLength];
    for(int i = 0; i < this.seriesLength; i++) {
      int modIndex = (currentIndex + i) % this.seriesLength;
      ret[i] = this.counterValues[modIndex];
    }
    return ret;
  }

  
  /**
   * Returns an immutable count series that represents a snapshot of this
   * series at a specific moment in time.
   * @return
   */
  public synchronized CountSeries snapshot() {
    long[] cvs = new long[this.seriesLength];
    for(int i = 0; i < this.seriesLength; i++) {
      int modIndex = (this.currentIndex + i) % this.seriesLength;
      cvs[i] = this.counterValues[modIndex];
    }

    return new CountSeries(this.startDate, this.dateSpan, cvs);
  }
  
}
