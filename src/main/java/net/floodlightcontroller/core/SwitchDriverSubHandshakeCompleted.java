package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFMessage;

/**
 * Indicates that a message was passed to a switch driver's subhandshake
 * handling code but the driver has already completed the sub-handshake
 * @author gregor
 *
 */
public class SwitchDriverSubHandshakeCompleted
        extends SwitchDriverSubHandshakeException {
    private static final long serialVersionUID = -8817822245846375995L;

    public SwitchDriverSubHandshakeCompleted(OFMessage m) {
        super("Sub-Handshake is already complete but received message " +
              m.getType());
    }
}
