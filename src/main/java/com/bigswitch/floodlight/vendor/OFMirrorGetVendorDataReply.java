package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;


/**
 * Subclass of OFVendorData 
 */
public class OFMirrorGetVendorDataReply extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFMirrorGetVendorDataReply();
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
    public static final int BSN_GET_MIRRORING_REPLY = 5;

    /**
     * Construct a get network mask vendor data
     */
    public OFMirrorGetVendorDataReply() {
        super(BSN_GET_MIRRORING_REPLY);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFMirrorGetVendorDataReply(byte tableIndex, int netMask) {
        super(BSN_GET_MIRRORING_REPLY, tableIndex, netMask);
    }
}
