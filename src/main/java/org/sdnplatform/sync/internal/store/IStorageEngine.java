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

import java.util.List;
import java.util.Map.Entry;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.SyncException;


/**
 * A base storage class which is actually responsible for data persistence. This
 * interface implies all the usual responsibilities of a Store implementation,
 * and in addition
 * <ol>
 * <li>The implementation MUST throw an ObsoleteVersionException if the user
 * attempts to put a version which is strictly before an existing version
 * (concurrent is okay)</li>
 * <li>The implementation MUST increment this version number when the value is
 * stored.</li>
 * <li>The implementation MUST contain an ID identifying it as part of the
 * cluster</li>
 * </ol>
 *
 * A hash value can be produced for known subtrees of a StorageEngine
 *
 *
 * @param <K> The type of the key being stored
 * @param <V> The type of the value being stored
 * @param <T> The type of the transforms
 *
 */
public interface IStorageEngine<K, V> extends IStore<K, V> {

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
     * Get an iterator over keys in the store.
     *
     * Note that the iterator need not be threadsafe, and that it must be
     * manually closed after use.
     *
     * @return An iterator over the keys in this StorageEngine.
     */
    public IClosableIterator<K> keys();

    /**
     * Truncate all entries in the store.  Note that this is a purely local
     * operation and all the data will sync back over of it's connected
     * @throws SyncException 
     */
    public void truncate() throws SyncException;

    /**
     * Write the given versioned values into the given key.
     * @param key the key
     * @param values the list of versions for that key
     * @return true if any of the values were new and not obsolete
     * @throws SyncException
     */
    public boolean writeSyncValue(K key, Iterable<Versioned<V>> values);
    
    /**
     * Perform any periodic cleanup tasks that might need to be performed.
     * This method will be called periodically by the sync manager
     * @throws SyncException 
     */
    public void cleanupTask() throws SyncException;
    
    /**
     * Returns true if the underlying data store is persistent
     * @return whether the store is persistent
     */
    public boolean isPersistent();

    /**
     * Set the interval after which tombstones will be cleaned up.  This
     * imposes an upper bound on the amount of time that two partitions can
     * be separate before reaching consistency for any given key.
     * @param interval the interval in milliseconds
     */
    void setTombstoneInterval(int interval);
}
