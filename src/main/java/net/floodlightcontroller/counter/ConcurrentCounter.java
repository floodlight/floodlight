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

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.counter.CounterValue.CounterType;


/**
 * This module needs to be updated with CounterValue.
 * 
 * This is a crumby attempt at a highly concurrent implementation of the Counter interface.
 * 
 * (Help! Help!  Someone please re-write me!  This will almost certainly break at high loads.)
 * 
 * The gist is that this class, ConcurrentCounter, keeps an internal highly transient buffer that is occasionally flushed
 * in to a set of CountBuffers (circular buffers) which store a longer term historical view of the count values at different
 * moments in time.
 * 
 * This Counter implementation may be a bit over-engineered...  The goal here was to present an implementation that is very
 * predictable with respect to memory and CPU time and, at the same time, present a very fast increment() method.  The reasoning
 * here is that this will be a go-to class when it comes to debugging, particularly in high-load situations where logging
 * may introduce so much variability to the system that it foils the results.
 * 
 * @author kyle
 *
 */
public class ConcurrentCounter implements ICounter {

  protected static final Map<DateSpan, Integer> MAX_HISTORY = new HashMap<DateSpan, Integer>();
  static {
    MAX_HISTORY.put(DateSpan.REALTIME, new Integer(1));
    MAX_HISTORY.put(DateSpan.SECONDS, new Integer(120));
    MAX_HISTORY.put(DateSpan.MINUTES, new Integer(60));
    MAX_HISTORY.put(DateSpan.HOURS, new Integer(48));
    MAX_HISTORY.put(DateSpan.DAYS, new Integer(60));
    MAX_HISTORY.put(DateSpan.WEEKS, new Integer(2)); 
  }
  
  protected static Set<ConcurrentCounter> liveCounters;
  
  static {
    liveCounters = Collections.newSetFromMap(new ConcurrentHashMap<ConcurrentCounter, Boolean>()); //nifty way to get concurrent hash set
    //Set a background thread to flush any liveCounters every 100 milliseconds
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable() {
        public void run() {
            for(ConcurrentCounter c : liveCounters) {
                c.flush();
            }
        }}, 100, 100, TimeUnit.MILLISECONDS);
  }

  /**
   * Very simple data structure to store off a single count entry at a single point in time
   * @author kyle
   *
   */
  protected static final class CountAtom {
    protected Date date;
    protected Long delta;
    
    protected CountAtom(Date date, Long delta) {
      this.date = date;
      this.delta = delta;
    }
    
    public String toString() {
      return "[" + this.date + ": " + this.delta + "]";
    }
  }

  
  protected Queue<CountAtom> unprocessedCountBuffer;
  protected Map<DateSpan, CountBuffer> counts;
  protected Date startDate;
  
  /**
   * Factory method to create a new counter instance.  (Design note - 
   * use a factory pattern here as it may be necessary to hook in other
   * registrations around counter objects as they are created.)
   * 
   * @param startDate
   * @return
   */
  public static ICounter createCounter(Date startDate) {
    ConcurrentCounter cc = new ConcurrentCounter(startDate);
    ConcurrentCounter.liveCounters.add(cc);
    return cc;
    
  }
  
  /**
   * Protected constructor - use createCounter factory method instead
   * @param startDate
   */
  protected ConcurrentCounter(Date startDate) {
    init(startDate);
  }
  
  protected void init(Date startDate) {
    this.startDate = startDate;
    this.unprocessedCountBuffer = new ConcurrentLinkedQueue<CountAtom>();
    this.counts = new HashMap<DateSpan, CountBuffer>();
      
    for(DateSpan ds : DateSpan.values()) {
      CountBuffer cb = new CountBuffer(startDate, ds, MAX_HISTORY.get(ds));
      counts.put(ds, cb);
    }
  }
  /**
   * This is the key method that has to be both fast and very thread-safe.
   */
  @Override
  public void increment() {
    this.increment(new Date(), (long)1);
  }
  
  @Override
  public void increment(Date d, long delta) {
    this.unprocessedCountBuffer.add(new CountAtom(d, delta));
  }
  
  @Override
  public void setCounter(Date d, CounterValue value) {
      // To be done later
  }
  
  /**
   * Reset the value.
   */
  @Override
  public void reset(Date startDate) {
    init(startDate);
  }
  
  /**
   * Flushes values out of the internal buffer and in to structures
   * that can be fetched with a call to snapshot()
   */
  public synchronized void flush() {
    for(CountAtom c = this.unprocessedCountBuffer.poll(); c != null; c = this.unprocessedCountBuffer.poll()) {
      for(DateSpan ds : DateSpan.values()) {
        CountBuffer cb = counts.get(ds);
        cb.increment(c.date, c.delta);
      }
    }
  }
  
  @Override
  public CounterValue getCounterValue() {
      // To be done later
      //CountSeries cs = counts.get(DateSpan.REALTIME).snapshot();
      //return cs.getSeries()[0];
      return new CounterValue(CounterType.LONG);
  }
  
  @Override
  public Date getCounterDate() {
      // To be done later
      //CountSeries cs = counts.get(DateSpan.REALTIME).snapshot();
      //return cs.getSeries()[0];
      return new Date();
  }
  
  @Override
  /**
   * This method returns a disconnected copy of the underlying CountSeries corresponding to dateSpan.
   */
  public CountSeries snapshot(DateSpan dateSpan) {
    flush();
    CountSeries cs = counts.get(dateSpan).snapshot();
    return cs;
  }

  
  
}
