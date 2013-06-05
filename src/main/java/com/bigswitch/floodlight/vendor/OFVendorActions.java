package com.bigswitch.floodlight.vendor;

import org.openflow.protocol.factory.OFVendorActionRegistry;

public final class OFVendorActions {
    public static final void registerStandardVendorActions() {
        OFVendorActionRegistry registry = OFVendorActionRegistry.getInstance();
        registry.register(OFActionBigSwitchVendor.BSN_VENDOR_ID, new OFBigSwitchVendorActionFactory());
        registry.register(OFActionNiciraVendor.NICIRA_VENDOR_ID, new OFNiciraVendorActionFactory());
    }
}