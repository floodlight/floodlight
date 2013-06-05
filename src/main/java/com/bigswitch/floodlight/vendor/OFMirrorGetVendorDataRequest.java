package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;


/**
 * Subclass of OFVendorData
 */
public class OFMirrorGetVendorDataRequest extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFMirrorGetVendorDataRequest();
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
    public static final int BSN_GET_MIRRORING_REQUEST = 4;

    /**
     * Construct a get network mask vendor data
     */
    public OFMirrorGetVendorDataRequest() {
        super(BSN_GET_MIRRORING_REQUEST);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFMirrorGetVendorDataRequest(byte tableIndex, int netMask) {
        super(BSN_GET_MIRRORING_REQUEST, tableIndex, netMask);
    }
}
