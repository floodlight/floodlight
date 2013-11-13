package org.sdnplatform.sync.internal.config.bootstrap;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.sdnplatform.sync.internal.rpc.ThriftFrameDecoder;
import org.sdnplatform.sync.internal.rpc.ThriftFrameEncoder;

public class BootstrapPipelineFactory 
    implements ChannelPipelineFactory, ExternalResourceReleasable {
    private Bootstrap bootstrap;
    private static final int maxFrameSize = 1024 * 1024 * 10;
    protected Timer timer;
    
    public BootstrapPipelineFactory(Bootstrap bootstrap) {
        super();
        this.bootstrap = bootstrap;
        this.timer = new HashedWheelTimer();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        BootstrapChannelHandler handler = 
                new BootstrapChannelHandler(bootstrap);
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("frameDecoder",
                         new ThriftFrameDecoder(maxFrameSize));
        pipeline.addLast("frameEncoder",
                         new ThriftFrameEncoder());
        pipeline.addLast("timeout",
                         new BootstrapTimeoutHandler(timer, 10));

        pipeline.addLast("handler", handler);

        return pipeline;
    }

    @Override
    public void releaseExternalResources() {
        timer.stop();
    }    
}
