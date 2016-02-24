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

import org.projectfloodlight.openflow.protocol.OFMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;


/**
 * Encode an iterable of openflow messages for output into a ByteBuf, for use in a
 * netty pipeline
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class OFMessageEncoder extends MessageToByteEncoder<Iterable<OFMessage>> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Iterable<OFMessage> msgList, ByteBuf out) throws Exception {
        for (OFMessage ofm :  msgList) {
            ofm.writeTo(out);
        }
    }
}