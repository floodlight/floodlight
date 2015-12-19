package org.sdnplatform.sync.internal.remote;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.util.Timer;

import org.sdnplatform.sync.internal.rpc.SyncMessageDecoder;
import org.sdnplatform.sync.internal.rpc.SyncMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline factory for the remote sync service
 * @author readams
 */
public class RemoteSyncChannelInitializer extends ChannelInitializer<Channel> {
    protected static final Logger logger =
            LoggerFactory.getLogger(RemoteSyncChannelInitializer.class.getName());

    private final RemoteSyncManager syncManager;
    private final Timer timer;

    private static final int maxFrameSize = 1024 * 1024 * 10;

    public RemoteSyncChannelInitializer(Timer timer, RemoteSyncManager syncManager) {
        super();
        this.syncManager = syncManager;
        this.timer = timer;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        RemoteSyncChannelHandler channelHandler =
                new RemoteSyncChannelHandler(syncManager);

        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("syncMessageDecoder", new SyncMessageDecoder(maxFrameSize));

        pipeline.addLast("syncMessageEncoder", new SyncMessageEncoder());

        pipeline.addLast("timeout", new RSHandshakeTimeoutHandler(channelHandler, timer, 3));

        pipeline.addLast("handler", channelHandler);
    }
}