package net.floodlightcontroller.core;

import org.openflow.protocol.statistics.OFDescriptionStatistics;

public interface IOFSwitchDriver {
    /**
     * Return an IOFSwitch object based on switch's manufacturer description
     * from OFDescriptionStatitics.
     * @param description
     */
    public IOFSwitch getOFSwitchImpl(OFDescriptionStatistics description);
}
