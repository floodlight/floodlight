/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class TimedCacheTest {
    public static class CacheEntry {
        public int key;
        
        public CacheEntry(int key) {
            this.key = key;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + key;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheEntry other = (CacheEntry) obj;
            if (key != other.key)
                return false;
            return true;
        }
    }
    
    protected TimedCache<CacheEntry> cache;
    
    @Before
    public void setUp() {
        // 
    }
    
    
    @Test
    public void testCaching() throws InterruptedException {
        int timeout = 50;
        int timeToSleep = 60;
        cache = new TimedCache<TimedCacheTest.CacheEntry>(100, timeout);
        
        CacheEntry e1a = new CacheEntry(1);
        CacheEntry e1b = new CacheEntry(1);
        CacheEntry e1c = new CacheEntry(1);
        CacheEntry e2 = new CacheEntry(2);
        
        assertEquals(false, cache.update(e1a));
        assertEquals(true, cache.update(e1a));
        assertEquals(true, cache.update(e1b));
        assertEquals(true, cache.update(e1c));
        assertEquals(false, cache.update(e2));
        assertEquals(true, cache.update(e2));
        
        Thread.sleep(timeToSleep);
        assertEquals(false, cache.update(e1a));
        assertEquals(false, cache.update(e2));
    }
    
    @Test
    public void testCapacity() throws InterruptedException {
        int timeout = 5000;
        cache = new TimedCache<TimedCacheTest.CacheEntry>(2, timeout);
        
        // Testing the capacity is tricky since the capacity can be 
        // exceeded for short amounts of time, so we try to flood the cache
        // to make sure the first entry is expired
        CacheEntry e1 = new CacheEntry(1);
        for (int i=0; i < 100; i++) {
            CacheEntry e = new CacheEntry(i);
            cache.update(e);
        }
        
        // entry 1 should have been expired due to capacity limits 
        assertEquals(false, cache.update(e1));
    }
    
    
    
}
