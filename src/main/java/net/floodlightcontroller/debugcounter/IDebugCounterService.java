package net.floodlightcontroller.debugcounter;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.DebugCounter.CounterInfo;
import java.util.List;

public interface IDebugCounterService extends IFloodlightService {

    /**
     * Different counter types.
     */
    public enum CounterType {
        ALWAYS_COUNT,
        COUNT_ON_DEMAND
    }

    public class DebugCounterInfo {
        CounterInfo counterinfo;
        Long counterValue;
    }

    /**
     * All modules that wish to have the DebugCounterService count for them, must
     * register their counters by making this call (typically from that module's
     * 'startUp' method). The counter can then be updated, displayed, reset etc.
     * using the registered moduleCounterName.
     *
     * @param moduleCounterName    the counter name which MUST be have the following
     *                             syntax:  <module name>-<counter name>
     *                             eg.: linkdiscovery-incoming
     * @param counterDescription   a descriptive string that gives more information
     *                             of what the counter is measuring. For example,
     *                             "Measures the number of incoming packets seen by
     *                             this module".
     * @param counterType          One of CounterType. COUNT_ON_DEMAND counter types
     *                             need to be explicitly enabled/disabled using other
     *                             methods in this API -- i.e. registering them is
     *                             not enough to start counting.
     * @return                     false if the counter has already been registered
     *                             or if the moduleCounterName is not as expected.
     */
    public boolean registerCounter(String moduleCounterName, String counterDescription,
                                   CounterType counterType);

    /**
     * Increments the counter by 1.
     * @param moduleCounterName   the registered counter name.
     */
    public void updateCounter(String moduleCounterName);

    /**
     * Update the global counter map with values from the thread local maps
     */
    public void flushCounters();

    /**
     *
     * @param moduleCounterName
     */
    public void resetCounter(String moduleCounterName);

    public void resetAllCounters();

    public void resetAllModuleCounters(String moduleName);

    public void enableCtrOnDemand(String moduleCounterName);

    public void disableCtrOnDemand(String moduleCounterName);

    public DebugCounterInfo getCounterValue(String moduleCounterName);

    public  List<DebugCounterInfo> getAllCounterValues();

    public  List<DebugCounterInfo> getModuleCounterValues();

}
