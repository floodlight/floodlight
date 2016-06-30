package org.sdnplatform.sync.internal.remote;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.GlobalEventExecutor;

import org.sdnplatform.sync.error.RemoteStoreException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.SyncRuntimeException;
import org.sdnplatform.sync.error.UnknownStoreException;
import org.sdnplatform.sync.internal.AbstractSyncManager;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.rpc.IRPCListener;
import org.sdnplatform.sync.internal.rpc.RPCService;
import org.sdnplatform.sync.internal.rpc.TProtocolUtil;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.store.MappingStoreListener;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.thrift.AsyncMessageHeader;
import org.sdnplatform.sync.thrift.SyncMessage;
import org.sdnplatform.sync.thrift.MessageType;
import org.sdnplatform.sync.thrift.RegisterRequestMessage;
import org.sdnplatform.sync.thrift.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Implementation of a sync service that passes its functionality off to a
 * remote sync manager over a TCP connection
 * @author readams
 */
public class RemoteSyncManager extends AbstractSyncManager {
    protected static final Logger logger =
            LoggerFactory.getLogger(RemoteSyncManager.class.getName());
    
    /**
     * Channel group that will hold all our channels
     */
    final ChannelGroup cg = new DefaultChannelGroup("Internal RPC", GlobalEventExecutor.INSTANCE);
    RemoteSyncChannelInitializer pipelineFactory;
    EventLoopGroup workerExecutor;

    /**
     * Active connection to server
     */
    protected volatile Channel channel;
    private volatile int connectionGeneration = 0;
    protected Object readyNotify = new Object();
    protected volatile boolean ready = false;
    protected volatile boolean shutdown = false;

    /**
     * The remote node ID of the node we're connected to
     */
    protected Short remoteNodeId;
    
    /**
     * Client bootstrap
     */
    protected Bootstrap clientBootstrap;
    
    /**
     * Transaction ID used in message headers in the RPC protocol
     */
    private AtomicInteger transactionId = new AtomicInteger();

    /**
     * The hostname of the server to connect to
     */
    protected String hostname = "localhost";
    
    /**
     * Port to connect to
     */
    protected int port = 6642;
    
    /**
     * Timer for Netty
     */
    private HashedWheelTimer timer;
    
    protected AuthScheme authScheme;
    protected String keyStorePath;
    protected String keyStorePassword;
    
    private ConcurrentHashMap<Integer, RemoteSyncFuture> futureMap = 
            new ConcurrentHashMap<Integer, RemoteSyncFuture>();
    private Object futureNotify = new Object();
    private static int MAX_PENDING_REQUESTS = 1000;
    
    // ************
    // ISyncService
    // ************

    public RemoteSyncManager() {
    }

    @Override
    public void registerStore(String storeName, Scope scope) 
            throws SyncException {
        doRegisterStore(storeName, scope, false);
    }

    @Override
    public void registerPersistentStore(String storeName, Scope scope)
            throws SyncException {
        doRegisterStore(storeName, scope, true);
    }

    // *******************
    // AbstractSyncManager
    // *******************

    @Override
    public void addListener(String storeName, 
                            MappingStoreListener listener)
                                    throws UnknownStoreException {
        ensureConnected();
    }

    @Override
    public IStore<ByteArray, byte[]>
            getStore(String storeName) throws UnknownStoreException {
        ensureConnected();
        return new RemoteStore(storeName, this);
    }

    @Override
    public short getLocalNodeId() {
        ensureConnected();
        return remoteNodeId;
    }
    
    @Override
    public void shutdown() {
        shutdown = true;
        logger.debug("Shutting down Remote Sync Manager");
        try {
            if (!cg.close().await(5, TimeUnit.SECONDS)) {
                logger.debug("Failed to cleanly shut down remote sync");
                return;
            }
            clientBootstrap = null;
            pipelineFactory = null;
            if (workerExecutor != null) {
            	workerExecutor.shutdownGracefully();
            	workerExecutor = null;
            }
            if (timer != null) {
            	timer.stop();
            	timer = null;
            }
        } catch (InterruptedException e) {
            logger.debug("Interrupted while shutting down remote sync");
        }
    }

    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        Map<String, String> config = context.getConfigParams(this);
        if (null != config.get("hostname"))
            hostname = config.get("hostname");
        if (null != config.get("port"))
            port = Integer.parseInt(config.get("port"));
        keyStorePath = config.get("keyStorePath");
        keyStorePassword = config.get("keyStorePassword");
        authScheme = AuthScheme.NO_AUTH;
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
        } catch (Exception e) {}
    }

    @Override
    public void startUp(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        shutdown = false;
        workerExecutor = new NioEventLoopGroup();
        timer = new HashedWheelTimer();
        
        pipelineFactory = new RemoteSyncChannelInitializer(timer, this);
        
        final Bootstrap bootstrap = new Bootstrap()
        .channel(NioSocketChannel.class)
        .group(workerExecutor)
        .option(ChannelOption.SO_REUSEADDR, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_SNDBUF, RPCService.SEND_BUFFER_SIZE)
        .option(ChannelOption.SO_RCVBUF, RPCService.SEND_BUFFER_SIZE)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, RPCService.CONNECT_TIMEOUT)
        .handler(pipelineFactory);
        
        clientBootstrap = bootstrap;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }
    
    // *****************
    // RemoteSyncManager
    // *****************
    
    /**
     * Get a suitable transaction ID for sending a message
     * @return the unique transaction iD
     */
    public int getTransactionId() {
        return transactionId.getAndIncrement();
    }

    /**
     * Send a request to the server and generate a future for the 
     * eventual reply.  Note that this call can block if there is no active
     * connection while a new connection is re-established or if the maximum
     * number of requests is already pending
     * @param xid the transaction ID for the request
     * @param request the actual request to send
     * @return A {@link Future} for the reply message
     * @throws InterruptedException 
     */
    public Future<SyncReply> sendRequest(int xid,
                                            SyncMessage request) 
                                         throws RemoteStoreException {
        ensureConnected();
        RemoteSyncFuture future = new RemoteSyncFuture(xid, 
                                                       connectionGeneration);
        futureMap.put(Integer.valueOf(xid), future);

        if (futureMap.size() > MAX_PENDING_REQUESTS) {
            synchronized (futureNotify) {
                while (futureMap.size() > MAX_PENDING_REQUESTS) {
                    try {
                        futureNotify.wait();
                    } catch (InterruptedException e) {
                        throw new RemoteStoreException("Could not send request",
                                                       e);
                    }
                }
            }
        }
        channel.writeAndFlush(request); 
        return future;
    }

    public void dispatchReply(int xid,
                              SyncReply reply) {
        RemoteSyncFuture future = futureMap.get(Integer.valueOf(xid));
        if (future == null) {
            logger.warn("Unexpected sync message replyid={}", xid);
            return;
        }
        futureMap.remove(Integer.valueOf(xid));
        future.setReply(reply);
        synchronized (futureNotify) {
            futureNotify.notify();
        }
    }

    protected void channelDisconnected(SyncException why) {
        ready = false;
        connectionGeneration += 1;
        if (why == null) why = new RemoteStoreException("Channel disconnected");
        for (RemoteSyncFuture f : futureMap.values()) {
            if (f.getConnectionGeneration() < connectionGeneration)
                dispatchReply(f.getXid(), 
                              new SyncReply(null, null, false, why, 0));
        }
    }

    // ***************
    // Local methods
    // ***************

    protected void ensureConnected() {
        if (!ready) {
            for (int i = 0; i < 2; i++) {
                synchronized (this) {
                    connectionGeneration += 1;
                    if (connect(hostname, port))
                        return;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}
            }
            if (channel == null) 
                throw new SyncRuntimeException(new SyncException("Failed to establish connection"));
        }
    }

    protected boolean connect(String hostname, int port) {
        ready = false;
        if (channel == null || !channel.isActive()) {
            SocketAddress sa =
                    new InetSocketAddress(hostname, port);
            ChannelFuture future = clientBootstrap.connect(sa);
            future.awaitUninterruptibly();
            if (!future.isSuccess()) {
                logger.error("Could not connect to " + hostname + 
                             ":" + port, future.cause());
                return false;
            }
            channel = future.channel();
        }
        while (!ready && channel != null && channel.isActive()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) { }
        }
        if (!ready || channel == null || !channel.isActive()) {
            logger.warn("Timed out connecting to {}:{}", hostname, port);
            return false;
        }
        logger.debug("Connected to {}:{}", hostname, port);
        return true;
    }

    private void doRegisterStore(String storeName, Scope scope, boolean b) 
            throws SyncException{

        ensureConnected();
        RegisterRequestMessage rrm = new RegisterRequestMessage();
        AsyncMessageHeader header = new AsyncMessageHeader();
        header.setTransactionId(getTransactionId());
        rrm.setHeader(header);
        
        Store store = new Store(storeName);
        store.setScope(TProtocolUtil.getTScope(scope));
        store.setPersist(false);
        rrm.setStore(store);
        
        SyncMessage bsm = new SyncMessage(MessageType.REGISTER_REQUEST);
        bsm.setRegisterRequest(rrm);
        Future<SyncReply> future =
                sendRequest(header.getTransactionId(), bsm);
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RemoteStoreException("Timed out on operation", e);
        } catch (Exception e) {
            throw new RemoteStoreException("Error while waiting for reply", e);
        }        
    }

	
	@Override
	public void addRPCListener(IRPCListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeRPCListener(IRPCListener listener) {
		// TODO Auto-generated method stub
		
	}
}
