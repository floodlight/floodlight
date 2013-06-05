package org.sdnplatform.sync.internal.rpc;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.sdnplatform.sync.thrift.SyncMessage;


/**
 * Encode a {@link SyncMessage} into the channel
 * @author readams
 *
 */
public class ThriftFrameEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel,
                            Object message) throws Exception {
        if (message instanceof SyncMessage) {
            ChannelBuffer buf = new DynamicChannelBuffer(512);
            ChannelBufferOutputStream os = new ChannelBufferOutputStream(buf);
            TCompactProtocol thriftProtocol =
                    new TCompactProtocol(new TIOStreamTransport(os));
            ((SyncMessage) message).write(thriftProtocol);

            ChannelBuffer len = ChannelBuffers.buffer(4);
            len.writeInt(buf.readableBytes());
            return ChannelBuffers.wrappedBuffer(len, buf);
        }
        return message;
    }

}
