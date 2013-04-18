package org.sdnplatform.sync.internal.remote;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.sdnplatform.sync.internal.rpc.ThriftFrameDecoder;
import org.sdnplatform.sync.internal.rpc.ThriftFrameEncoder;

/**
 * Pipeline factory for the remote sync service
 * @author readams
 */
public class RemoteSyncPipelineFactory implements ChannelPipelineFactory {

    protected RemoteSyncManager syncManager;

    private static final int maxFrameSize = 1024 * 1024 * 10;
    
    public RemoteSyncPipelineFactory(RemoteSyncManager syncManager) {
        super();
        this.syncManager = syncManager;
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

        pipeline.addLast("handler", channelHandler);
        return pipeline;
    }
}
