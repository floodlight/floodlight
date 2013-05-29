package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;


/**
 * Subclass of OFVendorData
 * 
 * @author munish_mehta
 */
public class OFNetmaskGetVendorDataRequest extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFNetmaskGetVendorDataRequest();
        }
    };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFNetmaskGetVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * Opcode/dataType to request an entry in the switch netmask table
     */
    public static final int BSN_GET_IP_MASK_ENTRY_REQUEST = 1;

    /**
     * Construct a get network mask vendor data
     */
    public OFNetmaskGetVendorDataRequest() {
        super(BSN_GET_IP_MASK_ENTRY_REQUEST);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFNetmaskGetVendorDataRequest(byte tableIndex, int netMask) {
        super(BSN_GET_IP_MASK_ENTRY_REQUEST, tableIndex, netMask);
    }
}
