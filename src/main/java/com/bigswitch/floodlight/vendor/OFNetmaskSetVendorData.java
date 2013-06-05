package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

public class OFNetmaskSetVendorData extends OFNetmaskVendorData {


    protected static Instantiable<OFVendorData> instantiable =
        new Instantiable<OFVendorData>() {
        public OFVendorData instantiate() {
            return new OFNetmaskSetVendorData();
        }
    };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFNetmaskSetVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * Opcode/dataType to set an entry in the switch netmask table
     */
    public static final int BSN_SET_IP_MASK_ENTRY = 0;
    
    /**
     * Construct a get network mask vendor data
     */
    public OFNetmaskSetVendorData() {
        super(BSN_SET_IP_MASK_ENTRY);   
    }
    
    /**
     * Construct a get network mask vendor data for a specific table entry
     */
    public OFNetmaskSetVendorData(byte tableIndex, int netMask) {
        super(BSN_SET_IP_MASK_ENTRY, tableIndex, netMask);
    }
}
