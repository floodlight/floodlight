package org.sdnplatform.sync.internal.config.bootstrap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.AuthException;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.config.SyncStoreCCProvider;
import org.sdnplatform.sync.internal.rpc.AbstractRPCChannelHandler;
import org.sdnplatform.sync.internal.rpc.TVersionedValueIterable;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.util.CryptoUtil;
import org.sdnplatform.sync.thrift.AsyncMessageHeader;
import org.sdnplatform.sync.thrift.ClusterJoinRequestMessage;
import org.sdnplatform.sync.thrift.ClusterJoinResponseMessage;
import org.sdnplatform.sync.thrift.ErrorMessage;
import org.sdnplatform.sync.thrift.HelloMessage;
import org.sdnplatform.sync.thrift.KeyedValues;
import org.sdnplatform.sync.thrift.MessageType;
import org.sdnplatform.sync.thrift.SyncMessage;
import org.sdnplatform.sync.thrift.VersionedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapChannelHandler extends AbstractRPCChannelHandler {
    protected static final Logger logger =
            LoggerFactory.getLogger(BootstrapChannelHandler.class);

    private Bootstrap bootstrap;
    private Short remoteNodeId;
    
    public BootstrapChannelHandler(Bootstrap bootstrap) {
        super();
        this.bootstrap = bootstrap;
    }

    // ****************************
    // IdleStateAwareChannelHandler
    // ****************************

    @Override
    public void channelOpen(ChannelHandlerContext ctx, 
                            ChannelStateEvent e) throws Exception {
        bootstrap.cg.add(ctx.getChannel());
    }

    // ******************************************
    // AbstractRPCChannelHandler message handlers
    // ******************************************

    @Override
    protected void handleHello(HelloMessage hello, Channel channel) {
        remoteNodeId = hello.getNodeId();
        
        org.sdnplatform.sync.thrift.Node n = 
                new org.sdnplatform.sync.thrift.Node();
        n.setHostname(bootstrap.localNode.getHostname());
        n.setPort(bootstrap.localNode.getPort());
        if (bootstrap.localNode.getNodeId() >= 0)
            n.setNodeId(bootstrap.localNode.getNodeId());
        if (bootstrap.localNode.getDomainId() >= 0)
            n.setDomainId(bootstrap.localNode.getDomainId());
        
        ClusterJoinRequestMessage cjrm = new ClusterJoinRequestMessage();
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(bootstrap.transactionId.getAndIncrement());
        cjrm.setHeader(header);
        cjrm.setNode(n);
        SyncMessage bsm = 
                new SyncMessage(MessageType.CLUSTER_JOIN_REQUEST);
        bsm.setClusterJoinRequest(cjrm);
        channel.write(bsm);
    }

    @Override
    protected void handleClusterJoinResponse(ClusterJoinResponseMessage response,
                                             Channel channel) {
        try {
            IStorageEngine<ByteArray, byte[]> store = 
                    bootstrap.syncManager.
                    getRawStore(SyncStoreCCProvider.SYSTEM_NODE_STORE);

            for (KeyedValues kv : response.getNodeStore()) {
                Iterable<VersionedValue> tvvi = kv.getValues();
                Iterable<Versioned<byte[]>> vs = new TVersionedValueIterable(tvvi);
                store.writeSyncValue(new ByteArray(kv.getKey()), vs);
            }
            
            IStoreClient<String, String> unsyncStoreClient = 
                    bootstrap.syncManager.
                    getStoreClient(SyncStoreCCProvider.SYSTEM_UNSYNC_STORE, 
                                   String.class, String.class);
            if (response.isSetNewNodeId()) {
                while (true) {
                    try {
                        unsyncStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_ID, 
                                              Short.toString(response.
                                                             getNewNodeId()));
                        break;
                    } catch (ObsoleteVersionException e) {}
                }
            }
            bootstrap.succeeded = true;
        } catch (Exception e) {
            logger.error("Error processing cluster join response", e);
            channel.write(getError(response.getHeader().getTransactionId(), e, 
                                   MessageType.CLUSTER_JOIN_RESPONSE));
        }
        channel.disconnect();
    }

    @Override
    protected void handleError(ErrorMessage error, Channel channel) {
        super.handleError(error, channel);
        channel.disconnect();
    }

    // *************************
    // AbstractRPCChannelHandler
    // *************************
    
    @Override
    protected int getTransactionId() {
        return bootstrap.transactionId.getAndIncrement();
    }

    @Override
    protected Short getRemoteNodeId() {
        return remoteNodeId;
    }

    @Override
    protected Short getLocalNodeId() {
        return null;
    }

    @Override
    protected AuthScheme getAuthScheme() {
        return bootstrap.authScheme;
    }

    @Override
    protected byte[] getSharedSecret() throws AuthException {
        try {
            return CryptoUtil.getSharedSecret(bootstrap.keyStorePath, 
                                              bootstrap.keyStorePassword);
        } catch (Exception e) {
            throw new AuthException("Could not read challenge/response " + 
                    "shared secret from key store " + 
                    bootstrap.keyStorePath, e);
        }
    }}
