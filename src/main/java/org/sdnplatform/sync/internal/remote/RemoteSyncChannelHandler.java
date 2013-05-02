package org.sdnplatform.sync.internal.remote;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.AuthException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.SyncException.ErrorType;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.rpc.AbstractRPCChannelHandler;
import org.sdnplatform.sync.internal.rpc.TProtocolUtil;
import org.sdnplatform.sync.internal.util.CryptoUtil;
import org.sdnplatform.sync.thrift.CursorResponseMessage;
import org.sdnplatform.sync.thrift.DeleteResponseMessage;
import org.sdnplatform.sync.thrift.ErrorMessage;
import org.sdnplatform.sync.thrift.GetResponseMessage;
import org.sdnplatform.sync.thrift.HelloMessage;
import org.sdnplatform.sync.thrift.PutResponseMessage;
import org.sdnplatform.sync.thrift.RegisterResponseMessage;


/**
 * Implement the client side of the RPC service for the 
 * {@link RemoteSyncManager}
 * @see RemoteSyncManager
 * @author readams
 */
public class RemoteSyncChannelHandler extends AbstractRPCChannelHandler {

    RemoteSyncManager syncManager;

    public RemoteSyncChannelHandler(RemoteSyncManager syncManager) {
        super();
        this.syncManager = syncManager;
    }

    // ****************************
    // IdleStateAwareChannelHandler
    // ****************************
    
    @Override
    public void channelOpen(ChannelHandlerContext ctx, 
                            ChannelStateEvent e) throws Exception {
        syncManager.cg.add(ctx.getChannel());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
                                    ChannelStateEvent e) throws Exception {
        this.syncManager.channel = null;
        syncManager.ready = false;
        syncManager.channelDisconnected(null);
    }

    // ******************************************
    // AbstractRPCChannelHandler message handlers
    // ******************************************

    @Override
    protected void handleHello(HelloMessage hello, Channel channel) {
        syncManager.remoteNodeId = hello.getNodeId();
        syncManager.ready = true;
        synchronized (syncManager.readyNotify) {
            syncManager.notifyAll();
        }
    }

    @Override
    protected void handleGetResponse(GetResponseMessage response,
                                     Channel channel) {
        List<Versioned<byte[]>> values = 
                TProtocolUtil.getVersionedList(response.getValues());
        SyncReply reply = new SyncReply(values, null, true, null, 0);
        syncManager.dispatchReply(response.getHeader().getTransactionId(), 
                                  reply);
    }

    @Override
    protected void handlePutResponse(PutResponseMessage response,
                                     Channel channel) {
        SyncReply reply = new SyncReply(null, null, true, null, 0);
        syncManager.dispatchReply(response.getHeader().getTransactionId(), 
                                  reply);

    }

    @Override
    protected void handleDeleteResponse(DeleteResponseMessage response,
                                        Channel channel) {
        SyncReply reply = new SyncReply(null, null, 
                                        response.isDeleted(), null, 0);
        syncManager.dispatchReply(response.getHeader().getTransactionId(), 
                                  reply);
    }

    @Override
    protected void handleCursorResponse(CursorResponseMessage response,
                                        Channel channel) {
        SyncReply reply = new SyncReply(null, response.getValues(), true, 
                                        null, response.getCursorId());
        syncManager.dispatchReply(response.getHeader().getTransactionId(), 
                                  reply);
    }

    @Override
    protected void handleRegisterResponse(RegisterResponseMessage response,
                                          Channel channel) {
        SyncReply reply = new SyncReply(null, null, 
                                        true, null, 0);
        syncManager.dispatchReply(response.getHeader().getTransactionId(), 
                                  reply);
    }

    @Override
    protected void handleError(ErrorMessage error, Channel channel) {            
        ErrorType errType = ErrorType.GENERIC;
        for (ErrorType e : ErrorType.values()) {
            if (e.getValue() == error.getError().getErrorCode()) {
                errType = e;
                break;
            }
        }
        SyncException ex = 
                SyncException.newInstance(errType, 
                                          error.getError().getMessage(), 
                                          null);
        if (ChannelState.CONNECTED.equals(channelState) ||
            ChannelState.OPEN.equals(channelState) ||
            ErrorType.AUTH.equals(errType)) {
            syncManager.channelDisconnected(ex);
            channel.close();
        } else {
            SyncReply reply = new SyncReply(null, null, false, ex, 0);
            syncManager.dispatchReply(error.getHeader().getTransactionId(), 
                                      reply);
        }
    }

    // *************************
    // AbstractRPCChannelHandler
    // *************************

    @Override
    protected Short getRemoteNodeId() {
        return syncManager.remoteNodeId;
    }

    @Override
    protected Short getLocalNodeId() {
        return null;
    }

    @Override
    protected String getLocalNodeIdString() {
        return "client";
    }

    @Override
    protected int getTransactionId() {
        return syncManager.getTransactionId();
    }

    @Override
    protected AuthScheme getAuthScheme() {
        return syncManager.authScheme;
    }

    @Override
    protected byte[] getSharedSecret() throws AuthException {
        try {
            return CryptoUtil.getSharedSecret(syncManager.keyStorePath, 
                                              syncManager.keyStorePassword);
        } catch (Exception e) {
            throw new AuthException("Could not read challenge/response " + 
                    "shared secret from key store " + 
                    syncManager.keyStorePath, e);
        }
    }
}
