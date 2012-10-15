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

/**
 * 
 */
package net.floodlightcontroller.counter;

import java.util.Date;



/**
 * This is a simple counter implementation that doesn't support data series.
 * The idea is that floodlight only keeps the realtime value for each counter,
 * statd, a statistics collection daemon, samples counters at a user-defined interval
 * and pushes the values to a database, which keeps time-based data series. 
 * @author Kanzhe
 *
 */
public class SimpleCounter implements ICounter {

  protected CounterValue counter;
  protected Date samplingTime;
  protected Date startDate;
  
  /**
   * Factory method to create a new counter instance.  
   * 
   * @param startDate
   * @return
   */
  public static ICounter createCounter(Date startDate, CounterValue.CounterType type) {
    SimpleCounter cc = new SimpleCounter(startDate, type);
    return cc;
  }
  
  /**
   * Factory method to create a copy of a counter instance.  
   * 
   * @param startDate
   * @return
   */
  public static ICounter createCounter(ICounter copy) {
    if (copy == null ||
        copy.getCounterDate() == null ||
        copy.getCounterValue() == null) {
        return null;
    }

     SimpleCounter cc = new SimpleCounter(copy.getCounterDate(),
            copy.getCounterValue().getType());
     cc.setCounter(copy.getCounterDate(), copy.getCounterValue());
     return cc;
  }
  
  /**
   * Protected constructor - use createCounter factory method instead
   * @param startDate
   */
  protected SimpleCounter(Date startDate, CounterValue.CounterType type) {
    init(startDate, type);
  }
  
  protected void init(Date startDate, CounterValue.CounterType type) {
    this.startDate = startDate;
    this.samplingTime = new Date();
    this.counter = new CounterValue(type);
  }
  
  /**
   * This is the key method that has to be both fast and very thread-safe.
   */
  @Override
  synchronized public void increment() {
    this.increment(new Date(), (long)1);
  }
  
  @Override
  synchronized public void increment(Date d, long delta) {
    this.samplingTime = d;
    this.counter.increment(delta);
  }
  
  synchronized public void setCounter(Date d, CounterValue value) {
      this.samplingTime = d;
      this.counter = value;
  }
  
  /**
   * This is the method to retrieve the current value.
   */
  @Override
  synchronized public CounterValue getCounterValue() {
    return this.counter;
  }

  /**
   * This is the method to retrieve the last sampling time.
   */
  @Override
  synchronized public Date getCounterDate() {
    return this.samplingTime;
  }
  
  /**
   * Reset value.
   */
  @Override
  synchronized public void reset(Date startDate) {
    init(startDate, this.counter.getType());
  }
  
  @Override
  /**
   * This method only returns the real-time value.
   */
  synchronized public CountSeries snapshot(DateSpan dateSpan) {
    long[] values = new long[1];
    values[0] = this.counter.getLong();
    return new CountSeries(this.samplingTime, DateSpan.DAYS, values);
  }
}
