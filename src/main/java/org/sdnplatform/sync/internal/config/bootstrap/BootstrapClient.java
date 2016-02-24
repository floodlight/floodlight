package org.sdnplatform.sync.internal.config.bootstrap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.GlobalEventExecutor;

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
public class BootstrapClient {
    protected static final Logger logger =
            LoggerFactory.getLogger(BootstrapClient.class);
    
    /**
     * Channel group that will hold all our channels
     */
    private ChannelGroup cg;

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
    
    EventLoopGroup workerExecutor = null;
    Bootstrap bootstrap = null;
    BootstrapChannelInitializer pipelineFactory;
    
    protected Node localNode;
    protected volatile boolean succeeded = false;
    
    private Timer timer;

    public BootstrapClient(SyncManager syncManager, AuthScheme authScheme,
                     String keyStorePath, String keyStorePassword) {
        super();
        this.syncManager = syncManager;
        this.authScheme = authScheme;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    public void init() throws SyncException {
        cg = new DefaultChannelGroup("Cluster Bootstrap", GlobalEventExecutor.INSTANCE);

        workerExecutor = new NioEventLoopGroup();
        timer = new HashedWheelTimer();
        
        bootstrap = new Bootstrap()
        .group(workerExecutor)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.SO_REUSEADDR, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_SNDBUF, RPCService.SEND_BUFFER_SIZE)
        .option(ChannelOption.SO_RCVBUF, RPCService.SEND_BUFFER_SIZE)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, RPCService.CONNECT_TIMEOUT);
        
        pipelineFactory = new BootstrapChannelInitializer(timer, this);
        bootstrap.handler(pipelineFactory);
    }
    
    public void shutdown() {
        if (cg != null) {
            cg.close().awaitUninterruptibly();
            cg = null;
        }
        bootstrap = null;
        pipelineFactory = null;
        if (workerExecutor != null) {
            try {
				workerExecutor.shutdownGracefully();
			} catch (TimeoutException e) {
				logger.warn("Error waiting for gracefull shutdown of BootstrapClient {}", e);
			}
            workerExecutor = null;
        }
        if (timer != null) {
        	timer.stop();
        	timer = null;
        }
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
            logger.debug("Could not connect to " + seed, future.cause());
            return false;
        }
        Channel channel = future.channel();
        logger.debug("[{}] Connected to {}", 
                     localNode != null ? localNode.getNodeId() : null,
                     seed);
        
        try {
            channel.closeFuture().await();
        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for bootstrap");
            return succeeded;
        }
        return succeeded;
    }
    
    public ChannelGroup getChannelGroup() {
    	return cg;
    }
}
