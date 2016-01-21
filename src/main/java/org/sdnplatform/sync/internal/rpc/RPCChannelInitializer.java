package org.sdnplatform.sync.internal.rpc;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.Timer;

import org.sdnplatform.sync.internal.SyncManager;


/**
 * Pipeline factory for the sync service.
 * @see SyncManager
 * @author readams
 */
public class RPCChannelInitializer extends ChannelInitializer<Channel> {

    protected SyncManager syncManager;
    protected RPCService rpcService;
    protected Timer timer;

    private static final int maxFrameSize = 512 * 1024;
    
    public RPCChannelInitializer(SyncManager syncManager,
                              RPCService rpcService,
                              Timer timer) {
        super();
        this.syncManager = syncManager;
        this.rpcService = rpcService;
        this.timer = timer;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        RPCChannelHandler channelHandler = 
                new RPCChannelHandler(syncManager, rpcService);

        IdleStateHandler idleHandler = 
                new IdleStateHandler(5, 10, 0);
        ReadTimeoutHandler readTimeoutHandler = 
                new ReadTimeoutHandler(30);
        
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("idle", idleHandler);
        pipeline.addLast("timeout", readTimeoutHandler);
        pipeline.addLast("handshaketimeout",
                         new HandshakeTimeoutHandler(channelHandler, timer, 10));

        pipeline.addLast("syncMessageDecoder",
                         new SyncMessageDecoder(maxFrameSize));
        pipeline.addLast("syncMessageEncoder",
                         new SyncMessageEncoder());

        pipeline.addLast("handler", channelHandler);
    }
}
