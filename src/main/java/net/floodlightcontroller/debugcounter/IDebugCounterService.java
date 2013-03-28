package net.floodlightcontroller.debugcounter;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.DebugCounter.CounterInfo;
import java.util.List;

public interface IDebugCounterService extends IFloodlightService {

    /**
     * Different counter types. Counters that are meant to be counted on demand
     * need to be separately enabled/disabled.
     */
    public enum CounterType {
        ALWAYS_COUNT,
        COUNT_ON_DEMAND
    }

    public class DebugCounterInfo {
        CounterInfo counterInfo;
        Long counterValue;

        public CounterInfo getCounterInfo() {
            return counterInfo;
        }
        public Long getCounterValue() {
            return counterValue;
        }
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
     *                             There should be only a single '-' in the name
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
     * Increments the counter by 1, if the counter is meant to be always counted,
     * or if the counter has been enabled for counting.
     * @param moduleCounterName   the registered counter name.
     */
    public void updateCounter(String moduleCounterName);

    /**
     * Update the global counter map with values from the thread local maps. This
     * method is not intended for use by any module. It's typical usage is from
     * floodlight core. As far as the modules are concerned, this should happen
     * automatically for their counters.
     */
    public void flushCounters();

    /**
     * Resets the value of the counter to zero if it is currently enabled. Note
     * that with live traffic, it is not necessary that the counter will display
     * zero with a get call as it may get updated between the reset and get calls.
     * @param moduleCounterName the registered counter name.
     */
    public void resetCounter(String moduleCounterName);

    /**
     * Resets the values of all counters that are currently enabled to zero.
     */
    public void resetAllCounters();

    /**
     * Resets the values of all counters that are currently active and belong
     * to a module with the given 'moduleName'. The moduleName MUST be the
     * part of the moduleCounterName with which the counters were registered.
     * eg. if 'linkdiscovery-incoming' and 'linkdiscovery-lldpeol' are two counters
     * the module name is 'linkdiscovery'
     * @param moduleName
     */
    public void resetAllModuleCounters(String moduleName);

    /**
     * This method applies only to CounterType.COUNT_ON_DEMAND. It is used to
     * enable counting on the counter. Note that this step is necessary to start
     * counting for these counter types - merely registering the counter is not
     * enough (as is the case for CounterType.ALWAYS_COUNT). Note that newly
     * enabled counter starts from an initial value of zero.
     *
     * @param moduleCounterName  the registered counter name.
     */
    public void enableCtrOnDemand(String moduleCounterName);

    /**
     * This method applies only to CounterType.ALWAYS_COUNT. It is used to disable
     * counting on this counter. Note that disabling a counter results in a loss
     * of the counter value. When re-enabled the counter will restart from zero.
     *
     * @param moduleCounterName the registered counter name.
     */
    public void disableCtrOnDemand(String moduleCounterName);

    /**
     * Get counter value and associated information for a specific counter if it
     * is active.
     *
     * @param moduleCounterName
     * @return DebugCounterInfo or null if the counter could not be found
     */
    public DebugCounterInfo getCounterValue(String moduleCounterName);

    /**
     * Get counter values and associated information for all active counters
     *
     * @return the list of values/info or an empty list
     */
    public  List<DebugCounterInfo> getAllCounterValues();

    /**
     * Get counter values and associated information for all active counters associated
     * with a module.
     *
     * @param moduleName
     * @return the list of values/info or an empty list
     */
    public  List<DebugCounterInfo> getModuleCounterValues(String moduleName);

    /**
     * Convenience method to figure out if the the given 'moduleCounterName' corresponds
     * to a registered moduleCounterName or not. Note that the counter may or
     * may not be enabled for counting, but if it is registered the method will
     * return true.
     *
     * @param param
     * @return false if moduleCounterName is not a registered counter
     */
    public boolean containsMCName(String moduleCounterName);

    /**
     * Convenience method to figure out if the the given 'moduleName' corresponds
     * to a registered moduleName or not. Note that the module may or may not have
     * a counter enabled for counting, but if it is registered the method will
     * return true.
     *
     * @param param
     * @return false if moduleName is not a registered counter
     */
    public boolean containsModName(String moduleName);

}
