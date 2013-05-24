package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;


/**
 * Subclass of OFVendorData
 * 
 * @author munish_mehta
 */
public class OFNetmaskGetVendorDataReply extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFNetmaskGetVendorDataReply();
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
     * Opcode/dataType to represent REPLY of GET_MASK request
     */
    public static final int BSN_GET_IP_MASK_ENTRY_REPLY = 2;

    /**
     * Construct a get network mask vendor data
     */
    public OFNetmaskGetVendorDataReply() {
        super(BSN_GET_IP_MASK_ENTRY_REPLY);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFNetmaskGetVendorDataReply(byte tableIndex, int netMask) {
        super(BSN_GET_IP_MASK_ENTRY_REPLY, tableIndex, netMask);
    }
}
