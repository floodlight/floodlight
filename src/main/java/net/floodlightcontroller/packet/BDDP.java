package net.floodlightcontroller.packet;

import java.util.ArrayList;

/**
 * This class defines the packet structure for broadcast domain discovery.
 * This class is an extended version of the LLDP class.  The only difference 
 * is that the eth-type is different from the standard LLDP.
 * 
 * @author srini
 *
 */
public class BDDP extends LLDP {
    public BDDP() {
        this.optionalTLVList = new ArrayList<LLDPTLV>();
        this.ethType = Ethernet.TYPE_BDDP;
    }
}
