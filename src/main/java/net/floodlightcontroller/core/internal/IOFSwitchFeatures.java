package net.floodlightcontroller.core.internal;

import org.openflow.protocol.statistics.OFDescriptionStatistics;

import net.floodlightcontroller.core.IOFSwitch;

public interface IOFSwitchFeatures {
    public void setFromDescription(IOFSwitch sw, OFDescriptionStatistics description);
}
