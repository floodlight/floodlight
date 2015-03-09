/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core.internal;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.Timer;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

/**
 * Creates a ChannelPipeline for a server-side openflow channel
 * @author readams, sovietaced
 */
public class OpenflowPipelineFactory
    implements ChannelPipelineFactory, ExternalResourceReleasable {

    protected IOFSwitchManager switchManager;
    protected INewOFConnectionListener connectionListener;
    protected Timer timer;
    protected IdleStateHandler idleHandler;
    protected ReadTimeoutHandler readTimeoutHandler;
    protected IDebugCounterService debugCounters;

    public OpenflowPipelineFactory(IOFSwitchManager switchManager, Timer timer,
                                   INewOFConnectionListener connectionListener,
                                  IDebugCounterService debugCounters) {
        super();
        this.switchManager = switchManager;
        this.connectionListener = connectionListener;
        this.timer = timer;
        this.debugCounters = debugCounters;
        this.idleHandler = new IdleStateHandler(
                                                timer,
                                                PipelineIdleReadTimeout.MAIN,
                                                PipelineIdleWriteTimeout.MAIN,
                                                0);
        this.readTimeoutHandler = new ReadTimeoutHandler(timer, 30);
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        OFChannelHandler handler = new OFChannelHandler(switchManager,
                                                        connectionListener,
                                                        pipeline,
                                                        debugCounters,
                                                        timer);

        pipeline.addLast(PipelineHandler.OF_MESSAGE_DECODER,
                         new OFMessageDecoder());
        pipeline.addLast(PipelineHandler.OF_MESSAGE_ENCODER,
                         new OFMessageEncoder());
        pipeline.addLast(PipelineHandler.MAIN_IDLE, idleHandler);
        pipeline.addLast(PipelineHandler.READ_TIMEOUT, readTimeoutHandler);
        pipeline.addLast(PipelineHandler.CHANNEL_HANDSHAKE_TIMEOUT,
                         new HandshakeTimeoutHandler(
                                                     handler,
                                                     timer,
                                                     PipelineHandshakeTimeout.CHANNEL));
        pipeline.addLast(PipelineHandler.CHANNEL_HANDLER, handler);
        return pipeline;
    }

    @Override
    public void releaseExternalResources() {
        timer.stop();
    }

    public static class PipelineHandler {
        final static String CHANNEL_HANDSHAKE_TIMEOUT = "channelhandshaketimeout";
        final static String SWITCH_HANDSHAKE_TIMEOUT = "switchhandshaketimeout";
        final static String CHANNEL_HANDLER = "channelhandler";
        final static String MAIN_IDLE = "mainidle";
        final static String AUX_IDLE = "auxidle";
        final static String OF_MESSAGE_DECODER = "ofmessagedecoder";
        final static String OF_MESSAGE_ENCODER = "ofmessageencoder";
        final static String READ_TIMEOUT = "readtimeout";
    }

    /**
     * Timeouts for parts of the handshake, in seconds
     */
    public static class PipelineHandshakeTimeout {
        final static int CHANNEL = 10;
        final static int SWITCH = 30;
    }

    /**
     * Timeouts for writes on connections, in seconds
     */
    public static class PipelineIdleWriteTimeout {
        final static int MAIN = 2;
        final static int AUX = 15;
    }

    /**
     * Timeouts for reads on connections, in seconds
     */
    public static class PipelineIdleReadTimeout {
        final static int MAIN = 3 * PipelineIdleWriteTimeout.MAIN;
        final static int AUX = 3 * PipelineIdleWriteTimeout.AUX;
    }
}
