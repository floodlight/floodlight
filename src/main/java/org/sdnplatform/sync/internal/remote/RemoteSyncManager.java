package org.sdnplatform.sync.internal.remote;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.sdnplatform.sync.error.RemoteStoreException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.SyncRuntimeException;
import org.sdnplatform.sync.error.UnknownStoreException;
import org.sdnplatform.sync.internal.AbstractSyncManager;
import org.sdnplatform.sync.internal.config.AuthScheme;
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

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Implementation of a sync service that passes its functionality off to a
 * remote sync manager over a TCP connection
 * @author readams
 */
@LogMessageCategory("State Synchronization")
public class RemoteSyncManager extends AbstractSyncManager {
    protected static final Logger logger =
            LoggerFactory.getLogger(RemoteSyncManager.class.getName());
    
    /**
     * Channel group that will hold all our channels
     */
    final ChannelGroup cg = new DefaultChannelGroup("Internal RPC");
    RemoteSyncPipelineFactory pipelineFactory;
    ExecutorService bossExecutor;
    ExecutorService workerExecutor;

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
    protected ClientBootstrap clientBootstrap;
    
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
            if (clientBootstrap != null) {
                clientBootstrap.releaseExternalResources();
            }
            clientBootstrap = null;
            if (pipelineFactory != null)
                pipelineFactory.releaseExternalResources();
            pipelineFactory = null;
            if (workerExecutor != null)
                workerExecutor.shutdown();
            workerExecutor = null;
            if (bossExecutor != null)
                bossExecutor.shutdown();
            bossExecutor = null;
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
        bossExecutor = Executors.newCachedThreadPool();
        workerExecutor = Executors.newCachedThreadPool();
        
        final ClientBootstrap bootstrap =
                new ClientBootstrap(
                     new NioClientSocketChannelFactory(bossExecutor,
                                                       workerExecutor));
        bootstrap.setOption("child.reuseAddr", true);
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.sendBufferSize", 
                            RPCService.SEND_BUFFER_SIZE);
        bootstrap.setOption("child.receiveBufferSize", 
                            RPCService.SEND_BUFFER_SIZE);
        bootstrap.setOption("child.connectTimeoutMillis", 
                            RPCService.CONNECT_TIMEOUT);
        pipelineFactory = new RemoteSyncPipelineFactory(this);
        bootstrap.setPipelineFactory(pipelineFactory);
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
        channel.write(request); 
        return future;
    }

    @LogMessageDoc(level="WARN",
                   message="Unexpected sync message reply type={type} id={id}",
                   explanation="An error occurred in the sync protocol",
                   recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
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
        if (channel == null || !channel.isConnected()) {
            SocketAddress sa =
                    new InetSocketAddress(hostname, port);
            ChannelFuture future = clientBootstrap.connect(sa);
            future.awaitUninterruptibly();
            if (!future.isSuccess()) {
                logger.error("Could not connect to " + hostname + 
                             ":" + port, future.getCause());
                return false;
            }
            channel = future.getChannel();
        }
        while (!ready && channel != null && channel.isConnected()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) { }
        }
        if (!ready || channel == null || !channel.isConnected()) {
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
}
