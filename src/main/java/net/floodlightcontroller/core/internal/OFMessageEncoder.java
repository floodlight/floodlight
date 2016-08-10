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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.openflow.protocol.OFMessage;

/**
 * Encode an openflow message for output into a ChannelBuffer, for use in a
 * netty pipeline
 * @author readams
 */
public class OFMessageEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel,
                            Object msg) throws Exception {
        if (!(  msg instanceof List))
            return msg;

        @SuppressWarnings("unchecked")
        List<OFMessage> msglist = (List<OFMessage>)msg;
        int size = 0;
        for (OFMessage ofm :  msglist) {
                size += ofm.getLengthU();
        }

        ChannelBuffer buf = ChannelBuffers.buffer(size);;
        for (OFMessage ofm :  msglist) {
            ofm.writeTo(buf);
        }
        return buf;
    }

}
