package net.floodlightcontroller.debugcounter;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reset debug counter values
 *
 * URI must be in one of the following forms:
 * "http://{controller-hostname}:8080/wm/core/debugcounter/reset/{param}/json
 *
 *  where {param} must be one of (no quotes):
 *       "all"                  resets all active counters.
 *       "{moduleName}"         resets all active counters for the specified module.
 *       "{moduleCounterName}"  resets specific counter if it is enabled.
 *
 * @author Saurav
 */
public class DebugCounterResetResource extends DebugCounterResourceBase {
    protected static Logger logger =
            LoggerFactory.getLogger(DebugCounterGetResource.class);

    public enum Option {
        ALL, ONE_MODULE, ONE_MODULE_COUNTER, ERROR_BAD_MODULE_NAME, ERROR_BAD_PARAM,
        ERROR_BAD_MODULE_COUNTER_NAME
    }

    public static class ResetOutput {
        String error;

        public ResetOutput() {
            error = null;
        }

        public String getError() {
            return error;
        }

    }

    @Get("json")
    public ResetOutput handleCounterResetCmd() {
        Option choice = Option.ERROR_BAD_PARAM;
        ResetOutput output = new ResetOutput();

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
                debugCounter.resetAllCounters();
                output.error = "None";
                break;
            case ONE_MODULE:
                debugCounter.resetAllModuleCounters(param);
                output.error = "None";
                break;
            case ONE_MODULE_COUNTER:
                debugCounter.resetCounter(param);
                output.error = "None";
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



}
