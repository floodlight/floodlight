package org.sdnplatform.sync;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.UnknownStoreException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;


import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * The sync service provides a high-performance in-memory database for
 * fault and partition-tolerant replication of state data.  It provides
 * eventually consistent semantics with versioning through vector clocks
 * and allows custom handling of resolution of inconsistent writes.
 * 
 * An important caveat to keep in mind is that keys should not be constructed
 * using any hash tables, because the serialized version will contain a
 * a potentially-inconsistent ordering of elements resulting in a failure
 * for keys to match.  Using a java bean or a {@link JsonNode} will avoid this
 * problem.  Using strings as keys also avoids this problem.
 * @author readams
 */
public interface ISyncService extends IFloodlightService {
    public enum Scope {
        /**
         * Stores with this scope will be replicated to all nodes in the 
         * cluster
         */
        GLOBAL,
        /**
         * Stores with this scope will be replicated only to other nodes
         * within the writing node's local domain
         */
        LOCAL,
        /**
         * Stores with this scope will not be replicated and will be stored
         * locally only.
         */
        UNSYNCHRONIZED
    }

    /**
     * Create a store with the given store name and scope
     * @param storeName the name of the store
     * @param scope the distribution scope for the data
     * @throws SyncException 
     */
    public void registerStore(String storeName, Scope scope) 
            throws SyncException;

    /**
     * Create a store with the given store name and scope that will be 
     * persistent across reboots.  The performance will be dramatically slower
     * @param storeName the name of the store
     * @param scope the distribution scope for the data
     */
    public void registerPersistentStore(String storeName, Scope scope) 
            throws SyncException;

    /**
     * Get a store client for the given store.  The store client will use
     * a default inconsistency resolution strategy which will use the
     * timestamps of any concurrent updates and choose the later update
     * @param storeName the name of the store to retrieve
     * @param keyClass the class for the underlying key needed for
     * deserialization
     * @param valueClass the class for the underlying value needed for
     * deserialization
     * @return the store client
     * @throws UnknownStoreException
     */
    public <K, V> IStoreClient<K, V> getStoreClient(String storeName,
                                                    Class<K> keyClass,
                                                    Class<V> valueClass)
                               throws UnknownStoreException;

    /**
     * Get a store client that will use the provided inconsistency resolver
     * to resolve concurrent updates.
     * @param storeName the name of the store to retrieve
     * @param keyClass the class for the underlying key needed for
     * deserialization
     * @param valueClass the class for the underlying value needed for
     * deserialization
     * @param resolver the inconsistency resolver to use for the store
     * @return the store client
     * @throws UnknownStoreException
     */
    public <K, V> IStoreClient<K, V>
        getStoreClient(String storeName,
                       Class<K> keyClass,
                       Class<V> valueClass,
                       IInconsistencyResolver<Versioned<V>> resolver)
                               throws UnknownStoreException;

    /**
     * Get a store client for the given store.  The store client will use
     * a default inconsistency resolution strategy which will use the
     * timestamps of any concurrent updates and choose the later update
     * @param storeName the name of the store to retrieve
     * @param keyType the type reference for the underlying key needed for
     * deserialization
     * @param valueType the type reference for the underlying value needed for
     * deserialization
     * @return the store client
     * @throws UnknownStoreException
     */
    public <K, V> IStoreClient<K, V> getStoreClient(String storeName,
                                                    TypeReference<K> keyType,
                                                    TypeReference<V> valueType)
                               throws UnknownStoreException;

    /**
     * Get a store client that will use the provided inconsistency resolver
     * to resolve concurrent updates.
     * @param storeName the name of the store to retrieve
     * @param keyType the type reference for the underlying key needed for
     * deserialization
     * @param valueType the type reference for the underlying value needed for
     * deserialization
     * @param resolver the inconsistency resolver to use for the store
     * @return the store client
     * @throws UnknownStoreException
     */
    public <K, V> IStoreClient<K, V>
        getStoreClient(String storeName,
                       TypeReference<K> keyType,
                       TypeReference<V> valueType,
                       IInconsistencyResolver<Versioned<V>> resolver)
                               throws UnknownStoreException;

}
