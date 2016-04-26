package org.sdnplatform.sync.internal.rpc;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Map.Entry;

import net.floodlightcontroller.debugcounter.IDebugCounter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.AuthException;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.Cursor;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.config.ClusterConfig;
import org.sdnplatform.sync.internal.config.Node;
import org.sdnplatform.sync.internal.config.SyncStoreCCProvider;
import org.sdnplatform.sync.internal.rpc.RPCService.NodeMessage;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.util.CryptoUtil;
import org.sdnplatform.sync.internal.version.VectorClock;
import org.sdnplatform.sync.thrift.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Channel handler for the RPC service
 * @author readams
 */
public class RPCChannelHandler extends AbstractRPCChannelHandler {
    protected static final Logger logger =
            LoggerFactory.getLogger(RPCChannelHandler.class);

    protected SyncManager syncManager;
    protected RPCService rpcService;
    protected Node remoteNode;
    protected boolean isClientConnection = false;

    public RPCChannelHandler(SyncManager syncManager,
                             RPCService rpcService) {
        super();
        this.syncManager = syncManager;
        this.rpcService = rpcService;
    }

    // ****************************
    // IdleStateAwareChannelHandler
    // ****************************

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        rpcService.getChannelGroup().add(ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (remoteNode != null) {
            rpcService.disconnectNode(remoteNode.getNodeId());
        }
        super.channelInactive(ctx);
    }

    // ******************************************
    // AbstractRPCChannelHandler message handlers
    // ******************************************

    @Override
    protected void handleHello(HelloMessage hello, Channel channel) {
        if (!hello.isSetNodeId()) {
            // this is a client connection.  Don't set this up as a node
            // connection
            isClientConnection = true;
            return;
        }
        remoteNode = syncManager.getClusterConfig().getNode(hello.getNodeId());
        if (remoteNode == null) {
            logger.error("[{}->{}] Attempted connection from unrecognized " +
                         "floodlight node {}; disconnecting",
                         new Object[]{getLocalNodeIdString(),
                                      getRemoteNodeIdString(),
                                      hello.getNodeId()});
            channel.close();
            return;
        }
        rpcService.nodeConnected(remoteNode.getNodeId(), channel);

        FullSyncRequestMessage srm = new FullSyncRequestMessage();
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(getTransactionId());
        srm.setHeader(header);
        SyncMessage bsm = new SyncMessage(MessageType.FULL_SYNC_REQUEST);
        channel.writeAndFlush(bsm);

        // XXX - TODO - if last connection was longer ago than the tombstone
        // timeout, then we need to do a complete flush and reload of our
        // state.  This is complex though since this applies across entire
        // partitions and not just single nodes.  We'd need to identify the
        // partition and nuke the smaller half (or lower priority in the case
        // of an even split).  Downstream listeners would need to be able to
        // handle a state nuke as well. A simple way to nuke would be to ensure
        // floodlight is restarted in the smaller partition.
    }

    @Override
    protected void handleGetRequest(GetRequestMessage request,
                                    Channel channel) {
        String storeName = request.getStoreName();
        try {
            IStorageEngine<ByteArray, byte[]> store =
                    syncManager.getRawStore(storeName);

            GetResponseMessage m = new GetResponseMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            m.setHeader(header);

            List<Versioned<byte[]>> values =
                    store.get(new ByteArray(request.getKey()));
            for (Versioned<byte[]> value : values) {
                m.addToValues(TProtocolUtil.getTVersionedValue(value));
            }

            SyncMessage bsm = new SyncMessage(MessageType.GET_RESPONSE);
            bsm.setGetResponse(m);
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.GET_REQUEST));
        }
    }

    @Override
    protected void handlePutRequest(PutRequestMessage request,
                                    Channel channel) {
        String storeName = request.getStoreName();
        try {
            IStorageEngine<ByteArray, byte[]> store =
                    syncManager.getRawStore(storeName);

            ByteArray key = new ByteArray(request.getKey());
            Versioned<byte[]> value = null;
            if (request.isSetVersionedValue()) {
                value = TProtocolUtil.
                        getVersionedValued(request.getVersionedValue());
                value.increment(syncManager.getLocalNodeId(),
                                System.currentTimeMillis());
            } else if (request.isSetValue()) {
                byte[] rvalue = request.getValue();
                List<IVersion> versions = store.getVersions(key);
                VectorClock newclock = new VectorClock();
                for (IVersion v : versions) {
                    newclock = newclock.merge((VectorClock)v);
                }
                newclock = newclock.incremented(syncManager.getLocalNodeId(),
                                                System.currentTimeMillis());
                value = Versioned.value(rvalue, newclock);
            } else {
                throw new SyncException("No value specified for put");
            }

            store.put(key, value);

            PutResponseMessage m = new PutResponseMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            m.setHeader(header);

            SyncMessage bsm = new SyncMessage(MessageType.PUT_RESPONSE);
            bsm.setPutResponse(m);
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.PUT_REQUEST));
        }
    }

    @Override
    protected void handleDeleteRequest(DeleteRequestMessage request,
                                       Channel channel) {
        try {
            String storeName = request.getStoreName();
            IStorageEngine<ByteArray, byte[]> store =
                    syncManager.getRawStore(storeName);
            ByteArray key = new ByteArray(request.getKey());
            VectorClock newclock;
            if (request.isSetVersion()) {
                newclock = TProtocolUtil.getVersion(request.getVersion());
            } else {
                newclock = new VectorClock();
                List<IVersion> versions = store.getVersions(key);
                for (IVersion v : versions) {
                    newclock = newclock.merge((VectorClock)v);
                }
            }
            newclock =
                    newclock.incremented(rpcService.syncManager.getLocalNodeId(),
                                         System.currentTimeMillis());
            Versioned<byte[]> value = Versioned.value(null, newclock);
            store.put(key, value);

            DeleteResponseMessage m = new DeleteResponseMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            m.setHeader(header);

            SyncMessage bsm =
                    new SyncMessage(MessageType.DELETE_RESPONSE);
            bsm.setDeleteResponse(m);
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.DELETE_REQUEST));
        }
    }

    @Override
    protected void handleSyncValue(SyncValueMessage request,
                                   Channel channel) {
        if (request.isSetResponseTo())
            rpcService.messageAcked(MessageType.SYNC_REQUEST,
                                    getRemoteNodeId());
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("[{}->{}] Got syncvalue {}",
                             new Object[]{getLocalNodeIdString(),
                                          getRemoteNodeIdString(),
                                          request});
            }

            Scope scope = TProtocolUtil.getScope(request.getStore().getScope());
            for (KeyedValues kv : request.getValues()) {
                Iterable<VersionedValue> tvvi = kv.getValues();
                Iterable<Versioned<byte[]>> vs = new TVersionedValueIterable(tvvi);
                syncManager.writeSyncValue(request.getStore().getStoreName(),
                                           scope,
                                           request.getStore().isPersist(),
                                           kv.getKey(), vs);
            }

            SyncValueResponseMessage m = new SyncValueResponseMessage();
            m.setCount(request.getValuesSize());
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            m.setHeader(header);
            SyncMessage bsm =
                    new SyncMessage(MessageType.SYNC_VALUE_RESPONSE);
            bsm.setSyncValueResponse(m);

            updateCounter(SyncManager.counterReceivedValues,
                          request.getValuesSize());
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.SYNC_VALUE));
        }
    }

    @Override
    protected void handleSyncValueResponse(SyncValueResponseMessage message,
                                           Channel channel) {
        rpcService.messageAcked(MessageType.SYNC_VALUE, getRemoteNodeId());
    }

    @Override
    protected void handleSyncOffer(SyncOfferMessage request,
                                   Channel channel) {
        try {
            String storeName = request.getStore().getStoreName();

            SyncRequestMessage srm = new SyncRequestMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            srm.setHeader(header);
            srm.setStore(request.getStore());

            for (KeyedVersions kv : request.getVersions()) {
                Iterable<org.sdnplatform.sync.thrift.VectorClock> tvci =
                        kv.getVersions();
                Iterable<VectorClock> vci = new TVersionIterable(tvci);

                boolean wantKey = syncManager.handleSyncOffer(storeName,
                                                              kv.getKey(), vci);
                if (wantKey)
                    srm.addToKeys(kv.bufferForKey());
            }

            SyncMessage bsm =
                    new SyncMessage(MessageType.SYNC_REQUEST);
            bsm.setSyncRequest(srm);
            if (logger.isTraceEnabled()) {
                logger.trace("[{}->{}] Sending SyncRequest with {} elements",
                             new Object[]{getLocalNodeIdString(),
                                          getRemoteNodeIdString(),
                                          srm.getKeysSize()});
            }
            channel.writeAndFlush(bsm);

        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(),
                                   e, MessageType.SYNC_OFFER));
        }
    }

    @Override
    protected void handleSyncRequest(SyncRequestMessage request,
                                     Channel channel) {
        rpcService.messageAcked(MessageType.SYNC_OFFER, getRemoteNodeId());
        if (!request.isSetKeys()) return;

        String storeName = request.getStore().getStoreName();
        try {
            IStorageEngine<ByteArray, byte[]> store =
                    syncManager.getRawStore(storeName);

            SyncMessage bsm =
                    TProtocolUtil.getTSyncValueMessage(request.getStore());
            SyncValueMessage svm = bsm.getSyncValue();
            svm.setResponseTo(request.getHeader().getTransactionId());
            svm.getHeader().setTransactionId(rpcService.getTransactionId());

            for (ByteBuffer key : request.getKeys()) {
                ByteArray keyArray = new ByteArray(key.array());
                List<Versioned<byte[]>> values =
                        store.get(keyArray);
                if (values == null || values.size() == 0) continue;
                KeyedValues kv =
                        TProtocolUtil.getTKeyedValues(keyArray, values);
                svm.addToValues(kv);
            }

            if (svm.isSetValues()) {
                updateCounter(SyncManager.counterSentValues,
                              svm.getValuesSize());
                rpcService.syncQueue.add(new NodeMessage(getRemoteNodeId(),
                                                         bsm));
            }
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.SYNC_REQUEST));
        }
    }

    @Override
    protected void handleFullSyncRequest(FullSyncRequestMessage request,
                                         Channel channel) {
        startAntientropy();
    }

    @Override
    protected void handleCursorRequest(CursorRequestMessage request,
                                       Channel channel) {
        try {
            Cursor c = null;
            if (request.isSetCursorId()) {
                c = syncManager.getCursor(request.getCursorId());
            } else {
                c = syncManager.newCursor(request.getStoreName());
            }
            if (c == null) {
                throw new SyncException("Unrecognized cursor");
            }

            CursorResponseMessage m = new CursorResponseMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            m.setHeader(header);
            m.setCursorId(c.getCursorId());

            if (request.isClose()) {
                syncManager.closeCursor(c);
            } else {
                int i = 0;
                while (i < 50 && c.hasNext()) {
                    Entry<ByteArray, List<Versioned<byte[]>>> e = c.next();

                    m.addToValues(TProtocolUtil.getTKeyedValues(e.getKey(),
                                                                e.getValue()));
                    i += 1;
                }
            }

            SyncMessage bsm =
                    new SyncMessage(MessageType.CURSOR_RESPONSE);
            bsm.setCursorResponse(m);
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(),
                                   e, MessageType.CURSOR_REQUEST));
        }
    }

    @Override
    protected void handleRegisterRequest(RegisterRequestMessage request,
                                         Channel channel) {
        try {
            Scope scope = TProtocolUtil.getScope(request.store.getScope());
            if (request.store.isPersist())
                syncManager.registerPersistentStore(request.store.storeName,
                                                    scope);
            else
                syncManager.registerStore(request.store.storeName, scope);
            RegisterResponseMessage m = new RegisterResponseMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            m.setHeader(header);
            SyncMessage bsm =
                    new SyncMessage(MessageType.REGISTER_RESPONSE);
            bsm.setRegisterResponse(m);
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.REGISTER_REQUEST));
        }
    }

    @Override
    protected void handleClusterJoinRequest(ClusterJoinRequestMessage request,
                                            Channel channel) {
        try {
            // We can get this message in two circumstances.  Either this is
            // a totally new node, or this is an existing node that is changing
            // its port or IP address.  We can tell the difference because the
            // node ID and domain ID will already be set for an existing node

            ClusterJoinResponseMessage cjrm = new ClusterJoinResponseMessage();
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(request.getHeader().getTransactionId());
            cjrm.setHeader(header);

            org.sdnplatform.sync.thrift.Node tnode = request.getNode();
            if (!tnode.isSetNodeId()) {
                // allocate a random node ID that's not currently in use
                // Note that there is an obvious possible race here if multiple
                // nodes join quickly or using different seeds.  In this case,
                // if you get unlucky you could have the same random node ID
                // and then bad things would start to happen.  We're essentially
                // assuming that node joins are happening one at a time by a
                // human; the randomness is a lame attempt to mitigate this race
                Random random = new Random();
                short newNodeId;
                ClusterConfig cc = syncManager.getClusterConfig();

                while (true) {
                    newNodeId = (short)random.nextInt(Short.MAX_VALUE);
                    if (cc.getNode(newNodeId) == null) break;
                }

                tnode.setNodeId(newNodeId);
                cjrm.setNewNodeId(newNodeId);
            }
            if (!tnode.isSetDomainId()) {
                // for now put the node into its own domain.  Once it joins
                // the cluster, it can easily change its domain by writing a
                // new domain ID into the system node store
                tnode.setDomainId(tnode.getNodeId());
            }
            IStoreClient<Short, Node> nodeStoreClient =
                    syncManager.getStoreClient(SyncStoreCCProvider.
                                               SYSTEM_NODE_STORE,
                                               Short.class, Node.class);
            while (true) {
                try {
                    Versioned<Node> node =
                            nodeStoreClient.get(tnode.getNodeId());
                    node.setValue(new Node(tnode.getHostname(),
                                           tnode.getPort(),
                                           tnode.getNodeId(),
                                           tnode.getDomainId()));
                    nodeStoreClient.put(tnode.getNodeId(), node);
                    break;
                } catch (ObsoleteVersionException e) { }
            }

            IStorageEngine<ByteArray, byte[]> store =
                    syncManager.getRawStore(SyncStoreCCProvider.
                                            SYSTEM_NODE_STORE);
            IClosableIterator<Entry<ByteArray,
                List<Versioned<byte[]>>>> entries = store.entries();
            try {
                while (entries.hasNext()) {
                    Entry<ByteArray, List<Versioned<byte[]>>> entry =
                            entries.next();
                    KeyedValues kv =
                            TProtocolUtil.getTKeyedValues(entry.getKey(),
                                                          entry.getValue());
                    cjrm.addToNodeStore(kv);
                }
            } finally {
                entries.close();
            }
            SyncMessage bsm =
                    new SyncMessage(MessageType.CLUSTER_JOIN_RESPONSE);
            bsm.setClusterJoinResponse(cjrm);
            channel.writeAndFlush(bsm);
        } catch (Exception e) {
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), e,
                                   MessageType.CLUSTER_JOIN_REQUEST));
        }
    }

    @Override
    protected void handleError(ErrorMessage error, Channel channel) {
        rpcService.messageAcked(error.getType(), getRemoteNodeId());
        updateCounter(SyncManager.counterErrorRemote, 1);
        super.handleError(error, channel);
    }

    // *************************
    // AbstractRPCChannelHandler
    // *************************

    @Override
    protected Short getLocalNodeId() {
        return syncManager.getLocalNodeId();
    }

    @Override
    protected Short getRemoteNodeId() {
        if (remoteNode != null)
            return remoteNode.getNodeId();
        return null;
    }

    @Override
    protected String getLocalNodeIdString() {
        return ""+getLocalNodeId();
    }

    @Override
    protected String getRemoteNodeIdString() {
        return ""+getRemoteNodeId();
    }

    @Override
    protected int getTransactionId() {
        return rpcService.getTransactionId();
    }

    @Override
    protected AuthScheme getAuthScheme() {
        return syncManager.getClusterConfig().getAuthScheme();
    }

    @Override
    protected byte[] getSharedSecret() throws AuthException {
    	 String path = syncManager.getClusterConfig().getKeyStorePath();
         String pass = syncManager.getClusterConfig().getKeyStorePassword();
         try {
             return CryptoUtil.getSharedSecret(path, pass);
         } catch (Exception e) {
             throw new AuthException("Could not read challenge/response " +
                     "shared secret from key store " + path, e);
         }
    }

    @Override
    protected SyncMessage getError(int transactionId, Exception error,
                                   MessageType type) {
        updateCounter(SyncManager.counterErrorProcessing, 1);
        return super.getError(transactionId, error, type);
    }

    // *****************
    // Utility functions
    // *****************

    protected void updateCounter(IDebugCounter counter, int incr) {
        counter.add(incr);
    }

    protected void startAntientropy() {
        // Run antientropy in a background task so we don't use up an I/O
        // thread.  Note that this task will result in lots of traffic
        // that will use I/O threads but each of those will be in manageable
        // chunks
        Runnable arTask = new Runnable() {
            @Override
            public void run() {
                syncManager.antientropy(remoteNode);
            }
        };
        syncManager.getThreadPool().getScheduledExecutor().execute(arTask);
    }


    protected static class TVersionIterable
        implements Iterable<VectorClock> {
        final Iterable<org.sdnplatform.sync.thrift.VectorClock> tcvi;

        public TVersionIterable(Iterable<org.sdnplatform.sync.thrift.VectorClock> tcvi) {
            this.tcvi = tcvi;
        }

        @Override
        public Iterator<VectorClock> iterator() {
            final Iterator<org.sdnplatform.sync.thrift.VectorClock> tcs =
                    tcvi.iterator();
            return new Iterator<VectorClock>() {

                @Override
                public boolean hasNext() {
                    return tcs.hasNext();
                }

                @Override
                public VectorClock next() {
                    return TProtocolUtil.getVersion(tcs.next());
                }

                @Override
                public void remove() {
                    tcs.remove();
                }
            };
        }
    }
}