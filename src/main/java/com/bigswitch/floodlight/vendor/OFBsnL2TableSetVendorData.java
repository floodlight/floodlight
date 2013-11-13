package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

public class OFBsnL2TableSetVendorData extends OFBsnL2TableVendorData {

    protected static Instantiable<OFVendorData> instantiableSingleton = 
        new Instantiable<OFVendorData>() {
            public OFVendorData instantiate() {
                return new OFBsnL2TableSetVendorData();
            }
        };
        
    public static final int BSN_L2_TABLE_SET = 12;
        
    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFBsnL2TableSetVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiableSingleton;
    }
    
    public OFBsnL2TableSetVendorData() {
        super(BSN_L2_TABLE_SET);
    }
    
    public OFBsnL2TableSetVendorData(boolean l2TableEnabled, 
                                     short l2TablePriority) {
        super(BSN_L2_TABLE_SET, l2TableEnabled, l2TablePriority);
    }
}
