package net.floodlightcontroller.core.util;

import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TIOStreamTransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;


/**
 * Serialize a single Thrift message into the channel.
 *
 * It uses thrift's {@link TCompactProtocol} and frames the message by preprending a four
 * byte length field (big endian).
 *
 * Note: needs to be subclasses with a concrete type implementing T.
 *
 * @author readams
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public abstract class ThriftFrameEncoder<T extends TBase<?,?>> extends MessageToByteEncoder<T> {
    @Override
    protected void encode(ChannelHandlerContext ctx, T msg, ByteBuf out)
            throws Exception {

        int lengthIndex = out.writerIndex();
        // length field, will be filled in later.
        out.writeInt(0);

        int startIndex = out.writerIndex();
        ByteBufOutputStream os = new ByteBufOutputStream(out);
        TCompactProtocol thriftProtocol =
                new TCompactProtocol(new TIOStreamTransport(os));
        msg.write(thriftProtocol);
        os.close();
        int endIndex = out.writerIndex();

        // update the length field
        int length = endIndex - startIndex;
        out.setInt(lengthIndex, length);
    }
}