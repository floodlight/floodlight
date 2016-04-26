package org.sdnplatform.sync.internal.rpc;

import java.io.IOException;
import java.net.ConnectException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;

import org.sdnplatform.sync.error.AuthException;
import org.sdnplatform.sync.error.HandshakeTimeoutException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.util.CryptoUtil;
import org.sdnplatform.sync.thrift.AsyncMessageHeader;
import org.sdnplatform.sync.thrift.AuthChallengeResponse;
import org.sdnplatform.sync.thrift.ClusterJoinRequestMessage;
import org.sdnplatform.sync.thrift.ClusterJoinResponseMessage;
import org.sdnplatform.sync.thrift.SyncError;
import org.sdnplatform.sync.thrift.SyncMessage;
import org.sdnplatform.sync.thrift.CursorRequestMessage;
import org.sdnplatform.sync.thrift.CursorResponseMessage;
import org.sdnplatform.sync.thrift.DeleteRequestMessage;
import org.sdnplatform.sync.thrift.DeleteResponseMessage;
import org.sdnplatform.sync.thrift.EchoReplyMessage;
import org.sdnplatform.sync.thrift.EchoRequestMessage;
import org.sdnplatform.sync.thrift.ErrorMessage;
import org.sdnplatform.sync.thrift.FullSyncRequestMessage;
import org.sdnplatform.sync.thrift.GetRequestMessage;
import org.sdnplatform.sync.thrift.GetResponseMessage;
import org.sdnplatform.sync.thrift.HelloMessage;
import org.sdnplatform.sync.thrift.MessageType;
import org.sdnplatform.sync.thrift.PutRequestMessage;
import org.sdnplatform.sync.thrift.PutResponseMessage;
import org.sdnplatform.sync.thrift.RegisterRequestMessage;
import org.sdnplatform.sync.thrift.RegisterResponseMessage;
import org.sdnplatform.sync.thrift.SyncOfferMessage;
import org.sdnplatform.sync.thrift.SyncRequestMessage;
import org.sdnplatform.sync.thrift.SyncValueMessage;
import org.sdnplatform.sync.thrift.SyncValueResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Abstract base class for implementing the RPC protocol.  The protocol is 
 * defined by a thrift specification; all protocol messages are delivered in
 * a {@link SyncMessage} which will provide specific type information. 
 * @author readams
 */
public abstract class AbstractRPCChannelHandler extends ChannelInboundHandlerAdapter {
    protected static final Logger logger =
            LoggerFactory.getLogger(AbstractRPCChannelHandler.class);
    protected String currentChallenge;
    
    protected enum ChannelState {
        OPEN,
        CONNECTED,
        AUTHENTICATED;
    }
    
    protected ChannelState channelState = ChannelState.OPEN;
    
    public AbstractRPCChannelHandler() {
        super();
    }

    // ****************************
    // IdleStateAwareChannelHandler
    // ****************************

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channelState = ChannelState.CONNECTED;

        HelloMessage m = new HelloMessage();
        if (getLocalNodeId() != null)
            m.setNodeId(getLocalNodeId());
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(getTransactionId());
        m.setHeader(header);
        switch (getAuthScheme()) {
            case NO_AUTH:
                channelState = ChannelState.AUTHENTICATED;
                m.setAuthScheme(org.sdnplatform.sync.thrift.
                                AuthScheme.NO_AUTH);
                break;
            case CHALLENGE_RESPONSE:
                AuthChallengeResponse cr = new AuthChallengeResponse();
                cr.setChallenge(generateChallenge());
                m.setAuthScheme(org.sdnplatform.sync.thrift.
                                AuthScheme.CHALLENGE_RESPONSE);
                m.setAuthChallengeResponse(cr);
                break;
        }
        SyncMessage bsm = new SyncMessage(MessageType.HELLO);
        bsm.setHello(m);
        ctx.channel().writeAndFlush(bsm);
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    	if (evt instanceof IdleStateEvent) {
    		channelIdle(ctx, (IdleStateEvent) evt);
    	}
    	super.userEventTriggered(ctx, evt);
    }

    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) throws Exception {
        // send an echo request
        EchoRequestMessage m = new EchoRequestMessage();
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(getTransactionId());
        m.setHeader(header);
        SyncMessage bsm = new SyncMessage(MessageType.ECHO_REQUEST);
        bsm.setEchoRequest(m);
        ctx.channel().writeAndFlush(bsm);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReadTimeoutException) {
            // read timeout
            logger.error("[{}->{}] Disconnecting RPC node due to read timeout",
                         getLocalNodeIdString(), getRemoteNodeIdString());
            ctx.channel().close();
        } else if (cause instanceof HandshakeTimeoutException) {
            // read timeout
            logger.error("[{}->{}] Disconnecting RPC node due to " +
                    "handshake timeout",
                    getLocalNodeIdString(), getRemoteNodeIdString());
            ctx.channel().close();
        } else if (cause instanceof ConnectException ||
        		cause instanceof IOException) {
            logger.debug("[{}->{}] {}: {}", 
                         new Object[] {getLocalNodeIdString(),
                                       getRemoteNodeIdString(), 
                                       cause.getClass().getName(),
                                       cause.getMessage()});
        } else {
            logger.error("[{}->{}] An error occurred on RPC channel",
                         new Object[]{getLocalNodeIdString(), 
                                      getRemoteNodeIdString(),
                                      cause});
            ctx.channel().close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (message instanceof SyncMessage) {
            handleSyncMessage((SyncMessage)message, ctx.channel());
        } else if (message instanceof List) {
            for (Object i : (List<?>)message) {
                if (i instanceof SyncMessage) {
                    try {
                        handleSyncMessage((SyncMessage)i,
                                             ctx.channel());
                    } catch (Exception ex) {
                        ctx.fireExceptionCaught(ex);
                    }
                }
            }
        } else {
            handleUnknownMessage(ctx, message);
        }
    }

    // ****************
    // Message Handlers
    // ****************

    /**
     * A handler for messages on the channel that are not of type 
     * {@link SyncMessage}
     * @param ctx the context
     * @param message the message object
     */
    protected void handleUnknownMessage(ChannelHandlerContext ctx, 
                                        Object message) {
        logger.warn("[{}->{}] Unhandled message: {}", 
                    new Object[]{getLocalNodeIdString(), 
                                 getRemoteNodeIdString(),
                                 message.getClass().getCanonicalName()});
    }
    
    /**
     * Handle a generic {@link SyncMessage} and dispatch to an appropriate
     * handler
     * @param bsm the message
     * @param channel the channel on which the message arrived
     */
    protected void handleSyncMessage(SyncMessage bsm, Channel channel) {
        switch (channelState) {
            case OPEN:
            case CONNECTED:
                switch (bsm.getType()) {
                    case HELLO:
                        handshake(bsm.getHello(), channel);
                        break;
                    case ECHO_REQUEST:
                        handleEchoRequest(bsm.getEchoRequest(), channel);
                        break;
                    case ERROR:
                        handleError(bsm.getError(), channel);
                        break;
                    default:
                        // ignore
                }
                break;
            case AUTHENTICATED:
                handleSMAuthenticated(bsm, channel);
                break;
        }
    }
    
    /**
     * Handle a generic {@link SyncMessage} and dispatch to an appropriate
     * handler
     * @param bsm the message
     * @param channel the channel on which the message arrived
     */
    protected void handleSMAuthenticated(SyncMessage bsm, Channel channel) {
        switch (bsm.getType()) {
            case HELLO:
                handleHello(bsm.getHello(), channel);
                break;
            case ECHO_REQUEST:
                handleEchoRequest(bsm.getEchoRequest(), channel);
                break;
            case GET_REQUEST:
                handleGetRequest(bsm.getGetRequest(), channel);
                break;
            case GET_RESPONSE:
                handleGetResponse(bsm.getGetResponse(), channel);
                break;
            case PUT_REQUEST:
                handlePutRequest(bsm.getPutRequest(), channel);
                break;
            case PUT_RESPONSE:
                handlePutResponse(bsm.getPutResponse(), channel);
                break;
            case DELETE_REQUEST:
                handleDeleteRequest(bsm.getDeleteRequest(), channel);
                break;
            case DELETE_RESPONSE:
                handleDeleteResponse(bsm.getDeleteResponse(), channel);
                break;
            case SYNC_VALUE_RESPONSE:
                handleSyncValueResponse(bsm.getSyncValueResponse(), channel);
                break;
            case SYNC_VALUE:
                handleSyncValue(bsm.getSyncValue(), channel);
                break;
            case SYNC_OFFER:
                handleSyncOffer(bsm.getSyncOffer(), channel);
                break;
            case FULL_SYNC_REQUEST:
                handleFullSyncRequest(bsm.getFullSyncRequest(), channel);
                break;
            case SYNC_REQUEST:
                handleSyncRequest(bsm.getSyncRequest(), channel);
                break;
            case CURSOR_REQUEST:
                handleCursorRequest(bsm.getCursorRequest(), channel);
                break;
            case CURSOR_RESPONSE:
                handleCursorResponse(bsm.getCursorResponse(), channel);
                break;
            case REGISTER_REQUEST:
                handleRegisterRequest(bsm.getRegisterRequest(), channel);
                break;
            case REGISTER_RESPONSE:
                handleRegisterResponse(bsm.getRegisterResponse(), channel);
                break;
            case CLUSTER_JOIN_REQUEST:
                handleClusterJoinRequest(bsm.getClusterJoinRequest(), channel);
                break;
            case CLUSTER_JOIN_RESPONSE:
                handleClusterJoinResponse(bsm.getClusterJoinResponse(), channel);
                break;
            case ERROR:
                handleError(bsm.getError(), channel);
                break;
            case ECHO_REPLY:
                // do nothing; just the read will have reset our read timeout
                // handler
                break;
            default:
                logger.warn("[{}->{}] Unhandled message: {}", 
                             new Object[]{getLocalNodeIdString(), 
                                          getRemoteNodeIdString(), 
                                          bsm.getType()});
                break;
        }
        
    }

    protected void handshake(HelloMessage request, Channel channel) {
        try {
            switch (getAuthScheme()) {
                case CHALLENGE_RESPONSE:
                    handshakeChallengeResponse(request, channel);
                    break;
                case NO_AUTH:
                    // shouldn't get here
                    break;
            }
        } catch (AuthException e) {
            logger.warn("[{}->{}] Failed to authenticate connection: {}",
                        new Object[]{getLocalNodeIdString(), 
                                     getRemoteNodeIdString(), 
                                     e.getMessage()});
            channel.writeAndFlush(getError(request.getHeader().getTransactionId(), 
                                   e, MessageType.HELLO));
            channel.close();
        }
    }
    
    protected void handshakeChallengeResponse(HelloMessage request,
                                              Channel channel) 
                                                      throws AuthException {
        AuthChallengeResponse cr = request.getAuthChallengeResponse();
        if (cr == null) {
            throw new AuthException("No authentication data in " + 
                    "handshake message");
        }
        
        if (cr.isSetResponse()) {
            authenticateResponse(currentChallenge, cr.getResponse());
            currentChallenge = null;
            channelState = ChannelState.AUTHENTICATED;
            handleHello(request, channel);
        } else if (cr.isSetChallenge()) {
            HelloMessage m = new HelloMessage();
            if (getLocalNodeId() != null)
                m.setNodeId(getLocalNodeId());
            AsyncMessageHeader header = new AsyncMessageHeader();
            header.setTransactionId(getTransactionId());
            m.setHeader(header);
            SyncMessage bsm = new SyncMessage(MessageType.HELLO);
            bsm.setHello(m); 
            AuthChallengeResponse reply = new AuthChallengeResponse();
            reply.setResponse(generateResponse(cr.getChallenge()));
            m.setAuthChallengeResponse(reply);
            channel.writeAndFlush(bsm);
        } else {
            throw new AuthException("No authentication data in " + 
                    "handshake message");
        }
    }
                                                      

    protected void error(ErrorMessage error, Channel channel) {
        if (MessageType.HELLO.equals(error.getType())) {
            
        }
    }

    protected void handleHello(HelloMessage request, Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.HELLO, channel);
    }

    protected void handleEchoRequest(EchoRequestMessage request,
                                     Channel channel) {
        EchoReplyMessage m = new EchoReplyMessage();
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(request.getHeader().getTransactionId());
        m.setHeader(header);
        SyncMessage bsm = new SyncMessage(MessageType.ECHO_REPLY);
        bsm.setEchoReply(m);
        channel.writeAndFlush(bsm);
    }

    protected void handleGetRequest(GetRequestMessage request,
                                  Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.GET_REQUEST, channel);
    }

    protected void handleGetResponse(GetResponseMessage response,
                                     Channel channel) {
        unexpectedMessage(response.getHeader().getTransactionId(),
                          MessageType.GET_RESPONSE, channel);
    }

    protected void handlePutRequest(PutRequestMessage request,
                                    Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.PUT_REQUEST, channel);
    }
    
    protected void handlePutResponse(PutResponseMessage response,
                                     Channel channel) {
        unexpectedMessage(response.getHeader().getTransactionId(),
                          MessageType.PUT_RESPONSE, channel);
    }

    protected void handleDeleteRequest(DeleteRequestMessage request,
                                     Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.DELETE_REQUEST, channel);
    }

    protected void handleDeleteResponse(DeleteResponseMessage response,
                                        Channel channel) {
        unexpectedMessage(response.getHeader().getTransactionId(),
                          MessageType.PUT_RESPONSE, channel);
    }

    protected void handleSyncValue(SyncValueMessage message, 
                                   Channel channel) {
        unexpectedMessage(message.getHeader().getTransactionId(),
                          MessageType.SYNC_VALUE, channel);
    }

    protected void handleSyncValueResponse(SyncValueResponseMessage message, 
                                           Channel channel) {
        unexpectedMessage(message.getHeader().getTransactionId(),
                          MessageType.SYNC_VALUE_RESPONSE, channel);
    }

    protected void handleSyncOffer(SyncOfferMessage message, 
                                   Channel channel) {
        unexpectedMessage(message.getHeader().getTransactionId(),
                          MessageType.SYNC_OFFER, channel);
    }

    protected void handleSyncRequest(SyncRequestMessage request,
                                   Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.SYNC_REQUEST, channel);
    }

    protected void handleFullSyncRequest(FullSyncRequestMessage request,
                                         Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.FULL_SYNC_REQUEST, channel);        
    }

    protected void handleCursorRequest(CursorRequestMessage request,
                                       Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.CURSOR_REQUEST, channel);
    }

    protected void handleCursorResponse(CursorResponseMessage response,
                                        Channel channel) {
        unexpectedMessage(response.getHeader().getTransactionId(),
                          MessageType.CURSOR_RESPONSE, channel);
    }

    protected void handleRegisterRequest(RegisterRequestMessage request,
                                         Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.REGISTER_REQUEST, channel);
    }

    protected void handleRegisterResponse(RegisterResponseMessage response,
                                          Channel channel) {
        unexpectedMessage(response.getHeader().getTransactionId(),
                          MessageType.REGISTER_RESPONSE, channel);
    }

    protected void handleClusterJoinRequest(ClusterJoinRequestMessage request,
                                            Channel channel) {
        unexpectedMessage(request.getHeader().getTransactionId(),
                          MessageType.CLUSTER_JOIN_REQUEST, channel);
    }

    protected void handleClusterJoinResponse(ClusterJoinResponseMessage response,
                                             Channel channel) {
        unexpectedMessage(response.getHeader().getTransactionId(),
                          MessageType.CLUSTER_JOIN_RESPONSE, channel);
    }

    protected void handleError(ErrorMessage error, Channel channel) {
        logger.error("[{}->{}] Error for message {} ({}): {} ({})", 
                     new Object[]{getLocalNodeIdString(), 
                                  getRemoteNodeIdString(),
                                  error.getHeader().getTransactionId(),
                                  error.getType(),
                                  error.getError().getMessage(),
                                  error.getError().getErrorCode()});
    }

    // *****************
    // Utility functions
    // *****************

    /**
     * Generate an error message from the provided transaction ID and
     * exception
     * @param transactionId the transaction Id
     * @param error the exception
     * @param type the type of the message that generated the error
     * @return the {@link SyncError} message
     */
    protected SyncMessage getError(int transactionId, Exception error, 
                                   MessageType type) {
        int ec = SyncException.ErrorType.GENERIC.getValue();
        if (error instanceof SyncException) {
            ec = ((SyncException)error).getErrorType().getValue();
        } else {
            logger.error("Unexpected error processing message " + transactionId
                         + "(" + type + ")", error);
        }
        SyncError m = new SyncError();
        m.setErrorCode(ec);
        m.setMessage(error.getMessage());
        ErrorMessage em = new ErrorMessage();
        em.setError(m);
        em.setType(type);
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(transactionId);
        em.setHeader(header);
        SyncMessage bsm = new SyncMessage(MessageType.ERROR);
        bsm.setError(em);
        return bsm;
    }
    
    /**
     * Send an error to the channel indicating that we got an unexpected
     * message for this type of RPC client
     * @param transactionId the transaction ID for the message that generated
     * the error
     * @param type The type of the message that generated the error
     * @param channel the channel to write the error
     */
    protected void unexpectedMessage(int transactionId,
                                     MessageType type,
                                     Channel channel) {
        String message = "Received unexpected message: " + type;
        logger.warn("[{}->{}] {}",
                    new Object[]{getLocalNodeIdString(), 
                                 getRemoteNodeIdString(),
                                 message});
        channel.writeAndFlush(getError(transactionId, 
                               new SyncException(message), type));
    }
    
    /**
     * Get a transaction ID suitable for sending an async message
     * @return the unique transaction ID
     */
    protected abstract int getTransactionId();

    /**
     * Get the node ID for the remote node if its connected
     * @return the node ID
     */
    protected abstract Short getRemoteNodeId();

    /**
     * Get the node ID for the remote node if its connected as a string
     * for use output
     * @return the node ID
     */
    protected String getRemoteNodeIdString() {
        return ""+getRemoteNodeId();
    }

    /**
     * Get the node ID for the local node if appropriate
     * @return the node ID.  Null if this is a client
     */
    protected abstract Short getLocalNodeId();

    /**
     * Get the node ID for the local node as a string for use output
     * @return the node ID
     */
    protected String getLocalNodeIdString() {
        return ""+getLocalNodeId();
    }

    /**
     * Get the type of authentication to use for this connection
     */
    protected abstract AuthScheme getAuthScheme();

    /**
     * Get a shared secret to be used for authentication handshake.  
     * Throwing an exception will cause authentication to fail
     * @return the shared secret
     */
    protected abstract byte[] getSharedSecret() throws AuthException;

    // *************
    // Local methods
    // *************

    private String generateChallenge() {
        byte[] challengeBytes = CryptoUtil.secureRandom(16);
        currentChallenge = DatatypeConverter.printBase64Binary(challengeBytes);
        return currentChallenge;
    }

    private void authenticateResponse(String challenge, 
                                      String response) throws AuthException {
        String expected = generateResponse(challenge);
        if (expected == null) return;

        byte[] expectedBytes = DatatypeConverter.parseBase64Binary(expected);
        byte[] reponseBytes = DatatypeConverter.parseBase64Binary(response);
        
        if (!Arrays.equals(expectedBytes, reponseBytes)) {
            throw new AuthException("Challenge response does not match " +
                                    "expected response");
        } 
    }
    
    private String generateResponse(String challenge) throws AuthException {
    	byte[] secretBytes = getSharedSecret();
        if (secretBytes == null) return null;

        SecretKeySpec signingKey = 
                new SecretKeySpec(secretBytes, "HmacSHA1");
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new AuthException("Could not initialize HmacSHA1 algorithm", e);
        }
        
        try {
            mac.init(signingKey);
            byte[] output = 
                    mac.doFinal(DatatypeConverter.parseBase64Binary(challenge));
            return DatatypeConverter.printBase64Binary(output);            
        } catch (InvalidKeyException e) {
            throw new AuthException("Invalid shared secret; could not " +
                    "authenticate response", e);
        }
    }
}
