package org.sdnplatform.sync.internal.config.bootstrap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.config.Node;
import org.sdnplatform.sync.internal.rpc.RPCService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;

/**
 * Makes an attempt to bootstrap the cluster based on seeds stored in the
 * local system store
 * @author readams
 */
public class Bootstrap {
    protected static final Logger logger =
            LoggerFactory.getLogger(Bootstrap.class);
    
    /**
     * Channel group that will hold all our channels
     */
    protected ChannelGroup cg;

    /**
     * Transaction ID used in message headers in the RPC protocol
     */
    protected AtomicInteger transactionId = new AtomicInteger();
    
    /**
     * The {@link SyncManager} that we'll be bootstrapping
     */
    protected SyncManager syncManager;
    protected final AuthScheme authScheme;
    protected final String keyStorePath;
    protected final String keyStorePassword;
    
    ExecutorService bossExecutor = null;
    ExecutorService workerExecutor = null;
    ClientBootstrap bootstrap = null;
    BootstrapPipelineFactory pipelineFactory;
    
    protected Node localNode;
    protected volatile boolean succeeded = false;

    public Bootstrap(SyncManager syncManager, AuthScheme authScheme,
                     String keyStorePath, String keyStorePassword) {
        super();
        this.syncManager = syncManager;
        this.authScheme = authScheme;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    public void init() throws SyncException {
        cg = new DefaultChannelGroup("Cluster Bootstrap");

        bossExecutor = Executors.newCachedThreadPool();
        workerExecutor = Executors.newCachedThreadPool();

        bootstrap =
                new ClientBootstrap(new NioClientSocketChannelFactory(bossExecutor,
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
        pipelineFactory = new BootstrapPipelineFactory(this);
        bootstrap.setPipelineFactory(pipelineFactory);
    }
    
    public void shutdown() {
        if (cg != null) {
            cg.close().awaitUninterruptibly();
            cg = null;
        }
        if (bootstrap != null)
            bootstrap.releaseExternalResources();
        bootstrap = null;
        if (pipelineFactory != null)
            pipelineFactory.releaseExternalResources();
        pipelineFactory = null;
        if (workerExecutor != null)
            workerExecutor.shutdown();
        workerExecutor = null;
        if (bossExecutor != null)
            bossExecutor.shutdown();
        bossExecutor = null;
    }
    
    public boolean bootstrap(HostAndPort seed, 
                             Node localNode) throws SyncException {
        this.localNode = localNode;
        succeeded = false;
        SocketAddress sa =
                new InetSocketAddress(seed.getHostText(), seed.getPort());
        ChannelFuture future = bootstrap.connect(sa);
        future.awaitUninterruptibly();
        if (!future.isSuccess()) {
            logger.debug("Could not connect to " + seed, future.getCause());
            return false;
        }
        Channel channel = future.getChannel();
        logger.debug("[{}] Connected to {}", 
                     localNode != null ? localNode.getNodeId() : null,
                     seed);
        
        try {
            channel.getCloseFuture().await();
        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for bootstrap");
            return succeeded;
        }
        return succeeded;
    }
}
