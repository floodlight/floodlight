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

package org.sdnplatform.sync;

import java.util.Iterator;
import java.util.Map.Entry;

import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;


/**
 * The user-facing interface to a sync store. Gives basic put/get/delete
 * plus helper functions.
 *
 * @param <K> The type of the key being stored
 * @param <V> The type of the value being stored
 */
public interface IStoreClient<K, V> {

    /**
     * Get the value associated with the given key or null if there is no value
     * associated with this key. This method strips off all version information
     * and is only useful when no further storage operations will be done on
     * this key. In general, you should prefer the get() method that returns
     * version information unless.
     *
     * @param key The key
     * @throws SyncException
     */
    public V getValue(K key) throws SyncException;

    /**
     * Get the value associated with the given key or defaultValue if there is
     * no value associated with the key. This method strips off all version
     * information and is only useful when no further storage operations will be
     * done on this key.In general, you should prefer the get() method that returns
     * version information unless.
     *
     * @param key The key for which to fetch the associated value
     * @param defaultValue A value to return if there is no value associated
     *        with this key
     * @return Either the value stored for the key or the default value.
     * @throws SyncException
     */
    public V getValue(K key, V defaultValue) throws SyncException;

    /**
     * Get the versioned value associated with the given key.  Note that while
     * this function will never return null, the {@link Versioned} returned
     * can have a null value (i.e. {@link Versioned#getValue() can be null}
     * if the key is not present.
     *
     * @param key The key for which to fetch the value.
     * @return The versioned value
     * @throws SyncException
     */
    public Versioned<V> get(K key) throws SyncException;

    /**
     * Get the versioned value associated with the given key or the defaultValue
     * if no value is associated with the key.
     *
     * @param key The key for which to fetch the value.
     * @return The versioned value, or the defaultValue if no value is stored
     *         for this key.
     * @throws SyncException
     */
    public Versioned<V> get(K key, Versioned<V> defaultValue)
            throws SyncException;

    /**
     * Get an iterator that will get all the entries in the store.  Note
     * that this has the potential to miss any values added while you're
     * iterating through the collection, and it's possible that items will
     * be deleted before you get to the end.
     *
     * Note that you *must* close the {@link IClosableIterator} when you are
     * finished with it or there may be resource leaks.  An example of how you
     * should use this iterator to ensure that it is closed even if there are
     * exceptions follows:
     * <code>
     * IClosableIterator iter = store.entries();
     * try {
     *     // do your iteration
     * } finally {
     *     iter.close();
     * }
     * </code>
     *
     * Another important caveat is that because {@link IClosableIterator}
     * extends {@link Iterator}, there is no checked exception declared in
     * {@link Iterator#next()}.  Because of this, calling
     * {@link Iterator#next()} on the iterator returned here may throw a
     * SyncRuntimeException wrapping a SyncException such as might be
     * returned by {@link IStoreClient#get(Object)}
     * @return
     * @throws SyncException
     */
    public IClosableIterator<Entry<K, Versioned<V>>> entries()
            throws SyncException;

    /**
     * Associated the given value to the key, clobbering any existing values
     * stored for the key.
     * Only use this variant if the write cannot possibly depend on
     * the current value in the store and if there cannot be a concurrent
     * update from multiple threads. Otherwise {@link #put(Object, Versioned)}
     * or {@link #putIfNotObsolete(Object, Versioned)}
     *
     * @param key The key
     * @param value The value
     * @return version The version of the object
     * @throws ObsoleteVersionException
     * @throws SyncException
     */
    public IVersion put(K key, V value) throws SyncException;

    /**
     * Put the given Versioned value into the store for the given key if the
     * version is greater to or concurrent with existing values. Throw an
     * ObsoleteVersionException otherwise.
     *
     * @param key The key
     * @param versioned The value and its versioned
     * @throws ObsoleteVersionException
     * @throws SyncException
     * @throws ObsoleteVersionException if the entry assoicated with the key
     * was locally modified by another thread after the get.
     */
    public IVersion put(K key, Versioned<V> versioned)
            throws SyncException;

    /**
     * Put the versioned value to the key, ignoring any ObsoleteVersionException
     * that may be thrown
     *
     * @param key The key
     * @param versioned The versioned value
     * @return true if the put succeeded
     * @throws SyncException
     */
    public boolean putIfNotObsolete(K key, Versioned<V> versioned)
            throws SyncException;

    /**
     * Delete the key by writing a null tombstone to the store obliterating
     * any existing value stored for the key.
     * Only use this variant if the delete cannot possibly depend on
     * the current value in the store and if there cannot be a concurrent
     * update from multiple threads. Otherwise {@link #delete(Object, IVersion)}
     * should be used.
     *
     * @param key The key
     * @throws SyncException
     */
    public void delete(K key) throws SyncException;

    /**
     * Delete the key by writing a null tombstone to the store using the
     * provided {@link IVersion}.
     *
     * @param key The key to delete
     * @param version The version of the key
     * @throws SyncException
     * @throws ObsoleteVersionException if the entry assoicated with the key
     * was locally modified by another thread after the get.
     */
    public void delete(K key, IVersion version) throws SyncException;

    /**
     * Add a listener that will be notified about changes to the given store.
     * @param listener the {@link IStoreListener} that will receive the
     * notifications
     */
    public void addStoreListener(IStoreListener<K> listener);

}
