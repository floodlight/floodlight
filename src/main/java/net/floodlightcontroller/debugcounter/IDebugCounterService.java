package net.floodlightcontroller.debugcounter;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.DebugCounter.DebugCounterInfo;

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

    /**
     *  A limit on the maximum number of counters that can be created
     */
    public static final int MAX_COUNTERS = 5000;

    /**
     * exception thrown when MAX_COUNTERS have been registered
     */
    public class MaxCountersRegistered extends Exception {
        private static final long serialVersionUID = 3173747663719376745L;
    }

    /**
     *  maximum level of hierarchical counters
     */
    public static final int MAX_HIERARCHY = 3;

    /**
     * All modules that wish to have the DebugCounterService count for them, must
     * register their counters by making this call (typically from that module's
     * 'startUp' method). The counter can then be updated, displayed, reset etc.
     * using the registered moduleCounterHierarchy.
     *
     * @param moduleCounterHierarchy    the counter name which MUST be have the following
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
     *                             or if the moduleCounterHierarchy is not as expected.
     */
    public IDebugCounter registerCounter(String moduleName, String counterHierarchy,
                             String counterDescription, CounterType counterType,
                             Object... metaData) throws MaxCountersRegistered;

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
     * @param moduleCounterHierarchy the registered counter name.
     */
    void resetCounterHierarchy(String moduleName, String counterHierarchy);

    /**
     * Resets the values of all counters that are currently enabled to zero.
     */
    public void resetAllCounters();

    /**
     * Resets the values of all counters that are currently active and belong
     * to a module with the given 'moduleName'. The moduleName MUST be the
     * part of the moduleCounterHierarchy with which the counters were registered.
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
     * @param moduleCounterHierarchy  the registered counter name.
     */
    public void enableCtrOnDemand(String moduleName, String counterHierarchy);

    /**
     * This method applies only to CounterType.ALWAYS_COUNT. It is used to disable
     * counting on this counter. Note that disabling a counter results in a loss
     * of the counter value. When re-enabled the counter will restart from zero.
     *
     * @param moduleCounterHierarchy the registered counter name.
     */
    public void disableCtrOnDemand(String moduleName, String counterHierarchy);

    /**
     * Get counter value and associated information for a specific counter if it
     * is active.
     *
     * @param moduleCounterHierarchy
     * @return DebugCounterInfo or null if the counter could not be found
     */
    public List<DebugCounterInfo> getCounterHierarchy(String moduleName,
                                                      String counterHierarchy);

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
     * Convenience method to figure out if the the given 'moduleCounterHierarchy' corresponds
     * to a registered moduleCounterHierarchy or not. Note that the counter may or
     * may not be enabled for counting, but if it is registered the method will
     * return true.
     *
     * @param param
     * @return false if moduleCounterHierarchy is not a registered counter
     */
    public boolean containsModuleCounterHierarchy(String moduleName, String counterHierarchy);

    /**
     * Convenience method to figure out if the the given 'moduleName' corresponds
     * to a registered moduleName or not. Note that the module may or may not have
     * a counter enabled for counting, but if it is registered the method will
     * return true.
     *
     * @param param
     * @return false if moduleName is not a registered counter
     */
    public boolean containsModuleName(String moduleName);


}
