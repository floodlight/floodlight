package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.IOFConnectionBackend;
import org.projectfloodlight.openflow.protocol.OFMessage;

public interface IOFConnectionListener {
    void connectionClosed(IOFConnectionBackend connection);

    void messageReceived(IOFConnectionBackend connection, OFMessage m);
    
    /**
     * Primarily for role requests, we need a way to tell the OFSwitchHandshakeHandler
     * that we've just sent a message (from e.g. a module). In this way, it'll list the
     * request as pending and be able to receive and process the reply 
     * from messageReceived() later when the reply with the correct XID comes in. This
     * will ensure the OFSwitchHandshakeHandler is always in the correct state.
     * 
     * @param connection
     * @param m
     */
    void messageWritten(IOFConnectionBackend connection, OFMessage m);
	
    boolean isSwitchHandshakeComplete(IOFConnectionBackend connection);
}
