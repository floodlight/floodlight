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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Test;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.version.VectorClock;


import static org.junit.Assert.*;
import static org.sdnplatform.sync.internal.TUtils.*;

import com.google.common.base.Objects;

public abstract class AbstractStoreT<K, V> {

    public abstract IStore<K, V> getStore() throws Exception;

    public abstract List<V> getValues(int numValues);

    public abstract List<K> getKeys(int numKeys);

    public List<String> getStrings(int numKeys, int size) {
        List<String> ts = new ArrayList<String>(numKeys);
        for(int i = 0; i < numKeys; i++)
            ts.add(randomLetters(size));
        return ts;
    }

    public List<byte[]> getByteValues(int numValues, int size) {
        List<byte[]> values = new ArrayList<byte[]>();
        for(int i = 0; i < numValues; i++)
            values.add(TUtils.randomBytes(size));
        return values;
    }

    public List<ByteArray> getByteArrayValues(int numValues, int size) {
        List<ByteArray> values = new ArrayList<ByteArray>();
        for(int i = 0; i < numValues; i++)
            values.add(new ByteArray(TUtils.randomBytes(size)));
        return values;
    }

    public K getKey() {
        return getKeys(1).get(0);
    }

    public V getValue() {
        return getValues(1).get(0);
    }

    public IVersion getExpectedVersionAfterPut(IVersion version) {
        return version;
    }

    protected boolean valuesEqual(V t1, V t2) {
        if (t1 instanceof byte[]) return Arrays.equals((byte[])t1, (byte[])t2);
        return Objects.equal(t1, t2);
    }

    protected void bassertEquals(String message, Versioned<V> v1, Versioned<V> v2) {
        String assertTrueMessage = v1 + " != " + v2 + ".";
        if(message != null)
            assertTrueMessage += message;
        assertTrue(assertTrueMessage, valuesEqual(v1.getValue(), v2.getValue()));
        assertEquals(message, v1.getVersion(), v2.getVersion());
    }

    protected void bassertEquals(Versioned<V> v1, Versioned<V> v2) {
        bassertEquals(null, v1, v2);
    }

    public void assertContains(Collection<Versioned<V>> collection, Versioned<V> value) {
        boolean found = false;
        for(Versioned<V> t: collection)
            if(valuesEqual(t.getValue(), value.getValue()))
                found = true;
        assertTrue(collection + " does not contain " + value + ".", found);
    }

    @Test
    public void testNullKeys() throws Exception {
        IStore<K, V> store = getStore();
        try {
            store.put(null, new Versioned<V>(getValue()));
            fail("Store should not put null keys!");
        } catch(IllegalArgumentException e) {
            // this is good
        }
        try {
            store.get(null);
            fail("Store should not get null keys!");
        } catch(IllegalArgumentException e) {
            // this is good
        }
    }

    @Test
    public void testPutNullValue() throws Exception {
        IStore<K,V> store = getStore();
        K key = getKey();
        store.put(key, new Versioned<V>(null));
        List<Versioned<V>> found = store.get(key);
        assertEquals("Wrong number of values.", 1, found.size());
        assertEquals("Returned non-null value.", null,
                     found.get(0).getValue());
    }

    @Test
    public void testGetAndDeleteNonExistentKey() throws Exception {
        K key = getKey();
        IStore<K, V> store = getStore();
        List<Versioned<V>> found = store.get(key);
        assertEquals("Found non-existent key: " + found, 0, found.size());
    }

    private void testObsoletePutFails(String message,
                                      IStore<K, V> store,
                                      K key,
                                      Versioned<V> versioned) throws SyncException {
        VectorClock clock = (VectorClock) versioned.getVersion();
        clock = clock.clone();
        try {
            store.put(key, versioned);
            fail(message);
        } catch(ObsoleteVersionException e) {
            // this is good, but check that we didn't fuck with the version
            assertEquals(clock, versioned.getVersion());
        }
    }

    @Test
    public void testFetchedEqualsPut() throws Exception {
        K key = getKey();
        IStore<K, V> store = getStore();
        VectorClock clock = getClock(1, 1, 2, 3, 3, 4);
        V value = getValue();
        assertEquals("Store not empty at start!", 0, store.get(key).size());
        Versioned<V> versioned = new Versioned<V>(value, clock);
        store.put(key, versioned);
        List<Versioned<V>> found = store.get(key);
        assertEquals("Should only be one version stored.", 1, found.size());
        assertTrue("Values not equal!", valuesEqual(versioned.getValue(), found.get(0).getValue()));
    }

    @Test
    public void testVersionedPut() throws Exception {
        K key = getKey();
        IStore<K, V> store = getStore();
        VectorClock clock = getClock(1, 1);
        VectorClock clockCopy = clock.clone();
        V value = getValue();
        assertEquals("Store not empty at start!", 0, store.get(key).size());
        Versioned<V> versioned = new Versioned<V>(value, clock);

        // put initial version
        store.put(key, versioned);
        assertContains(store.get(key), versioned);

        // test that putting obsolete versions fails
        testObsoletePutFails("Put of identical version/value succeeded.",
                             store,
                             key,
                             new Versioned<V>(value, clockCopy));
        testObsoletePutFails("Put of identical version succeeded.",
                             store,
                             key,
                             new Versioned<V>(getValue(), clockCopy));
        testObsoletePutFails("Put of obsolete version succeeded.",
                             store,
                             key,
                             new Versioned<V>(getValue(), getClock(1)));
        assertEquals("Should still only be one version in store.", store.get(key).size(), 1);
        assertContains(store.get(key), versioned);

        // test that putting a concurrent version succeeds
        if(allowConcurrentOperations()) {
            store.put(key, new Versioned<V>(getValue(), getClock(1, 2)));
            assertEquals(2, store.get(key).size());
        } else {
            try {
                store.put(key, new Versioned<V>(getValue(), getClock(1, 2)));
                fail();
            } catch(ObsoleteVersionException e) {
                // expected
            }
        }

        // test that putting an incremented version succeeds
        Versioned<V> newest = new Versioned<V>(getValue(), getClock(1, 1, 2, 2));
        store.put(key, newest);
        assertContains(store.get(key), newest);
    }

    @Test
    public void testGetVersions() throws Exception {
        List<K> keys = getKeys(2);
        K key = keys.get(0);
        V value = getValue();
        IStore<K, V> store = getStore();
        store.put(key, Versioned.value(value));
        List<Versioned<V>> versioneds = store.get(key);
        List<IVersion> versions = store.getVersions(key);
        assertEquals(1, versioneds.size());
        assertTrue(versions.size() > 0);
        for(int i = 0; i < versions.size(); i++)
            assertEquals(versioneds.get(0).getVersion(), versions.get(i));

        assertEquals(0, store.getVersions(keys.get(1)).size());
    }

    @Test
    public void testCloseIsIdempotent() throws Exception {
        IStore<K, V> store = getStore();
        store.close();
        // second close is okay, should not throw an exception
        store.close();
    }

    @Test
    public void testEntries() throws Exception {
        IStore<K, V> store = getStore();
        int putCount = 537;
        List<K> keys = getKeys(putCount);
        List<V> values = getValues(putCount);
        assertEquals(putCount, values.size());
        for(int i = 0; i < putCount; i++)
            store.put(keys.get(i), new Versioned<V>(values.get(i)));
        
        HashMap<K, V> map = new HashMap<K, V>();
        for (int i = 0; i < keys.size(); i++) {
            map.put(keys.get(i), values.get(i));
        }

        IClosableIterator<Entry<K, List<Versioned<V>>>> iter = store.entries();
        int size = 0;
        try {
            while (iter.hasNext()) {
                Entry<K, List<Versioned<V>>> e = iter.next();
                size += 1;
                assertGetAllValues(map.get(e.getKey()), e.getValue());

            }
        } finally {
            iter.close();
        }
        assertEquals("Number of entries", keys.size(), size);
    }

    protected void assertGetAllValues(V expectedValue, List<Versioned<V>> versioneds) {
        assertEquals(1, versioneds.size());
        valuesEqual(expectedValue, versioneds.get(0).getValue());
    }

    protected boolean allowConcurrentOperations() {
        return true;
    }
}
