package org.sdnplatform.sync.internal.remote;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.RemoteStoreException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.SyncRuntimeException;
import org.sdnplatform.sync.internal.rpc.TProtocolUtil;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.store.StoreUtils;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.util.Pair;
import org.sdnplatform.sync.thrift.AsyncMessageHeader;
import org.sdnplatform.sync.thrift.SyncMessage;
import org.sdnplatform.sync.thrift.CursorRequestMessage;
import org.sdnplatform.sync.thrift.GetRequestMessage;
import org.sdnplatform.sync.thrift.KeyedValues;
import org.sdnplatform.sync.thrift.MessageType;
import org.sdnplatform.sync.thrift.PutRequestMessage;


/**
 * A store implementation that will connect to a remote sync instance
 * @author readams
 */
public class RemoteStore implements IStore<ByteArray, byte[]> {

    private String storeName;
    private RemoteSyncManager syncManager;

    public RemoteStore(String storeName, RemoteSyncManager syncManager) {
        super();
        this.storeName = storeName;
        this.syncManager = syncManager;
    }

    // *************************
    // IStore<ByteArray, byte[]>
    // *************************

    @Override
    public List<Versioned<byte[]>> get(ByteArray key) throws SyncException {
        StoreUtils.assertValidKey(key);
        GetRequestMessage grm = new GetRequestMessage();

        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(syncManager.getTransactionId());
        grm.setHeader(header);

        grm.setKey(key.get());
        grm.setStoreName(storeName);
        
        SyncMessage bsm = new SyncMessage(MessageType.GET_REQUEST);
        bsm.setGetRequest(grm);

        SyncReply reply = getReply(header.getTransactionId(), bsm);

        return reply.getValues();
    }

    @Override
    public IClosableIterator<Entry<ByteArray, List<Versioned<byte[]>>>>
            entries() {
        return new RemoteIterator();
    }

    @Override
    public void put(ByteArray key, Versioned<byte[]> value) 
            throws SyncException {
        StoreUtils.assertValidKey(key);
        PutRequestMessage prm = new PutRequestMessage();

        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(syncManager.getTransactionId());
        prm.setHeader(header);
        prm.setVersionedValue(TProtocolUtil.getTVersionedValue(value));
        prm.setKey(key.get());
        prm.setStoreName(storeName);
        
        SyncMessage bsm = new SyncMessage(MessageType.PUT_REQUEST);
        bsm.setPutRequest(prm);
        
        getReply(header.getTransactionId(), bsm);
    }

    @Override
    public List<IVersion> getVersions(ByteArray key) throws SyncException {
        List<Versioned<byte[]>> values = get(key);
        ArrayList<IVersion> versions = new ArrayList<IVersion>();
        for (Versioned<byte[]> v : values) {
            versions.add(v.getVersion());
        }
        return versions;
    }

    @Override
    public String getName() {
        return storeName;
    }

    @Override
    public void close() throws SyncException {
        
    }

    // *************
    // Local methods
    // *************
    
    private SyncReply getReply(int xid,
                                  SyncMessage bsm) 
            throws SyncException {
        SyncReply reply = null;
        try {
            Future<SyncReply> future = 
                    syncManager.sendRequest(xid, bsm);
            reply = future.get(5, TimeUnit.SECONDS);
            
        } catch (TimeoutException e) {
            throw new RemoteStoreException("Timed out on operation", e);
        } catch (Exception e) {
            throw new RemoteStoreException("Error while waiting for reply", e);
        }

        if (reply.getError() != null)
            throw reply.getError();

        return reply;
    }
    
    private class RemoteIterator 
        implements IClosableIterator<Entry<ByteArray, 
                                           List<Versioned<byte[]>>>> {

        private final int cursorId;
        Iterator<KeyedValues> currentChunk;
        
        public RemoteIterator() {
            CursorRequestMessage crm = getCRM();
            crm.setStoreName(storeName);
            SyncMessage bsm = new SyncMessage(MessageType.CURSOR_REQUEST);
            bsm.setCursorRequest(crm);
            SyncReply reply;
            try {
                reply = getReply(crm.getHeader().getTransactionId(), 
                                 bsm);
            } catch (SyncException e) {
                throw new SyncRuntimeException(e);
            }
            this.cursorId = reply.getIntValue();
            if (reply.getKeyedValues() != null)
                currentChunk = reply.getKeyedValues().iterator();
        }

        @Override
        public boolean hasNext() {
            if (currentChunk != null) {
                if (currentChunk.hasNext())
                    return true;
            }
            Iterator<KeyedValues> nextChunk = getChunk();
            if (nextChunk != null) {
                currentChunk = nextChunk;
                return nextChunk.hasNext();
            }
            return false;
        }

        @Override
        public Entry<ByteArray, List<Versioned<byte[]>>> next() {
            if (!hasNext()) throw new NoSuchElementException();
            KeyedValues kv = currentChunk.next();
            
            ByteArray k = new ByteArray(kv.getKey());
            List<Versioned<byte[]>> v = 
                    TProtocolUtil.getVersionedList(kv.getValues());
            return new Pair<ByteArray, List<Versioned<byte[]>>>(k, v);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            CursorRequestMessage crm = getCRM();
            crm.setCursorId(cursorId);
            crm.setClose(true);
            SyncMessage bsm = new SyncMessage(MessageType.CURSOR_REQUEST);
            bsm.setCursorRequest(crm);
            try {
                getReply(crm.getHeader().getTransactionId(), 
                         bsm);
            } catch (SyncException e) {
                throw new SyncRuntimeException(e);
            }
        }
        
        private Iterator<KeyedValues> getChunk() {
            CursorRequestMessage crm = getCRM();
            crm.setCursorId(cursorId);
            SyncMessage bsm = new SyncMessage(MessageType.CURSOR_REQUEST);
            bsm.setCursorRequest(crm);

            SyncReply reply;
            try {
                reply = getReply(crm.getHeader().getTransactionId(), 
                                              bsm);
            } catch (SyncException e) {
                throw new SyncRuntimeException(e);
            }
            if (reply.getKeyedValues() == null || 
                reply.getKeyedValues().size() == 0) return null;

            return reply.getKeyedValues().iterator();
        }

        private CursorRequestMessage getCRM() {
            CursorRequestMessage crm = new CursorRequestMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(syncManager.getTransactionId());
            crm.setHeader(header);
            return crm;
        }
    }
}
