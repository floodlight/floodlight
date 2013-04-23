package org.sdnplatform.sync.internal.rpc;

import java.util.ArrayList;
import java.util.List;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.sdnplatform.sync.thrift.SyncMessage;

/**
 * Decode a {@link SyncMessage} from the channel
 * @author readams
 */
public class ThriftFrameDecoder extends LengthFieldBasedFrameDecoder {

    public ThriftFrameDecoder(int maxSize) {
        super(maxSize, 0, 4, 0, 4);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx,
                            Channel channel,
                            ChannelBuffer buffer) throws Exception {
        List<SyncMessage> ms = null;
        ChannelBuffer frame = null;
        while (null != (frame = (ChannelBuffer) super.decode(ctx, channel, 
                                                             buffer))) {
            if (ms == null) ms = new ArrayList<SyncMessage>();
            ChannelBufferInputStream is = new ChannelBufferInputStream(frame);
            TCompactProtocol thriftProtocol =
                    new TCompactProtocol(new TIOStreamTransport(is));
            SyncMessage bsm = new SyncMessage();
            bsm.read(thriftProtocol);
            ms.add(bsm);
        }
        return ms;
    }

    @Override
    protected ChannelBuffer extractFrame(ChannelBuffer buffer,
                                         int index, int length) {
        return buffer.slice(index, length);
    }
}
