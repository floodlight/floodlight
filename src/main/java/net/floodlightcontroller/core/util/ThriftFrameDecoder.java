package net.floodlightcontroller.core.util;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TIOStreamTransport;

/**
 * Slice and decode Thrift Serialized Messages from the channel, push a {@link List} of Messages upstream.
 *
 * Each message is preceeded by a four-byte length field.
 *
 * Subclasses should implement {@link #allocateMessage()} to allocate an instance of the
 * desired message type.
 *
 * Implementation note: this decoder class was initially built for netty3, and is unnecessarily
 * complex and inelegant. In particular, netty has efficient support for handling lists of messages
 * now, so this could be replaced by a plain {@link LengthFieldBasedFrameDecoder} to do the slicing
 * and then a simple ThriftDecoder that would just decode one message at a time.
 *
 * @author readams
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public abstract class ThriftFrameDecoder<T extends TBase<?,?>> extends LengthFieldBasedFrameDecoder {
    public ThriftFrameDecoder(int maxSize) {
        super(maxSize, 0, 4, 0, 4);
    }

    protected abstract T allocateMessage();

    @Override
    protected final Object decode(ChannelHandlerContext ctx,
                            ByteBuf buffer) throws Exception {
        /* This is initialized to null because the decode function must return
         * null if the buffer does not contain a complete frame and cannot be
         * decoded.
         */
        List<T> ms = null;
        ByteBuf frame = null;
        while (null != (frame = (ByteBuf) super.decode(ctx, buffer))) {
            if (ms == null) ms = new ArrayList<T>();
            ByteBufInputStream is = new ByteBufInputStream(frame);
            TCompactProtocol thriftProtocol =
                    new TCompactProtocol(new TIOStreamTransport(is));
            T message = allocateMessage();
            message.read(thriftProtocol);
            ms.add(message);
        }
        return ms;
    }

    @Override
    protected final ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index,
            int length) {
        return buffer.slice(index, length);
    }
}