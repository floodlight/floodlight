package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.IOFConnectionBackend;
import org.projectfloodlight.openflow.protocol.OFMessage;

public interface IOFConnectionListener {
    void connectionClosed(IOFConnectionBackend connection);

    void messageReceived(IOFConnectionBackend connection, OFMessage m);

    boolean isSwitchHandshakeComplete(IOFConnectionBackend connection);
}
