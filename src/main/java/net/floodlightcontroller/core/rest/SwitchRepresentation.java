package net.floodlightcontroller.core.rest;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import net.floodlightcontroller.core.IOFConnection;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

import com.google.common.base.Preconditions;

/**
 * REST representation of an OF Switch. Stitches together data from different
 * areas of the platform to provide a complete, centralized representation.
 * @author Jason Parraga <Jason.Parraga@bigswitch.com>
 *
 */
public class SwitchRepresentation {

    private final long buffers;
    private final Set<OFCapabilities> capabilities;
    private final Short tables;
    private final SocketAddress inetAddress;
    private final Collection<OFPortDesc> sortedPorts;
    private final boolean isConnected;
    private final Date connectedSince;
    private final DatapathId dpid;
    private final Map<Object, Object> attributes;
    private final boolean isActive;

    private final Collection<IOFConnection> connections;
    private final String handshakeState;
    private final String quarantineReason;

    public SwitchRepresentation(@Nonnull IOFSwitch sw, @Nonnull OFSwitchHandshakeHandler handshakeHandler) {
        Preconditions.checkNotNull(sw, "switch must not be null");
        Preconditions.checkNotNull(handshakeHandler, "handshakeHandler must not be null");

        // IOFSwitch
        this.buffers = sw.getBuffers();
        this.capabilities = sw.getCapabilities();
        this.tables = sw.getNumTables();
        this.inetAddress = sw.getInetAddress();
        this.sortedPorts = sw.getSortedPorts();
        this.isConnected = sw.isConnected();
        this.connectedSince = sw.getConnectedSince();
        this.dpid = sw.getId();
        this.attributes = sw.getAttributes();
        this.isActive = sw.isActive();

        // OFSwitchHandshakeHandler
        this.connections = handshakeHandler.getConnections();
        this.handshakeState = handshakeHandler.getState();
        this.quarantineReason = handshakeHandler.getQuarantineReason();
    }

    public long getBuffers() {
        return this.buffers;
    }

    public Short getTables() {
        return this.tables;
    }

    public Set<OFCapabilities> getCapabilities() {
        return this.capabilities;
    }

    public SocketAddress getInetAddress() {
        return this.inetAddress;
    }

    public Collection<OFPortDesc> getSortedPorts() {
        return this.sortedPorts;
    }

    public boolean isConnected() {
        return this.isConnected;
    }

    public Date getConnectedSince() {
        return this.connectedSince;
    }

    public DatapathId getDpid() {
        return this.dpid;
    }

    public Map<Object, Object> getAttributes() {
        return this.attributes;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public Collection<IOFConnection> getConnections() {
        return this.connections;
    }

    public String getHandshakeState() {
        return this.handshakeState;
    }

    public String getQuarantineReason() {
        return this.quarantineReason;
    }
}
