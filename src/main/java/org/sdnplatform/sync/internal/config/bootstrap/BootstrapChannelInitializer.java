package org.sdnplatform.sync.internal.config.bootstrap;

import org.sdnplatform.sync.internal.rpc.SyncMessageDecoder;
import org.sdnplatform.sync.internal.rpc.SyncMessageEncoder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.util.Timer;



public class BootstrapChannelInitializer extends ChannelInitializer<Channel> {
    private final BootstrapClient bootstrap;
    private static final int maxFrameSize = 1024 * 1024 * 10;
    protected Timer timer;

    public BootstrapChannelInitializer(Timer timer, BootstrapClient bootstrap) {
        super();
        this.timer = timer;
        this.bootstrap = bootstrap;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        BootstrapChannelHandler handler =
                new BootstrapChannelHandler(bootstrap);

        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("syncMessageDecoder", new SyncMessageDecoder(maxFrameSize));

        pipeline.addLast("syncMessageEncoder", new SyncMessageEncoder());

        pipeline.addLast("timeout", new BootstrapTimeoutHandler(timer, 10));

        pipeline.addLast("handler", handler);
    }
}