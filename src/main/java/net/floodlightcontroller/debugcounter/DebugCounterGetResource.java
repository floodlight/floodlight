package net.floodlightcontroller.debugcounter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.debugcounter.IDebugCounterService.DebugCounterInfo;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Return the debug counter data for the get rest-api call
 *
 * URI must be in one of the following forms:
 * "http://<controller-hostname>:8080/wm/core/debugcounter/<param>/json
 *
 *  where <param> must be one of (no quotes)
 *       all                  returns value/info on all active counters
 *       <moduleName>         returns value/info on all active counters for the specified module
 *       <moduleCounterName>  returns value/info for specific counter if it is enabled
 *
 * @author Saurav
 */
public class DebugCounterGetResource extends DebugCounterResourceBase {
    protected static Logger logger =
            LoggerFactory.getLogger(DebugCounterGetResource.class);

    /**
     * The output JSON model that contains the counter information
     */
    public static class DebugCounterInfoOutput {
        public Map<String, DebugCounterInfo> counterMap;
        public String error;

        DebugCounterInfoOutput() {
            counterMap = new HashMap<String, DebugCounterInfo>();
            error = null;
        }
        public Map<String, DebugCounterInfo> getCounterMap() {
            return counterMap;
        }

        public String getError() {
            return error;
        }

    }

    public enum Option {
        ALL, ONE_MODULE, ONE_MODULE_COUNTER, ERROR_BAD_MODULE_NAME, ERROR_BAD_PARAM,
        ERROR_BAD_MODULE_COUNTER_NAME
    }

    @Get("json")
    public DebugCounterInfoOutput handleCounterInfoQuery() {
        DebugCounterInfoOutput output = new DebugCounterInfoOutput();
        Option choice = Option.ERROR_BAD_PARAM;

        String param = (String)getRequestAttributes().get("param");
        if (param == null) {
            param = "all";
            choice = Option.ALL;
        } else if (param.equals("all")) {
            choice = Option.ALL;
        } else if (param.contains("-")) {
            // differentiate between disabled and non-existing counters
            boolean isRegistered = debugCounter.containsMCName(param);
            if (isRegistered) {
                choice = Option.ONE_MODULE_COUNTER;
            } else {
                choice = Option.ERROR_BAD_MODULE_COUNTER_NAME;
            }
        } else {
            boolean isRegistered = debugCounter.containsModName(param);
            if (isRegistered) {
                choice = Option.ONE_MODULE;
            } else {
                choice = Option.ERROR_BAD_MODULE_NAME;
            }
        }

        switch (choice) {
            case ALL:
                poplulateAllCounters(debugCounter.getAllCounterValues(), output);
                break;
            case ONE_MODULE:
                populateModuleCounters(debugCounter.getModuleCounterValues(param), output);
                break;
            case ONE_MODULE_COUNTER:
                populateSingleCounter(debugCounter.getCounterValue(param), output);
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

    private void populateSingleCounter(DebugCounterInfo debugCounterInfo,
                                       DebugCounterInfoOutput output) {
        if (debugCounterInfo != null)
            output.counterMap.put(debugCounterInfo.counterInfo.moduleCounterName,
                                  debugCounterInfo);
    }

    private void populateModuleCounters(List<DebugCounterInfo> moduleCounterValues,
                                        DebugCounterInfoOutput output) {
        for (DebugCounterInfo dci : moduleCounterValues) {
            populateSingleCounter(dci, output);
        }
    }

    private void poplulateAllCounters(List<DebugCounterInfo> allCounterValues,
                                      DebugCounterInfoOutput output) {
        for (DebugCounterInfo dci : allCounterValues) {
            populateSingleCounter(dci, output);
        }
    }



}
