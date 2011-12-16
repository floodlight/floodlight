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

package net.floodlightcontroller.util;

import java.util.LinkedHashMap;
import java.util.Map;


// The key is any object/hash-code
// The value is time-stamp in milliseconds
// The time interval denotes the interval for which the entry should remain in the hashmap.

// If an entry is present in the Linkedhashmap, it does not mean that 


public class TimedHashMap<K, V> extends LinkedHashMap<K, V> {
    
    private static final long serialVersionUID = 1L;
    
    private final long timeoutInterval;    //specified in milliseconds.
    
    public TimedHashMap(int ti)
    {
        super();
        this.timeoutInterval = ti;
    }
    
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
       return (((Long)eldest.getValue()) < System.currentTimeMillis() - this.timeoutInterval);
    }
    
    public long getTimeoutInterval()
    {
        return this.timeoutInterval;
    }
}