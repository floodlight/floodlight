package org.sdnplatform.sync.internal.rpc;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.sdnplatform.sync.internal.SyncManager;


/**
 * Pipeline factory for the sync service.
 * @see SyncManager
 * @author readams
 */
public class RPCPipelineFactory 
    implements ChannelPipelineFactory, ExternalResourceReleasable {

    protected SyncManager syncManager;
    protected RPCService rpcService;
    protected Timer timer;

    private static final int maxFrameSize = 512 * 1024;
    
    public RPCPipelineFactory(SyncManager syncManager,
                              RPCService rpcService) {
        super();
        this.syncManager = syncManager;
        this.rpcService = rpcService;

        this.timer = new HashedWheelTimer();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        RPCChannelHandler channelHandler = 
                new RPCChannelHandler(syncManager, rpcService);

        IdleStateHandler idleHandler = 
                new IdleStateHandler(timer, 5, 10, 0);
        ReadTimeoutHandler readTimeoutHandler = 
                new ReadTimeoutHandler(timer, 30);
        
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("idle", idleHandler);
        pipeline.addLast("timeout", readTimeoutHandler);
        pipeline.addLast("handshaketimeout",
                         new HandshakeTimeoutHandler(channelHandler, timer, 10));

        pipeline.addLast("frameDecoder",
                         new ThriftFrameDecoder(maxFrameSize));
        pipeline.addLast("frameEncoder",
                         new ThriftFrameEncoder());

        pipeline.addLast("handler", channelHandler);
        return pipeline;
    }

    @Override
    public void releaseExternalResources() {
        timer.stop();        
    }
}
