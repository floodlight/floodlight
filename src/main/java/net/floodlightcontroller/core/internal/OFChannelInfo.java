package net.floodlightcontroller.core.internal;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.annotation.Nonnull;

import io.netty.channel.Channel;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPAddress;
import org.projectfloodlight.openflow.types.OFAuxId;

import com.google.common.base.Preconditions;

/** Basic information that {@link OFChannelHandler} attaches to the
 *  netty channel via {@link Channel#setAttachment(Object)}, mainly
 *  for the purpose of being able to log the connection information.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class OFChannelInfo {
    private final DatapathId id;
    private final OFAuxId auxId;
    private final IPAddress<?> address;
    private final int port;

    public OFChannelInfo(@Nonnull DatapathId id, @Nonnull OFAuxId auxId, @Nonnull SocketAddress address) {
        Preconditions.checkNotNull(id, "id should not be null");
        Preconditions.checkNotNull(auxId, "auxId should not be null");
        Preconditions.checkNotNull(address, "address should not be null");

        this.id = id;
        this.auxId = auxId;
        InetSocketAddress socketAddress = (InetSocketAddress) address;
        this.address = IPAddress.of(socketAddress.getHostString());
        this.port = socketAddress.getPort();
    }

    public DatapathId getId() {
        return id;
    }

    public OFAuxId getAuxId() {
        return auxId;
    }

    public IPAddress<?> getAddress() {
       return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return id + "/" + auxId + "@" + address + ":" + port;
    }
}