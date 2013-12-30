package org.sdnplatform.sync.internal;

import java.util.List;

import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.version.VectorClock;


public abstract class AbstractStoreClient<K,V> implements IStoreClient<K, V> {

    @Override
    public V getValue(K key) throws SyncException {
        return getValue(key, null);
    }

    @Override
    public V getValue(K key, V defaultValue) throws SyncException {
        Versioned<V> val = get(key);
        if (val == null || val.getValue() == null) return defaultValue;
        return val.getValue();
    }
    
    /**
     * Get the versions for a key
     * @param key the key
     * @return the versions
     * @throws SyncException
     */
    protected abstract List<IVersion> getVersions(K key) throws SyncException;
    
    @Override
    public Versioned<V> get(K key) throws SyncException {
        return get(key, null);
    }

    @Override
    public IVersion put(K key, V value) throws SyncException {
        List<IVersion> versions = getVersions(key);
        Versioned<V> versioned;
        if(versions.isEmpty())
            versioned = Versioned.value(value, new VectorClock());
        else if(versions.size() == 1)
            versioned = Versioned.value(value, versions.get(0));
        else {
            versioned = get(key, null);
            if(versioned == null)
                versioned = Versioned.value(value, new VectorClock());
            else
                versioned.setValue(value);
        }
        return put(key, versioned);
    }

    @Override
    public boolean putIfNotObsolete(K key, Versioned<V> versioned)
            throws SyncException {
        try {
            put(key, versioned);
            return true;
        } catch (ObsoleteVersionException e) {
            return false;
        }
    }

    @Override
    public void delete(K key) throws SyncException {
        put(key, (V)null);
    }

    @Override
    public void delete(K key, IVersion version) throws SyncException {
        put(key, new Versioned<V>(null, version));
    }

}
