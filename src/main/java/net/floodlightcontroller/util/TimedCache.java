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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The key is any object/hash-code
 * The value is time-stamp in milliseconds
 * The time interval denotes the interval for which the entry should remain in the hashmap.
 * If an entry is present in the Linkedhashmap, it does not mean that it's valid (recently seen)
 * 
 * @param <K> Type of the values in this cache
 */
public class TimedCache<K> {    
    private final long timeoutInterval;    //specified in milliseconds.
	private ConcurrentMap<K, Long> cache;
    private long cacheHits;
    private long totalHits;
    
	public TimedCache(int capacity, int timeToLive) {
        cache = new ConcurrentLinkedHashMap.Builder<K, Long>()
        	    .maximumWeightedCapacity(capacity)
            .build();
        this.timeoutInterval = timeToLive;
        this.cacheHits = 0;
        this.totalHits = 0;
    }
    
    public long getTimeoutInterval() {
        return this.timeoutInterval;
    }
    
    public long getCacheHits() {
    	     return cacheHits;
    }
    
    public long getTotalHits() {
		return totalHits;
	}

    /**
     * Always try to update the cache and set the last-seen value for this key.
     * 
     * Return true, if a valid existing field was updated, else return false.
     * (note: if multiple threads update simultaneously, one of them will succeed,
     *  other wills return false)
     * 
     * @param key
     * @return boolean
     */
    public boolean update(K key)
    {
        Long curr = new Long(System.currentTimeMillis());
        Long prev = cache.putIfAbsent(key, curr);
        
		this.totalHits++;
        if (prev == null) {
        		return false;
        }

        if (curr - prev > this.timeoutInterval) {
            if (cache.replace(key, prev, curr)) {
            		return false;
            }
        }
        
        this.cacheHits++;
        return true;
    }
}
