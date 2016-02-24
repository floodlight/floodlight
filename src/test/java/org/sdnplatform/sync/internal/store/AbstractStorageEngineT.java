/*
 * Copyright 2008-2009 LinkedIn, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.sdnplatform.sync.internal.store;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Test;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.util.ByteArray;

import static org.junit.Assert.*;


public abstract class AbstractStorageEngineT extends AbstractByteArrayStoreT {

    @Override
    public IStore<ByteArray, byte[]> getStore() {
        return getStorageEngine();
    }

    public abstract IStorageEngine<ByteArray, byte[]> getStorageEngine();

    public void testGetNoEntries() {
        IClosableIterator<Entry<ByteArray, List<Versioned<byte[]>>>> it = null;
        try {
            IStorageEngine<ByteArray, byte[]> engine = getStorageEngine();
            it = engine.entries();
            while(it.hasNext())
                fail("There shouldn't be any entries in this store.");
        } finally {
            if(it != null)
                it.close();
        }
    }

    @Test
    public void testGetNoKeys() {
        IClosableIterator<ByteArray> it = null;
        try {
            IStorageEngine<ByteArray, byte[]> engine = getStorageEngine();
            it = engine.keys();
            while(it.hasNext())
                fail("There shouldn't be any entries in this store.");
        } finally {
            if(it != null)
                it.close();
        }
    }

    @Test
    public void testPruneOnWrite() throws SyncException {
        IStorageEngine<ByteArray, byte[]> engine = getStorageEngine();
        Versioned<byte[]> v1 = new Versioned<byte[]>(new byte[] { 1 }, TUtils.getClock(1));
        Versioned<byte[]> v2 = new Versioned<byte[]>(new byte[] { 2 }, TUtils.getClock(2));
        Versioned<byte[]> v3 = new Versioned<byte[]>(new byte[] { 3 }, TUtils.getClock(1, 2));
        ByteArray key = new ByteArray((byte) 3);
        engine.put(key, v1);
        engine.put(key, v2);
        assertEquals(2, engine.get(key).size());
        engine.put(key, v3);
        assertEquals(1, engine.get(key).size());
    }

    @Test
    public void testTruncate() throws Exception {
        IStorageEngine<ByteArray, byte[]> engine = getStorageEngine();
        Versioned<byte[]> v1 = new Versioned<byte[]>(new byte[] { 1 });
        Versioned<byte[]> v2 = new Versioned<byte[]>(new byte[] { 2 });
        Versioned<byte[]> v3 = new Versioned<byte[]>(new byte[] { 3 });
        ByteArray key1 = new ByteArray((byte) 3);
        ByteArray key2 = new ByteArray((byte) 4);
        ByteArray key3 = new ByteArray((byte) 5);

        engine.put(key1, v1);
        engine.put(key2, v2);
        engine.put(key3, v3);
        engine.truncate();

        IClosableIterator<Entry<ByteArray, List<Versioned<byte[]>>>> it = null;
        try {
            it = engine.entries();
            while(it.hasNext()) {
                fail("There shouldn't be any entries in this store.");
            }
        } finally {
            if(it != null) {
                it.close();
            }
        }
    }

    @Test
    public void testCleanupTask() throws Exception {
        IStorageEngine<ByteArray, byte[]> engine = getStorageEngine();
        engine.setTombstoneInterval(500);

        Versioned<byte[]> v1_1 = new Versioned<byte[]>(new byte[] { 1 }, TUtils.getClock(1));
        Versioned<byte[]> v1_2 = new Versioned<byte[]>(null, TUtils.getClock(1, 1));

        // add, update, delete
        Versioned<byte[]> v2_1 = new Versioned<byte[]>(new byte[] { 1 }, TUtils.getClock(1));
        Versioned<byte[]> v2_2 = new Versioned<byte[]>(new byte[] { 2 }, TUtils.getClock(1, 2));
        Versioned<byte[]> v2_3 = new Versioned<byte[]>(null, TUtils.getClock(1, 2, 1));

        // delete then add again
        Versioned<byte[]> v3_1 = new Versioned<byte[]>(new byte[] { 1 }, TUtils.getClock(1));
        Versioned<byte[]> v3_2 = new Versioned<byte[]>(null, TUtils.getClock(1, 2));
        Versioned<byte[]> v3_3 = new Versioned<byte[]>(new byte[] { 2 }, TUtils.getClock(1, 2, 1));

        // delete concurrent to update
        Versioned<byte[]> v4_1 = new Versioned<byte[]>(new byte[] { 1 }, TUtils.getClock(1));
        Versioned<byte[]> v4_2 = new Versioned<byte[]>(new byte[] { 2 }, TUtils.getClock(1, 2));
        Versioned<byte[]> v4_3 = new Versioned<byte[]>(null, TUtils.getClock(1, 1));
        
        ByteArray key1 = new ByteArray((byte) 3);
        ByteArray key2 = new ByteArray((byte) 4);
        ByteArray key3 = new ByteArray((byte) 5);
        ByteArray key4 = new ByteArray((byte) 6);

        engine.put(key1, v1_1);
        assertEquals(1, engine.get(key1).size());

        engine.put(key1, v1_2);
        List<Versioned<byte[]>> r = engine.get(key1);
        assertEquals(1, r.size());
        assertNull(r.get(0).getValue());

        engine.put(key2, v2_1);
        engine.put(key2, v2_2);
        engine.put(key2, v2_3);
        engine.put(key3, v3_1);
        engine.put(key3, v3_2);
        engine.put(key4, v4_1);
        engine.put(key4, v4_2);
        engine.put(key4, v4_3);
        
        engine.cleanupTask();
        r = engine.get(key1);
        assertEquals(1, r.size());
        assertNull(r.get(0).getValue());

        engine.put(key3, v3_3);
        
        Thread.sleep(501);
        engine.cleanupTask();
        r = engine.get(key1);
        assertEquals(0, r.size());
        r = engine.get(key2);
        assertEquals(0, r.size());
        r = engine.get(key3);
        assertEquals(1, r.size());
        r = engine.get(key4);
        assertEquals(2, r.size());
        
    }

    @SuppressWarnings("unused")
    private boolean remove(List<byte[]> list, byte[] item) {
        Iterator<byte[]> it = list.iterator();
        boolean removedSomething = false;
        while(it.hasNext()) {
            if(TUtils.bytesEqual(item, it.next())) {
                it.remove();
                removedSomething = true;
            }
        }
        return removedSomething;
    }

}
