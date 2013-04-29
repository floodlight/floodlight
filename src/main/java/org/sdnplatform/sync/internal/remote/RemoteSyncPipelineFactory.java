package org.sdnplatform.sync.internal.remote;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.sdnplatform.sync.internal.rpc.ThriftFrameDecoder;
import org.sdnplatform.sync.internal.rpc.ThriftFrameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline factory for the remote sync service
 * @author readams
 */
public class RemoteSyncPipelineFactory 
    implements ChannelPipelineFactory, ExternalResourceReleasable {
    protected static final Logger logger =
            LoggerFactory.getLogger(RemoteSyncPipelineFactory.class.getName());
    
    protected RemoteSyncManager syncManager;
    protected Timer timer;

    private static final int maxFrameSize = 1024 * 1024 * 10;
    
    public RemoteSyncPipelineFactory(RemoteSyncManager syncManager) {
        super();
        this.syncManager = syncManager;
        this.timer = new HashedWheelTimer();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        RemoteSyncChannelHandler channelHandler = 
                new RemoteSyncChannelHandler(syncManager);
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("frameDecoder",
                         new ThriftFrameDecoder(maxFrameSize));
        pipeline.addLast("frameEncoder",
                         new ThriftFrameEncoder());
        pipeline.addLast("timeout",
                         new RSHandshakeTimeoutHandler(channelHandler,
                                                       timer, 3));

        pipeline.addLast("handler", channelHandler);
        return pipeline;
    }

    @Override
    public void releaseExternalResources() {
        timer.stop();
    }
}
