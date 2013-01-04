package org.openflow.protocol.action;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openflow.protocol.factory.OFVendorActionRegistry;

public class OFVendorActionRegistryTest {

    @Test
    public void test() {
        MockVendorActionFactory factory = new MockVendorActionFactory();
        OFVendorActionRegistry.getInstance().register(MockVendorAction.VENDOR_ID, factory);
        assertEquals(factory, OFVendorActionRegistry.getInstance().get(MockVendorAction.VENDOR_ID));
    }

}
