package org.sdnplatform.sync.internal.store;

import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This storage engine will asynchronously replicate its data to the other
 * nodes in the cluster based on the scope of the s
 */
public class SynchronizingStorageEngine extends ListenerStorageEngine {

    protected static Logger logger =
                LoggerFactory.getLogger(SynchronizingStorageEngine.class);

    /**
     * The synchronization manager
     */
    protected SyncManager syncManager;

    /**
     * The scope of distribution for data in this store
     */
    protected Scope scope;

    /**
     * Allocate a synchronizing storage engine
     * @param localStorage the local storage
     * @param syncManager the sync manager
     * @param debugCounter the debug counter service
     * @param scope the scope for this store
     * @param rpcService the RPC service
     * @param storeName the name of the store
     */
    public SynchronizingStorageEngine(IStorageEngine<ByteArray,
                                                    byte[]> localStorage,
                                      SyncManager syncManager,
                                      IDebugCounterService debugCounter, 
                                      Scope scope) {
        super(localStorage, debugCounter);
        this.localStorage = localStorage;
        this.syncManager = syncManager;
        this.scope = scope;
    }

    // *************************
    // StorageEngine<Key,byte[]>
    // *************************

    @Override
    public void put(ByteArray key, Versioned<byte[]> value)
            throws SyncException {
        super.put(key, value);
        if (!Scope.UNSYNCHRONIZED.equals(scope))
            syncManager.queueSyncTask(this, key, value);
    }
    
    // **************
    // Public methods
    // **************

    /**
     * Get the scope for this store
     * @return
     */
    public Scope getScope() {
        return scope;
    }
}
