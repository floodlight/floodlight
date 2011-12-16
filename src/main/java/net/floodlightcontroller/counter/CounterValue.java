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

/**
 * The class defines the counter value type and value
 * 
 * @author Kanzhe
 *
 */
public class CounterValue { 
  public enum CounterType {
      LONG,
      DOUBLE
  }
  
  protected CounterType type; 
  protected long longValue;
  protected double doubleValue;
  
  public CounterValue(CounterType type) {
    this.type = CounterType.LONG;
    this.longValue = 0;    
    this.doubleValue = 0.0;
  }
  
  /**
   * This method is only applicable to type long.
   * Setter() should be used for type double
   */
  public void increment(long delta) {
      if (this.type == CounterType.LONG) {
          this.longValue += delta;
      } else {
          throw new IllegalArgumentException("Invalid counter type. This counter is not a long type.");
      }
  }
  
  public void setLongValue(long value) {
      if (this.type == CounterType.LONG) {
          this.longValue = value;
      } else {
          throw new IllegalArgumentException("Invalid counter type. This counter is not a long type.");
      }
  }
  
  public void setDoubleValue(double value) {
      if (this.type == CounterType.DOUBLE) {
          this.doubleValue = value;
      } else {
          throw new IllegalArgumentException("Invalid counter type. This counter is not a double type.");
      }
  }
  
  public long getLong() {
      if (this.type == CounterType.LONG) {
          return this.longValue;
      } else {
          throw new IllegalArgumentException("Invalid counter type. This counter is not a long type.");
      }
  }
  
  public double getDouble() {
      if (this.type == CounterType.DOUBLE) {
          return this.doubleValue;
      } else {
          throw new IllegalArgumentException("Invalid counter type. This counter is not a double type.");
      }
  }
  

  public CounterType getType() {
    return this.type;
  }
  
  public String toString() {
    String ret = "{type: ";
    if (this.type == CounterType.DOUBLE) {
        ret += "Double" + ", value: " + this.doubleValue + "}";
    } else {
        ret += "Long" + ", value: " + this.longValue + "}";
    }
    return ret;
  }


}
