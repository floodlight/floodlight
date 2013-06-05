/*
 * Copyright 2008-2009 LinkedIn, Inc
 * Copyright 2013 Big Switch Networks, Inc.
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
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.IVersion.Occurred;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.util.Pair;


/**
 * A simple non-persistent, in-memory store.
 */
public class InMemoryStorageEngine<K, V> implements IStorageEngine<K, V> {

    private final ConcurrentMap<K, List<Versioned<V>>> map;
    private final String name;
    
    /**
     * Interval in milliseconds before tombstones will be cleared.
     */
    protected int tombstoneDeletion = 24 * 60 * 60 * 1000;

    public InMemoryStorageEngine(String name) {
        this.name = name;
        this.map = new ConcurrentHashMap<K, List<Versioned<V>>>();
    }

    public InMemoryStorageEngine(String name, 
                                 ConcurrentMap<K, List<Versioned<V>>> map) {
        this.name = name;
        this.map = map;
    }

    // ******************
    // StorageEngine<K,V>
    // ******************

    @Override
    public void close() {}

    @Override
    public List<IVersion> getVersions(K key) throws SyncException {
        return StoreUtils.getVersions(get(key));
    }

    @Override
    public List<Versioned<V>> get(K key) throws SyncException {
        StoreUtils.assertValidKey(key);
        List<Versioned<V>> results = map.get(key);
        if(results == null) {
            return new ArrayList<Versioned<V>>(0);
        }
        synchronized(results) {
            return new ArrayList<Versioned<V>>(results);
        }
    }

    @Override
    public void put(K key, Versioned<V> value) throws SyncException {
        if (!doput(key, value))
            throw new ObsoleteVersionException();
    }

    public boolean doput(K key, Versioned<V> value) throws SyncException {
        StoreUtils.assertValidKey(key);

        IVersion version = value.getVersion();

        while(true) {
            List<Versioned<V>> items = map.get(key);
            // If we have no value, optimistically try to add one
            if(items == null) {
                items = new ArrayList<Versioned<V>>();
                items.add(new Versioned<V>(value.getValue(), version));
                if (map.putIfAbsent(key, items) != null)
                    continue;
                return true;
            } else {
                synchronized(items) {
                    // if this check fails, items has been removed from the map
                    // by delete, so we try again.
                    if(map.get(key) != items)
                        continue;

                    // Check for existing versions - remember which items to
                    // remove in case of success
                    List<Versioned<V>> itemsToRemove = new ArrayList<Versioned<V>>(items.size());
                    for(Versioned<V> versioned: items) {
                        Occurred occurred = value.getVersion().compare(versioned.getVersion());
                        if(occurred == Occurred.BEFORE) {
                            return false;
                        } else if(occurred == Occurred.AFTER) {
                            itemsToRemove.add(versioned);
                        }
                    }
                    items.removeAll(itemsToRemove);
                    items.add(value);
                }
                return true;
            }
        }
    }

    @Override
    public IClosableIterator<Entry<K,List<Versioned<V>>>> entries() {
        return new InMemoryIterator<K, V>(map);
    }

    @Override
    public IClosableIterator<K> keys() {
        // TODO Implement more efficient version.
        return StoreUtils.keys(entries());
    }

    @Override
    public void truncate() {
        map.clear();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean writeSyncValue(K key, Iterable<Versioned<V>> values) {
        boolean success = false;
        for (Versioned<V> value : values) {
            try {
                put (key, value);
                success = true;
            } catch (SyncException e) {
                // ignore
            }
        }
        return success;
    }

    @Override
    public void cleanupTask() {
        // Remove tombstones that are older than the tombstone deletion
        // threshold.  If a value is deleted and the tombstone has been 
        // cleaned up before the cluster is fully synchronized, then there
        // is a chance that deleted values could be resurrected
        Iterator<Entry<K, List<Versioned<V>>>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<K, List<Versioned<V>>> e = iter.next();
            List<Versioned<V>> items = e.getValue();

            synchronized (items) {
                if (StoreUtils.canDelete(items, tombstoneDeletion))
                    iter.remove();
            }
        }
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public void setTombstoneInterval(int interval) {
        this.tombstoneDeletion = interval;
    }

    // *********************
    // InMemoryStorageEngine
    // *********************

    /**
     * Get the number of keys currently in the store
     * @return
     */
    public int size() {
        return map.size();
    }
    
    /**
     * Atomically remove the key and return the value that was mapped to it,
     * if any
     * @param key the key to remove
     * @return the mapped values
     */
    public List<Versioned<V>> remove(K key) {
        while (true) {
            List<Versioned<V>> items = map.get(key);
            synchronized (items) {
                if (map.remove(key, items))
                    return items;                
            }
        }
    }

    /**
     * Check whether the given key is present in the store
     * @param key the key
     * @return <code>true</code> if the key is present
     */
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }
    
    // ******
    // Object
    // ******

    @Override
    public String toString() {
        return toString(15);
    }

    // *************
    // Local methods
    // *************

    protected String toString(int size) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        int count = 0;
        for(Entry<K, List<Versioned<V>>> entry: map.entrySet()) {
            if(count > size) {
                builder.append("...");
                break;
            }
            builder.append(entry.getKey());
            builder.append(':');
            builder.append(entry.getValue());
            builder.append(',');
        }
        builder.append('}');
        return builder.toString();
    }

    private static class InMemoryIterator<K, V> implements 
        IClosableIterator<Entry<K, List<Versioned<V>>>> {

        private final Iterator<Entry<K, List<Versioned<V>>>> iterator;

        public InMemoryIterator(ConcurrentMap<K, List<Versioned<V>>> map) {
            this.iterator = map.entrySet().iterator();
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public Pair<K, List<Versioned<V>>> next() {
            Entry<K, List<Versioned<V>>> entry = iterator.next();
            return new Pair<K, List<Versioned<V>>>(entry.getKey(), 
                    entry.getValue());
        }

        public void remove() {
            throw new UnsupportedOperationException("No removal y'all.");
        }

        @Override
        public void close() {
            // nothing to do
        }
    }
}
