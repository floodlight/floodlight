package org.sdnplatform.sync.internal.rpc;

import java.util.ArrayList;
import java.util.List;

import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.version.ClockEntry;
import org.sdnplatform.sync.internal.version.VectorClock;
import org.sdnplatform.sync.thrift.AsyncMessageHeader;
import org.sdnplatform.sync.thrift.SyncMessage;
import org.sdnplatform.sync.thrift.KeyedValues;
import org.sdnplatform.sync.thrift.KeyedVersions;
import org.sdnplatform.sync.thrift.MessageType;
import org.sdnplatform.sync.thrift.Store;
import org.sdnplatform.sync.thrift.SyncOfferMessage;
import org.sdnplatform.sync.thrift.SyncValueMessage;
import org.sdnplatform.sync.thrift.VersionedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Some utility methods for constructing Thrift messages
 * @author readams
 */
public class TProtocolUtil {
    protected static Logger logger =
            LoggerFactory.getLogger(TProtocolUtil.class.getName());
    
    /**
     * Convert a {@link VectorClock} into a 
     * {@link org.sdnplatform.sync.thrift.VectorClock}
     * @param vc the input clock
     * @return the output thrift object
     */
    public static org.sdnplatform.sync.thrift.VectorClock
        getTVectorClock(VectorClock vc) {
        org.sdnplatform.sync.thrift.VectorClock tvc =
                new org.sdnplatform.sync.thrift.VectorClock();
        tvc.setTimestamp(vc.getTimestamp());
        for (ClockEntry ce : vc.getEntries()) {
            org.sdnplatform.sync.thrift.ClockEntry tce =
                    new org.sdnplatform.sync.thrift.ClockEntry();
            tce.setNodeId(ce.getNodeId());
            tce.setVersion(ce.getVersion());
            tvc.addToVersions(tce);
        }
        
        return tvc;
    }
    
    /**
     * Allocate a thrift {@link org.sdnplatform.sync.thrift.VersionedValue}
     * object wrapping a {@link Versioned} object
     * @param value the value to wrap
     * @return the thrift object
     */
    public static org.sdnplatform.sync.thrift.VersionedValue
        getTVersionedValue(Versioned<byte[]> value) {

        org.sdnplatform.sync.thrift.VersionedValue tvv =
                new org.sdnplatform.sync.thrift.VersionedValue();
        org.sdnplatform.sync.thrift.VectorClock tvc = 
                getTVectorClock((VectorClock)value.getVersion());
        
        tvv.setVersion(tvc);
        tvv.setValue(value.getValue());

        return tvv;
    }

    /**
     * Construct a thrift {@link org.sdnplatform.sync.thrift.KeyedValues}
     * @param key the key
     * @param value the versioned values
     * @return the thrift object
     */
    @SafeVarargs
	public static KeyedValues getTKeyedValues(ByteArray key, 
                                              Versioned<byte[]>... value) {
        KeyedValues kv = new KeyedValues();
        kv.setKey(key.get());
        for (Versioned<byte[]> v : value) {
            kv.addToValues(getTVersionedValue(v));
        }
        return kv;
    }
    
    /**
     * Construct a thrift {@link org.sdnplatform.sync.thrift.KeyedValues}
     * @param key the key
     * @param values the versioned values
     * @return the thrift object
     */
    public static KeyedValues 
            getTKeyedValues(ByteArray key, 
                            Iterable<Versioned<byte[]>> values) {
        KeyedValues kv = new KeyedValues();
        kv.setKey(key.get());
        for (Versioned<byte[]> v : values) {
            kv.addToValues(getTVersionedValue(v));
        }
        return kv;
    }
    
    /**
     * Construct a thrift {@link org.sdnplatform.sync.thrift.KeyedValues}
     * @param key the key
     * @param value the versioned values
     * @return the thrift object
     */
    public static KeyedVersions 
            getTKeyedVersions(ByteArray key, List<Versioned<byte[]>> values) {
        KeyedVersions kv = new KeyedVersions();
        kv.setKey(key.get());
        for (Versioned<byte[]> v : values) {
            kv.addToVersions(getTVectorClock((VectorClock)v.getVersion()));
        }
        return kv;
    }
   
    /**
     * Allocate a thrift {@link org.sdnplatform.sync.thrift.Store} object
     * for the current store
     * @param storeName the name of the store
     * @param scope the scope of the store
     * @param persist whether the store is persistent
     * @return the object
     */
    public static org.sdnplatform.sync.thrift.Store getTStore(String storeName,
                                                               Scope scope, 
                                                               boolean persist) {
        return getTStore(storeName, getTScope(scope), persist);
    }
    
    /**
     * Allocate a thrift {@link org.sdnplatform.sync.thrift.Store} object
     * for the current store
     * @param storeName the name of the store
     * @param scope the scope of the store
     * @param persist whether the store is persistent
     * @return the object
     */
    public static org.sdnplatform.sync.thrift.Store 
            getTStore(String storeName,
                      org.sdnplatform.sync.thrift.Scope scope,
                      boolean persist) {
        org.sdnplatform.sync.thrift.Store store =
                new org.sdnplatform.sync.thrift.Store();
        store.setScope(scope);
        store.setStoreName(storeName);
        store.setPersist(persist);
        return store;
    }

    /**
     * Convert a {@link org.sdnplatform.sync.thrift.Scope} into a 
     * {@link Scope}
     * @param tScope the {@link org.sdnplatform.sync.thrift.Scope} to convert
     * @return the resulting {@link Scope}
     */
    public static Scope getScope(org.sdnplatform.sync.thrift.Scope tScope) {
        switch (tScope) {
            case LOCAL:
                return Scope.LOCAL;
            case UNSYNCHRONIZED:
                return Scope.UNSYNCHRONIZED;                
            case GLOBAL:
            default:
                return Scope.GLOBAL;
        }
    }

    /**
     * Convert a {@link Scope} into a 
     * {@link org.sdnplatform.sync.thrift.Scope}
     * @param tScope the {@link Scope} to convert
     * @return the resulting {@link org.sdnplatform.sync.thrift.Scope}
     */
    public static org.sdnplatform.sync.thrift.Scope getTScope(Scope Scope) {
        switch (Scope) {
            case LOCAL:
                return org.sdnplatform.sync.thrift.Scope.LOCAL;
            case UNSYNCHRONIZED:
                return org.sdnplatform.sync.thrift.Scope.UNSYNCHRONIZED;
            case GLOBAL:
            default:
                return org.sdnplatform.sync.thrift.Scope.GLOBAL;
        }
    }
    
    /**
     * Get a partially-initialized {@link SyncValueMessage} wrapped with a 
     * {@link SyncMessage}.  The values will not be set in the
     * {@link SyncValueMessage}, and the transaction ID will not be set in 
     * the {@link AsyncMessageHeader}.
     * @param storeName the store name
     * @param scope the scope
     * @param persist whether the store is persistent
     * @return the {@link SyncMessage}
     */
    public static SyncMessage getTSyncValueMessage(String storeName, 
                                                      Scope scope,
                                                      boolean persist) {
        return getTSyncValueMessage(getTStore(storeName, scope, persist));
    }

    /**
     * Get a partially-initialized {@link SyncValueMessage} wrapped with a 
     * {@link SyncMessage}.  The values will not be set in the
     * {@link SyncValueMessage}, and the transaction ID will not be set in 
     * the {@link AsyncMessageHeader}.
     * @param store the {@link Store} associated with the message
     * @return the {@link SyncMessage}
     */
    public static SyncMessage getTSyncValueMessage(Store store) {
        SyncMessage bsm = 
                new SyncMessage(MessageType.SYNC_VALUE);
        AsyncMessageHeader header = new AsyncMessageHeader();
        SyncValueMessage svm = new SyncValueMessage();
        svm.setHeader(header);
        svm.setStore(store);

        bsm.setSyncValue(svm);
        return bsm;
    }
    
    /**
     * Get a partially-initialized {@link SyncOfferMessage} wrapped with a 
     * {@link SyncMessage}.
     * @param storeName the name of the store associated with the message
     * @param scope the {@link Scope} for the store
     * @param persist the scope for the store 
     * @return the {@link SyncMessage}
     */
    public static SyncMessage getTSyncOfferMessage(String storeName,
                                                      Scope scope,
                                                      boolean persist) {
        SyncMessage bsm = new SyncMessage(MessageType.SYNC_OFFER);
        AsyncMessageHeader header = new AsyncMessageHeader();
        SyncOfferMessage som = new SyncOfferMessage();
        som.setHeader(header);
        som.setStore(getTStore(storeName, scope, persist));
        
        bsm.setSyncOffer(som);
        return bsm;
    }

    /**
     * Convert a thrift {@link org.sdnplatform.sync.thrift.VectorClock} into
     * a {@link VectorClock}.
     * @param tvc the {@link org.sdnplatform.sync.thrift.VectorClock}
     * @param the {@link VectorClock}
     */
    public static VectorClock getVersion(org.sdnplatform.sync.thrift.VectorClock tvc) {
        ArrayList<ClockEntry> entries =
                new ArrayList<ClockEntry>();
        if (tvc.getVersions() != null) {
            for (org.sdnplatform.sync.thrift.ClockEntry ce :
                tvc.getVersions()) {
                entries.add(new ClockEntry(ce.getNodeId(), ce.getVersion()));
            }
        }
        return new VectorClock(entries, tvc.getTimestamp());
    }
    
    /**
     * Convert a thrift {@link VersionedValue} into a {@link Versioned}.
     * @param tvv the {@link VersionedValue}
     * @return the {@link Versioned}
     */
    public static Versioned<byte[]> 
            getVersionedValued(VersionedValue tvv) {
                Versioned<byte[]> vv =
                new Versioned<byte[]>(tvv.getValue(), 
                                      getVersion(tvv.getVersion()));
        return vv;
    }

    /**
     * Convert from a list of {@link VersionedValue} to a list 
     * of {@link Versioned<byte[]>}
     * @param tvv the list of versioned values
     * @return the list of versioned
     */
    public static List<Versioned<byte[]>> getVersionedList(List<VersionedValue> tvv) {
        ArrayList<Versioned<byte[]>> values = 
                new ArrayList<Versioned<byte[]>>();
        if (tvv != null) {
            for (VersionedValue v : tvv) {
                values.add(TProtocolUtil.getVersionedValued(v));
            }
        }
        return values;
    }
}
