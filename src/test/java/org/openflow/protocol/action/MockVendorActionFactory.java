package org.openflow.protocol.action;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.factory.OFVendorActionFactory;

public class MockVendorActionFactory implements OFVendorActionFactory {

    @Override
    public OFActionVendor readFrom(ChannelBuffer data) {
        MockVendorAction action = new MockVendorAction();
        action.readFrom(data);
        return action;
    }

}
