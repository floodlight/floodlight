package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;

public class OFBigSwitchVendorExtensions {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized)
            return;
    
        OFBasicVendorId bsnVendorId = 
                new OFBasicVendorId(OFBigSwitchVendorData.BSN_VENDOR_ID, 4);
        OFVendorId.registerVendorId(bsnVendorId);

        // register data types used for big tap
        OFBasicVendorDataType setEntryVendorData =
                new OFBasicVendorDataType(
                         OFNetmaskSetVendorData.BSN_SET_IP_MASK_ENTRY,
                         OFNetmaskSetVendorData.getInstantiable());
        bsnVendorId.registerVendorDataType(setEntryVendorData);

        OFBasicVendorDataType getEntryVendorDataRequest =
                new OFBasicVendorDataType(
                         OFNetmaskGetVendorDataRequest.BSN_GET_IP_MASK_ENTRY_REQUEST,
                         OFNetmaskGetVendorDataRequest.getInstantiable());
        bsnVendorId.registerVendorDataType(getEntryVendorDataRequest);

        OFBasicVendorDataType getEntryVendorDataReply =
                new OFBasicVendorDataType(
                         OFNetmaskGetVendorDataReply.BSN_GET_IP_MASK_ENTRY_REPLY,
                         OFNetmaskGetVendorDataReply.getInstantiable());
        bsnVendorId.registerVendorDataType(getEntryVendorDataReply);

        // register data types used for tunneling
        OFBasicVendorDataType getIntfIPVendorDataRequest = 
                new OFBasicVendorDataType(
                          OFInterfaceIPRequestVendorData.BSN_GET_INTERFACE_IP_REQUEST,
                          OFInterfaceIPRequestVendorData.getInstantiable());
        bsnVendorId.registerVendorDataType(getIntfIPVendorDataRequest);

        OFBasicVendorDataType getIntfIPVendorDataReply = 
                new OFBasicVendorDataType(
                          OFInterfaceIPReplyVendorData.BSN_GET_INTERFACE_IP_REPLY,
                          OFInterfaceIPReplyVendorData.getInstantiable());
        bsnVendorId.registerVendorDataType(getIntfIPVendorDataReply);

        
    }
}
