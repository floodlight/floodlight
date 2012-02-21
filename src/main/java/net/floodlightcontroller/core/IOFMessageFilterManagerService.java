package net.floodlightcontroller.core;

import net.floodlightcontroller.core.module.IFloodlightService;

import org.openflow.protocol.OFMessage;

public interface IOFMessageFilterManagerService extends IFloodlightService {

    public String getDataAsString(IOFSwitch sw, OFMessage msg, 
                                  FloodlightContext cntx);

}
