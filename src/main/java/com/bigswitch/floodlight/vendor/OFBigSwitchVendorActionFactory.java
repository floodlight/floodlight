package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFActionVendor;
import org.openflow.protocol.factory.OFVendorActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OFBigSwitchVendorActionFactory implements OFVendorActionFactory {
    protected static Logger logger =
            LoggerFactory.getLogger(OFBigSwitchVendorActionFactory.class);

    static class OFActionBigSwitchVendorDemux extends OFActionBigSwitchVendor {
        OFActionBigSwitchVendorDemux() {
            super((short) 0);
        }
    }

    @Override
    public OFActionVendor readFrom(ChannelBuffer data) {
        data.markReaderIndex();
        OFActionBigSwitchVendor demux = new OFActionBigSwitchVendorDemux();
        demux.readFrom(data);
        data.resetReaderIndex();

        switch(demux.getSubtype()) {
            case OFActionMirror.BSN_ACTION_MIRROR:
                OFActionMirror mirrorAction = new OFActionMirror((short) 0);
                mirrorAction.readFrom(data);
                return mirrorAction;
            case OFActionTunnelDstIP.SET_TUNNEL_DST_SUBTYPE:
                OFActionTunnelDstIP tunnelAction = new OFActionTunnelDstIP();
                tunnelAction.readFrom(data);
                return tunnelAction;
            default:
                logger.error("Unknown BSN vendor action subtype: "+demux.getSubtype());
                return null;
        }
    }

}
