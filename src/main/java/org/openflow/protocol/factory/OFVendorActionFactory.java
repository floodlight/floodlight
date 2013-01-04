package org.openflow.protocol.factory;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFActionVendor;

/** Interface contract for an actionfactory that creates vendor-specific actions.
 *  VendorActionFactories are registered with the BasicFactory for a specific
 *  vendor id.
 *  <p>
 *  <b>Note:</b> Implementations are expected to be thread-safe.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public interface OFVendorActionFactory {

    /** parse the data from the wire, create and return a vendor-specific action.
     *
     * @param data contains a serialized vendor action at the current readerPosition.
     *    The full message is guaranteed to be available in the buffer.
     *
     * @return upon success returns a newly allocated vendor-specific
     *   action instance, and advances the readerPosition in data for the
     *   entire length. Upon failure, returns null and leaves the readerPosition
     *   in data unmodified.
     */
    OFActionVendor readFrom(ChannelBuffer data);
}
