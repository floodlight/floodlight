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

import java.util.List;
import java.util.Map.Entry;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.SyncException;


/**
 * The basic interface used for storage and storage decorators. Allows the usual
 * crud operations.
 *
 * Note that certain operations rely on the correct implementation of equals and
 * hashCode for the key. As such, arrays as keys should be avoided.
 *
 *
 */
public interface IStore<K, V> {

    /**
     * Get the value associated with the given key
     *
     * @param key The key to check for
     * @return The value associated with the key or an empty list if no values
     *         are found.
     * @throws SyncException
     */
    public List<Versioned<V>> get(K key) throws SyncException;
    
    /**
     * Get an iterator over pairs of entries in the store. The key is the first
     * element in the pair and the versioned value is the second element.
     *
     * Note that the iterator need not be threadsafe, and that it must be
     * manually closed after use.
     *
     * @return An iterator over the entries in this StorageEngine.
     */
    public IClosableIterator<Entry<K,List<Versioned<V>>>> entries();
    
    /**
     * Associate the value with the key and version in this store
     *
     * @param key The key to use
     * @param value The value to store and its version.
     */
    public void put(K key, Versioned<V> value)
            throws SyncException;

    /**
     * Get a list of the versions associated with the given key
     * @param key the key
     * @return the list of {@link IVersion} objects
     * @throws SyncException
     */
    public List<IVersion> getVersions(K key) throws SyncException;
    
    /**
     * @return The name of the store.
     */
    public String getName();

    /**
     * Close the store.
     *
     * @throws SyncException If closing fails.
     */
    public void close() throws SyncException;
}
