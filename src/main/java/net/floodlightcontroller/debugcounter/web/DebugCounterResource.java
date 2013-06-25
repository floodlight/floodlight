package net.floodlightcontroller.debugcounter.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.debugcounter.DebugCounter.DebugCounterInfo;
import net.floodlightcontroller.debugcounter.IDebugCounterService.CounterType;

/**
 * Web interface for Debug Counters
 *
 * @author Saurav
 */
public class DebugCounterResource extends DebugCounterResourceBase {
    protected static Logger logger =
            LoggerFactory.getLogger(DebugCounterResource.class);

    /**
     * The output JSON model that contains the counter information
     */
    public class DebugCounterInfoOutput {
        protected class DCInfo {
            private final Long counterValue;
            private final CounterType counterType;
            private final String counterDesc;
            private final boolean enabled;
            private final String counterHierarchy;
            private final String moduleName;
            private final String[] metaData;

            DCInfo(DebugCounterInfo dci) {
                this.moduleName = dci.getCounterInfo().getModuleName();
                this.counterHierarchy = dci.getCounterInfo().getCounterHierarchy();
                this.counterDesc = dci.getCounterInfo().getCounterDesc();
                this.metaData = dci.getCounterInfo().getMetaData();
                this.enabled = dci.getCounterInfo().isEnabled();
                this.counterType = dci.getCounterInfo().getCtype();
                this.counterValue = dci.getCounterValue();
            }

            public Long getCounterValue() {
                return counterValue;
            }
            public CounterType getCounterType() {
                return counterType;
            }

            public String getCounterDesc() {
                return counterDesc;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public String getCounterHierarchy() {
                return counterHierarchy;
            }

            public String getModuleName() {
                return moduleName;
            }

            public String[] getMetaData() {
                return metaData;
            }

        }
        // complete counter information - null if only names are requested or
        // if an error occurs
        public Map<String, DCInfo> counterMap;
        // list of names could be just moduleNames or counter hierarchical names
        // for a specific module
        public List<String> names;

        public String error;

        DebugCounterInfoOutput(boolean getList) {
            if (!getList) {
                counterMap = new HashMap<String, DCInfo>();
            }
            error = null;
        }
        public Map<String, DCInfo> getCounterMap() {
            return counterMap;
        }

        public String getError() {
            return error;
        }

        public List<String> getNames() {
            return names;
        }

    }

    public enum Option {
        ALL, ONE_MODULE, MODULE_COUNTER_HIERARCHY, ERROR_BAD_MODULE_NAME,
        ERROR_BAD_PARAM,
        ERROR_BAD_MODULE_COUNTER_NAME
    }

    public static class CounterPost {
        public Boolean reset;
        public Boolean enable;

        public Boolean getReset() {
            return reset;
        }
        public void setReset(Boolean reset) {
            this.reset = reset;
        }
        public Boolean getEnable() {
            return enable;
        }
        public void setEnable(Boolean enable) {
            this.enable = enable;
        }
    }

    public static class ResetOutput {
        String error = null;

        public String getError() {
            return error;
        }
        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Reset or enable/disable counters
     *
     * If using curl:
     * curl -X POST -d DATA -H "Content-Type: application/json" URL
     * where  DATA must be one of the following:
     *    {\"reset\":true}   to reset counters
     *    {\"enable\":true}  to enable counter
     *    {\"enable\":false} to disable counter
     * and URL must be in one of the following forms:
     *    "http://{controller-hostname}:8080/wm/debugcounter/{param1}/{param2}/{param3}/{param4}
     *
     * {param1} can be null, 'all' or the name of a module {moduleName}.
     * {param2}/{param3}/{param4} refer to hierarchical counter names.
     *
     * The Reset command will reset the counter specified as well as all counters
     * in the hierarchical levels below. For example, if a counter hierarchy exists
     * as switch/00:00:00:00:01:02:03:04/pktin/drops, then a reset command with just
     * the moduleName (param1=switch) and counterHierarchy (param2=00:00:00:00:01:02:03:04)
     * will reset all counters for that switch. Continuing the example -
     * for a counterHierarchy (param2=00:00:00:00:01:02:03:04 and param3=pktin), the reset
     * command will remove all pktin counters for that switch.
     *
     * The enable/disable command will ONLY disable a specific counter (and only if
     * that counter is of CounterType.ON_DEMAND)
     * It will not enable/disable counters at any other hierarchical level.
     *
     */
    @Post
    public ResetOutput postHandler(CounterPost postData) {
        ResetOutput output = new ResetOutput();
        Option choice = Option.ERROR_BAD_PARAM;
        String param1 = (String)getRequestAttributes().get("param1");
        String param2 = (String)getRequestAttributes().get("param2");
        String param3 = (String)getRequestAttributes().get("param3");
        String param4 = (String)getRequestAttributes().get("param4");
        String moduleName = "";

        if (param1 == null) {
             moduleName = "all";
            choice = Option.ALL;
        } else if (param1.equals("all")) {
            moduleName = "all";
            choice = Option.ALL;
        } else {
            moduleName = param1;
        }

        String counterHierarchy = "";
        if (param2 != null) {
            counterHierarchy += param2;
            if (param3 != null) {
                counterHierarchy += "/" + param3;
                if (param4 != null) {
                    counterHierarchy += "/" + param4;
                }
            }
        }

        if (!moduleName.equals("all") && counterHierarchy.equals("")) {
            // only module name specified
            boolean isRegistered = debugCounter.containsModuleName(param1);
            if (isRegistered) {
                choice = Option.ONE_MODULE;
            } else {
                choice = Option.ERROR_BAD_MODULE_NAME;
            }
        } else if (!moduleName.equals("all") && !counterHierarchy.equals("")) {
            // both module and counter names specified
            boolean isRegistered = debugCounter.
                    containsModuleCounterHierarchy(moduleName, counterHierarchy);
            if (isRegistered) {
                choice = Option.MODULE_COUNTER_HIERARCHY;
            } else {
                choice = Option.ERROR_BAD_MODULE_COUNTER_NAME;
            }
        }

        boolean reset = false;
        boolean turnOnOff = false;
        if (postData.getReset() != null && postData.getReset()) {
            reset = true;
        }
        if (postData.getEnable() != null) {
            turnOnOff = true;
        }

        switch (choice) {
            case ALL:
                if (reset) debugCounter.resetAllCounters();
                break;
            case ONE_MODULE:
                if (reset) debugCounter.resetAllModuleCounters(moduleName);
                break;
            case MODULE_COUNTER_HIERARCHY:
                if (reset)
                    debugCounter.resetCounterHierarchy(moduleName, counterHierarchy);
                else if (turnOnOff && postData.getEnable())
                    debugCounter.enableCtrOnDemand(moduleName, counterHierarchy);
                else if (turnOnOff && !postData.getEnable())
                    debugCounter.disableCtrOnDemand(moduleName, counterHierarchy);
                break;
            case ERROR_BAD_MODULE_NAME:
                output.error = "Module name has no corresponding registered counters";
                break;
            case ERROR_BAD_MODULE_COUNTER_NAME:
                output.error = "Counter not registered";
                break;
            case ERROR_BAD_PARAM:
                output.error = "Bad param";
        }

        return output;
    }

    /**
     * Return the debug counter data for the get rest-api call
     *
     * URI must be in one of the following forms:
     * "http://{controller-hostname}:8080/wm/debugcounter/{param1}/{param2}/{param3}/{param4}"
     *
     *  where {param1} must be one of (no quotes):
     *       null                   if nothing is given then by default all
     *                              module names are returned for which counters
     *                              have been registered
     *       "all"                  returns value/info on all counters.
     *       "{moduleName}"         returns value/info on all counters for
     *                              the specified module 'moduelName'.
     * & {param2}/{param3}/{param4} refer to hierarchical counter names.
     *                              eg. 00:00:00:00:01:02:03:04/pktin/drops
     *                              where leaving out any of the params returns
     *                              all counters in the hierarchical level below.
     *                              So giving just the switch dpid will fetch
     *                              all counters for that switch.
     *                              A special case => if param2 is null, then
     *                              all hierarchical counterNames are returned
     *                              for the given moduleName (in param1)
     */
    @Get
    public DebugCounterInfoOutput handleCounterInfoQuery() {
        DebugCounterInfoOutput output;
        Option choice = Option.ERROR_BAD_PARAM;
        String param1 = (String)getRequestAttributes().get("param1");
        String param2 = (String)getRequestAttributes().get("param2");
        String param3 = (String)getRequestAttributes().get("param3");
        String param4 = (String)getRequestAttributes().get("param4");

        if (param1 == null) {
            output = new DebugCounterInfoOutput(true);
            return listCounters(output);
        } else if (param1.equals("all")) {
            output = new DebugCounterInfoOutput(false);
            populateCounters(debugCounter.getAllCounterValues(), output);
            return output;
        }

        output = new DebugCounterInfoOutput(false);
        String counterHierarchy = "";
        if (param2 == null) {
            // param2 is null -- return list of counternames for param1
            boolean isRegistered = debugCounter.containsModuleName(param1);
            output = new DebugCounterInfoOutput(true);
            if (isRegistered) {
                return listCounters(param1, output);
            } else {
                choice = Option.ERROR_BAD_MODULE_NAME;
            }
        } else if (param2.equals("all")) {
            // get all counter info for a single module
            boolean isRegistered = debugCounter.containsModuleName(param1);
            if (isRegistered) {
                choice = Option.ONE_MODULE;
            } else {
                choice = Option.ERROR_BAD_MODULE_NAME;
            }
        } else {
            counterHierarchy += param2;
            if (param3 != null) {
                counterHierarchy += "/" + param3;
                if (param4 != null) {
                    counterHierarchy += "/" + param4;
                }
            }
            boolean isRegistered = debugCounter.
                    containsModuleCounterHierarchy(param1, counterHierarchy);
            if (isRegistered) {
                choice = Option.MODULE_COUNTER_HIERARCHY;
            } else {
                choice = Option.ERROR_BAD_MODULE_COUNTER_NAME;
            }
        }

        switch (choice) {
            case ONE_MODULE:
                populateCounters(debugCounter.getModuleCounterValues(param1), output);
                break;
            case MODULE_COUNTER_HIERARCHY:
                populateCounters(debugCounter.getCounterHierarchy(param1, counterHierarchy),
                                      output);
                break;
            case ERROR_BAD_MODULE_NAME:
                output.error = "Module name is not registered for debug-counters";
                break;
            case ERROR_BAD_MODULE_COUNTER_NAME:
                output.error = "Counter not registered";
                break;
            case ERROR_BAD_PARAM:
            default:
                output.error = "Bad param";
        }

        return output;
    }

    private DebugCounterInfoOutput listCounters(String moduleName,
                                                DebugCounterInfoOutput output) {
        output.names = debugCounter.getModuleCounterList(moduleName);
        return output;
    }

    private DebugCounterInfoOutput listCounters(DebugCounterInfoOutput output) {
        output.names = debugCounter.getModuleList();
        return output;
    }

    private void populateSingleCounter(DebugCounterInfo debugCounterInfo,
                                       DebugCounterInfoOutput output) {
        if (debugCounterInfo != null)
            output.counterMap.put(debugCounterInfo.getCounterInfo().
                                  getModuleCounterHierarchy(),
                                  output.new DCInfo(debugCounterInfo));
    }

    private void populateCounters(List<DebugCounterInfo> counterValues,
                                        DebugCounterInfoOutput output) {
        for (DebugCounterInfo dci : counterValues) {
            populateSingleCounter(dci, output);
        }
    }



}
