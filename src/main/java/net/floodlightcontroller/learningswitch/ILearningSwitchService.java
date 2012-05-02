package net.floodlightcontroller.learningswitch;

import java.util.Map;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.MacVlanPair;

public interface ILearningSwitchService extends IFloodlightService {
    /**
     * Returns the LearningSwitch's learned host table
     * @return The learned host table
     */
    public Map<IOFSwitch, Map<MacVlanPair,Short>> getTable();
}
