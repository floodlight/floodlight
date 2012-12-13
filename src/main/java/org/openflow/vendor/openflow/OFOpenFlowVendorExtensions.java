package org.openflow.vendor.openflow;

import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;

public class OFOpenFlowVendorExtensions {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized)
            return;

        // Configure openflowj to be able to parse the OpenFlow extensions.
        OFBasicVendorId openflowVendorId =
                new OFBasicVendorId(OFOpenFlowVendorData.OF_VENDOR_ID, 4);
        OFVendorId.registerVendorId(openflowVendorId);

        OFBasicVendorDataType queueModifyVendorData =
                new OFBasicVendorDataType(OFQueueModifyVendorData.OFP_EXT_QUEUE_MODIFY,
                        OFQueueModifyVendorData.getInstantiable());
        openflowVendorId.registerVendorDataType(queueModifyVendorData);

        OFBasicVendorDataType queueDeleteVendorData =
                new OFBasicVendorDataType(OFQueueDeleteVendorData.OFP_EXT_QUEUE_DELETE,
                        OFQueueModifyVendorData.getInstantiable());
        openflowVendorId.registerVendorDataType(queueDeleteVendorData);

        initialized = true;
    }
}
