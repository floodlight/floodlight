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

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.factory.OFMessageFactory;

/**
 * Decode an openflow message from a Channel, for use in a netty
 * pipeline
 * @author readams
 */
public class OFMessageDecoder extends FrameDecoder {

    OFMessageFactory factory = new BasicFactory();
    
    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel,
                            ChannelBuffer buffer) throws Exception {
        if (!channel.isConnected()) {
            // In testing, I see decode being called AFTER decode last.
            // This check avoids that from reading curroupted frames
            return null;
        }

        List<OFMessage> message = factory.parseMessage(buffer);
        return message;
    }

    @Override
    protected Object decodeLast(ChannelHandlerContext ctx, Channel channel,
                            ChannelBuffer buffer) throws Exception {
        // This is not strictly needed atthis time. It is used to detect
        // connection reset detection from netty (for debug)
        return null;
    }

}
