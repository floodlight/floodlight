package net.floodlightcontroller.core;

import org.openflow.protocol.OFMessage;

public interface IOFMessageFilterManagerService extends IFloodlightService {

    public String getDataAsString(IOFSwitch sw, OFMessage msg, 
                                  FloodlightContext cntx);

}
