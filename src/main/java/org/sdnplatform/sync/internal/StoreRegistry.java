package org.sdnplatform.sync.internal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.ConnectionPoolDataSource;

import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.PersistException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.store.InMemoryStorageEngine;
import org.sdnplatform.sync.internal.store.JavaDBStorageEngine;
import org.sdnplatform.sync.internal.store.SynchronizingStorageEngine;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Manage registered stores and associated metadata
 * @author readams
 */
public class StoreRegistry {
    protected static final Logger logger =
            LoggerFactory.getLogger(StoreRegistry.class);

    /**
     * The associated syncManager
     */
    private final SyncManager syncManager;

    /**
     * Directory where the persistent store will be located
     */
    private final String dbPath;
    
    /**
     * A data source suitable for use in persistent stores
     */
    private ConnectionPoolDataSource persistentDataSource; 

    /**
     * The storage engines that contain the locally-stored data
     */
    private HashMap<String,SynchronizingStorageEngine> localStores =
            new HashMap<String, SynchronizingStorageEngine>();

    /**
     * Undelivered hints associated with the stores
     */
    private InMemoryStorageEngine<HintKey,byte[]> hints;
    
    /**
     * A queue containing pending hints.  
     */
    private ArrayDeque<HintKey> hintQueue = new ArrayDeque<HintKey>();
    private Lock hintLock = new ReentrantLock();
    private Condition hintsAvailable = hintLock.newCondition();
    
    /**
     * Construct a new {@link StoreRegistry}
     * @param syncManager The associated syncManager
     */
    public StoreRegistry(SyncManager syncManager, String dbPath) {
        super();
        this.syncManager = syncManager;
        this.dbPath = dbPath;
        hints = new InMemoryStorageEngine<HintKey, byte[]>("system-hints");
    }
    
    // **************
    // public methods
    // **************

    /**
     * Get the store associated with the given name, or null if there is no
     * such store
     * @param storeName
     * @return a {@link SynchronizingStorageEngine}
     */
    public SynchronizingStorageEngine get(String storeName) {
        return localStores.get(storeName);
    }

    /**
     * Register a new store with the given name, scope and persistence
     * @param storeName the name of the store
     * @param scope the scope for the store
     * @param persistent whether the store should be persistent
     * @return the newly-allocated store
     * @throws PersistException 
     */
    public synchronized SynchronizingStorageEngine register(String storeName, 
                                                            Scope scope, 
                                                            boolean persistent) 
                                              throws PersistException {
        SynchronizingStorageEngine store =
                localStores.get(storeName);
        if (store != null) {
            return store;
        }
                
        IStorageEngine<ByteArray, byte[]> dstore;
        if (persistent) {
            if (persistentDataSource == null)
                persistentDataSource = JavaDBStorageEngine.getDataSource(dbPath, false);
            dstore = new JavaDBStorageEngine(storeName, persistentDataSource);
        } else {
            dstore = new InMemoryStorageEngine<ByteArray, byte[]>(storeName);
        }
        store = new SynchronizingStorageEngine(dstore, syncManager,
                                               syncManager.debugCounter,
                                               scope);
        localStores.put(storeName, store);
        return store;
    }

    /**
     * Get a collection containing all the currently-registered stores
     * @return the {@link Collection<SynchronizingStorageEngine>}
     */
    public Collection<SynchronizingStorageEngine> values() {
        return localStores.values();
    }
    
    /**
     * Add a key/value to the hint store for the given store
     * @param storeName the name of the store for the keyed value
     * @param key the key
     * @param value the value
     */
    @LogMessageDoc(level="ERROR",
                   message="Failed to queue hint for store {storeName}",
                   explanation="There was an error synchronizing data to " + 
                               "remote nodes",
                   recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public void queueHint(String storeName, 
                          ByteArray key, Versioned<byte[]> value) {
        try {
            HintKey hk = new HintKey(storeName,key);
            hintLock.lock();
            try {
                boolean needed = !hints.containsKey(hk);
                needed &= hints.doput(hk, value);
                if (needed) {
                    hintQueue.add(hk);
                    hintsAvailable.signal();
                }
            } finally {
                hintLock.unlock();
            }
        } catch (SyncException e) {
            logger.error("Failed to queue hint for store " + storeName, e);
        }
    }

    /**
     * Drain up to the given number of hints to the provided collection.
     * This method will block until at least one hint is available
     * @param c the collection to which the hints should be copied
     * @param maxElements the maximum number of hints to drain
     * @throws InterruptedException
     */
    public void takeHints(Collection<Hint> c, int maxElements) 
            throws InterruptedException {
        int count = 0;
        try {
            while (count == 0) {
                hintLock.lock();
                while (hintQueue.isEmpty()) {
                    hintsAvailable.await();
                }
                while (count < maxElements && !hintQueue.isEmpty()) {
                    HintKey hintKey = hintQueue.pollFirst();
                    if (hintKey != null) {
                        List<Versioned<byte[]>> values = hints.remove(hintKey);
                        if (values == null) {
                            continue;
                        }
                        c.add(new Hint(hintKey, values));
                        count += 1;
                    }
                }
            }
        } finally {
            hintLock.unlock();
        }
    }

    public void shutdown() {
        hintQueue.clear();
        hints.close();
    }

    /**
     * A key in the hint store
     * @author readams
     */
    public static class HintKey {
        private final String storeName;
        private final ByteArray key;
        private final short nodeId;
        
        public HintKey(String storeName, 
                       ByteArray key,
                       short nodeId) {
            super();
            this.storeName = storeName;
            this.key = key;
            this.nodeId = nodeId;
        }

        public HintKey(String storeName, 
                       ByteArray key) {
            super();
            this.storeName = storeName;
            this.key = key;
            this.nodeId = -1;
        }
        
        public String getStoreName() {
            return storeName;
        }
        public ByteArray getKey() {
            return key;
        }
        public short getNodeId() {
            return nodeId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + nodeId;
            result = prime * result
                     + ((storeName == null) ? 0 : storeName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            HintKey other = (HintKey) obj;
            if (key == null) {
                if (other.key != null) return false;
            } else if (!key.equals(other.key)) return false;
            if (nodeId != other.nodeId) return false;
            if (storeName == null) {
                if (other.storeName != null) return false;
            } else if (!storeName.equals(other.storeName)) return false;
            return true;
        }
    }
    
    /**
     * A hint representing a hint key and a value
     * @author readams
     */
    public static class Hint {
        private HintKey hintKey;
        private List<Versioned<byte[]>> values;
        public Hint(HintKey hintKey, List<Versioned<byte[]>> values) {
            super();
            this.hintKey = hintKey;
            this.values = values;
        }
        public HintKey getHintKey() {
            return hintKey;
        }
        public List<Versioned<byte[]>> getValues() {
            return values;
        }
    }
}
