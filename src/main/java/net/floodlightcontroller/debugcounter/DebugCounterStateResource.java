package net.floodlightcontroller.debugcounter;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enable/disable on-demand counters
 *
 * URI must be in one of the following forms:
 * "http://{controller-hostname}:8080/wm/core/debugcounter/{moduleCounterName}/{state}/json
 *
 *  where {state} must be one of (no quotes):
 *       "enable"        enables counter {moduleCounterName} if it is an on-demand counter.
 *       "disable"       disables counter {moduleCounterName} if it is an on-demand counter.
 *
 * @author Saurav
 */
public class DebugCounterStateResource extends DebugCounterResourceBase {
    protected static Logger logger =
            LoggerFactory.getLogger(DebugCounterStateResource.class);

    public static class StateOutput {
        String error;

        public StateOutput() {
            error = null;
        }

        public String getError() {
            return error;
        }

    }

    @Get("json")
    public StateOutput handleCounterStateCmd() {
        StateOutput output = new StateOutput();

        String state = (String)getRequestAttributes().get("state");
        String moduleCounterName = (String)getRequestAttributes().get("moduleCounterName");

        if (!moduleCounterName.contains("-")) {
            output.error = "Specified moduleCounterName is not of type " +
                    "<moduleName>-<counterName>.";
            return output;
        }

        if ( !(state.equals("enable") || state.equals("disable")) ) {
            output.error = "State must be either enable or disable";
            return output;
        }

        if (state.equals("enable")) {
            debugCounter.enableCtrOnDemand(moduleCounterName);
        } else {
            debugCounter.disableCtrOnDemand(moduleCounterName);
        }
        output.error = "None";
        return output;
    }
}
