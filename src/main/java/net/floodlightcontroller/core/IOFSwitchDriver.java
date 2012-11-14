package net.floodlightcontroller.core;

import org.openflow.protocol.statistics.OFDescriptionStatistics;

public interface IOFSwitchDriver {
    /**
     * Return an IOFSwitch object based on switch's manufacturer description
     * from OFDescriptionStatitics.
     * @param registered_desc string used to register this driver
     * @param description DescriptionStatistics from the switch instance
     */
    public IOFSwitch getOFSwitchImpl(String registered_desc,
            OFDescriptionStatistics description);
}
