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
 * Simple interface for a counter whose value can be retrieved in several different
 * time increments (last x seconds, minutes, hours, days)
 */
package net.floodlightcontroller.counter;

import java.util.Date;

/**
 * @author kyle
 *
 */
public interface ICounter {

  /**
   * Most commonly used method
   */
  public void increment();

  /**
   * Used primarily for flushing thread local updates
   */
  public void increment(Date d, long delta);

  /**
   * Counter value setter
   */
  public void setCounter(Date d, CounterValue value);

  /**
   * Return the most current value
   */
  public Date getCounterDate();

  /**
   * Return the most current value
   */
  public CounterValue getCounterValue();

  /**
   * Reset the value
   */
  public void reset(Date d);


  public static enum DateSpan {
    REALTIME,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
    WEEKS
  }
}
