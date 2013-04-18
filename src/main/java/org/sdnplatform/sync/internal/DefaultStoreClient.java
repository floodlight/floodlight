package org.sdnplatform.sync.internal;

import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IInconsistencyResolver;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.InconsistentDataException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.UnknownStoreException;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.store.MappingStoreListener;
import org.sdnplatform.sync.internal.util.Pair;
import org.sdnplatform.sync.internal.version.ChainedResolver;
import org.sdnplatform.sync.internal.version.TimeBasedInconsistencyResolver;
import org.sdnplatform.sync.internal.version.VectorClock;
import org.sdnplatform.sync.internal.version.VectorClockInconsistencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Default implementation of a store client used for accessing a store
 * locally in process.
 * @author readams
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class DefaultStoreClient<K, V> extends AbstractStoreClient<K, V> {
    protected static final Logger logger =
            LoggerFactory.getLogger(DefaultStoreClient.class.getName());

    private IStore<K, V> delegate;
    private IInconsistencyResolver<Versioned<V>> resolver;
    private AbstractSyncManager syncManager;
    private Class<K> keyClass;
    private TypeReference<K> keyType;

    @SuppressWarnings("unchecked")
    public DefaultStoreClient(IStore<K, V> delegate,
                              IInconsistencyResolver<Versioned<V>> resolver,
                              AbstractSyncManager syncManager,
                              Class<K> keyClass,
                              TypeReference<K> keyType) {
        super();
        this.delegate = delegate;
        this.syncManager = syncManager;
        this.keyClass = keyClass;
        this.keyType = keyType;
        
        IInconsistencyResolver<Versioned<V>> vcir =
                new VectorClockInconsistencyResolver<V>();
        IInconsistencyResolver<Versioned<V>> secondary = resolver;
        if (secondary == null)
            secondary = new TimeBasedInconsistencyResolver<V>();
        this.resolver = new ChainedResolver<Versioned<V>>(vcir, secondary);
    }

    // ******************
    // IStoreClient<K,V>
    // ******************

    @Override
    public Versioned<V> get(K key, Versioned<V> defaultValue) 
            throws SyncException {
        List<Versioned<V>> raw = delegate.get(key);
        return handleGet(key, defaultValue, raw);
    }

    @Override
    public IClosableIterator<Entry<K, Versioned<V>>> entries() throws SyncException {
        return new StoreClientIterator(delegate.entries());
    }

    @Override
    public IVersion put(K key, Versioned<V> versioned)
            throws SyncException {
        VectorClock vc = (VectorClock)versioned.getVersion();

        vc = vc.incremented(syncManager.getLocalNodeId(),
                            System.currentTimeMillis());
        versioned = Versioned.value(versioned.getValue(), vc);

        delegate.put(key, versioned);
        return versioned.getVersion();
    }

    @Override
    public void addStoreListener(IStoreListener<K> listener) {
        if (listener == null)
            throw new IllegalArgumentException("Must include listener");
        MappingStoreListener msl = 
                new MappingStoreListener(keyType, keyClass, listener);
        try {
            syncManager.addListener(delegate.getName(), msl);
        } catch (UnknownStoreException e) {
            // this shouldn't happen since we already have a store client,
            // unless the store has been deleted somehow
            logger.error("Unexpected internal state: unknown store " +
                         "from store client.  Could not register listener", e);
        }
    }

    // ************************
    // AbstractStoreClient<K,V>
    // ************************

    @Override
    protected List<IVersion> getVersions(K key) throws SyncException {
        return delegate.getVersions(key);
    }

    // *********************
    // Private local methods
    // *********************

    protected Versioned<V> handleGet(K key,
                                     Versioned<V> defaultValue,
                                     List<Versioned<V>> raw) 
                                             throws InconsistentDataException {
        if (raw == null) return defaultValue(defaultValue);
        List<Versioned<V>> vs = resolver.resolveConflicts(raw);
        return getItemOrThrow(key, defaultValue, vs);
    }
    
    protected Versioned<V> defaultValue(Versioned<V> defaultValue) {
        if (defaultValue == null)
            return Versioned.emptyVersioned();
        return defaultValue;
    }
    
    protected Versioned<V> getItemOrThrow(K key,
                                          Versioned<V> defaultValue,
                                          List<Versioned<V>> items)
             throws InconsistentDataException {
        if(items.size() == 0)
            return defaultValue(defaultValue);
        else if(items.size() == 1)
            return items.get(0);
        else
            throw new InconsistentDataException("Resolver failed to resolve" +
                    " conflict: " + items.size() + " unresolved items", items);
    }
    
    
    protected class StoreClientIterator implements 
        IClosableIterator<Entry<K, Versioned<V>>> {

        IClosableIterator<Entry<K, List<Versioned<V>>>> delegate;
        
        public StoreClientIterator(IClosableIterator<Entry<K, 
                                   List<Versioned<V>>>> delegate) {
            super();
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Entry<K, Versioned<V>> next() {
            Entry<K, List<Versioned<V>>> n = delegate.next();
            try {
                return new Pair<K, Versioned<V>>(n.getKey(),
                                      handleGet(n.getKey(), null, n.getValue()));
            } catch (SyncException e) {
                logger.error("Failed to construct next value", e);
                return null;
            }
        }

        @Override
        public void remove() {
            delegate.remove();
        }

        @Override
        public void close() {
            delegate.close();
        }
        
    }
}
