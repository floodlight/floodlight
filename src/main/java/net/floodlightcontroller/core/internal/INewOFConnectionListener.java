package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.IOFConnectionBackend;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;

/** a listener that is notified when a new OFConnection has been opened and
 *  handshaked (i.e., the {@link OFFeaturesReply} has been received.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public interface INewOFConnectionListener {
    /** notify this listener that a new connection has been opened
     *
     * @param connection - the new connection
     * @param featuresReply - the {@link OFFeaturesReply} that was received.
     */
    void connectionOpened(IOFConnectionBackend connection,
                          OFFeaturesReply featuresReply);
}
