package org.openflow.vendor.openflow;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

/**
 * Class that represents the vendor data in the queue modify request
 * 
 * @author Andrew Ferguson (adf@cs.brown.edu)
 */
public class OFQueueModifyVendorData extends OFQueueVendorData {

    protected static Instantiable<OFVendorData> instantiable =
            new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new OFQueueModifyVendorData();
                }
            };
	
    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFQueueModifyVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }
            
    /**
     * The data type value for a queue modify request
     */
    public static final int OFP_EXT_QUEUE_MODIFY = 0;
}
