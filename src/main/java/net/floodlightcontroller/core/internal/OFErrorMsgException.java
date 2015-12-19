package net.floodlightcontroller.core.internal;

import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFRequest;

/** raised/reported by Futures in @IOFConnection when a an
 *  {@link OFErrorMsg} is received in response to a {@link OFRequest}
 *  sent via {@link OFConnection#writeRequest(OFRequest)} or
 *  {@link OFConnection#writeStatsRequest(org.projectfloodlight.openflow.protocol.OFStatsRequest)}.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class OFErrorMsgException extends Exception {
    private static final long serialVersionUID = 1L;

    private final OFErrorMsg errorMessage;

    public OFErrorMsgException(final OFErrorMsg errorMessage) {
        super("OF error received: " + errorMessage.toString());
        this.errorMessage = errorMessage;
    }

    /** @return the received OFErrorMsg that caused the error to be raised.
     */
    public OFErrorMsg getErrorMessage() {
        return errorMessage;
    }
}
