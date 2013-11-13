package org.openflow.vendor.nicira;

import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;

public class OFNiciraVendorExtensions {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized)
            return;

        // Configure openflowj to be able to parse the role request/reply
        // vendor messages.
        OFBasicVendorId niciraVendorId =
                new OFBasicVendorId(OFNiciraVendorData.NX_VENDOR_ID, 4);
        OFVendorId.registerVendorId(niciraVendorId);
        OFBasicVendorDataType roleRequestVendorData =
                new OFBasicVendorDataType(OFRoleRequestVendorData.NXT_ROLE_REQUEST,
                        OFRoleRequestVendorData.getInstantiable());
        niciraVendorId.registerVendorDataType(roleRequestVendorData);
        OFBasicVendorDataType roleReplyVendorData =
                new OFBasicVendorDataType(OFRoleReplyVendorData.NXT_ROLE_REPLY,
                        OFRoleReplyVendorData.getInstantiable());
        niciraVendorId.registerVendorDataType(roleReplyVendorData);

        initialized = true;
    }
}
