package net.floodlightcontroller.debugevent.web;


import java.util.HashMap;
import java.util.List;
import java.util.Map;


import net.floodlightcontroller.debugevent.IDebugEventService.DebugEventInfo;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Return the debug event data for the get rest-api call
 *
 * URI must be in one of the following forms:
 * "http://{controller-hostname}:8080/wm/debugevent/{param}/json
 *
 *  where {param} must be one of (no quotes):
 *       "all"                  returns value/info on all active events.
 *       "{moduleName}"         returns value/info on all active events for the specified module.
 *       "{moduleCounterName}"  returns value/info for specific event if it is active.
 *
 * @author Saurav
 */
public class DebugEventResource extends DebugEventResourceBase {
    protected static Logger logger =
            LoggerFactory.getLogger(DebugEventResource.class);

    /**
     * The output JSON model that contains the counter information
     */
    public static class DebugEventInfoOutput {
        public Map<String, DebugEventInfo> eventMap;
        public String error;

        DebugEventInfoOutput() {
            eventMap = new HashMap<String, DebugEventInfo>();
            error = null;
        }
        public Map<String, DebugEventInfo> getEventMap() {
            return eventMap;
        }

        public String getError() {
            return error;
        }

    }

    public enum Option {
        ALL, ONE_MODULE, ONE_MODULE_EVENT, ERROR_BAD_MODULE_NAME, ERROR_BAD_PARAM,
        ERROR_BAD_MODULE_EVENT_NAME
    }

    @Get("json")
    public DebugEventInfoOutput handleEventInfoQuery() {
        DebugEventInfoOutput output = new DebugEventInfoOutput();
        Option choice = Option.ERROR_BAD_PARAM;

        String param = (String)getRequestAttributes().get("param");
        if (param == null) {
            param = "all";
            choice = Option.ALL;
        } else if (param.equals("all")) {
            choice = Option.ALL;
        } else if (param.contains("-")) {
            // differentiate between disabled and non-existing counters
            boolean isRegistered = debugEvent.containsMEName(param);
            if (isRegistered) {
                choice = Option.ONE_MODULE_EVENT;
            } else {
                choice = Option.ERROR_BAD_MODULE_EVENT_NAME;
            }
        } else {
            boolean isRegistered = debugEvent.containsModName(param);
            if (isRegistered) {
                choice = Option.ONE_MODULE;
            } else {
                choice = Option.ERROR_BAD_MODULE_NAME;
            }
        }

        switch (choice) {
            case ALL:
                populateEvents(debugEvent.getAllEventHistory(), output);
                break;
            case ONE_MODULE:
                populateEvents(debugEvent.getModuleEventHistory(param), output);
                break;
            case ONE_MODULE_EVENT:
                populateSingleEvent(debugEvent.getSingleEventHistory(param), output);
                break;
            case ERROR_BAD_MODULE_NAME:
                output.error = "Module name has no corresponding registered events";
                break;
            case ERROR_BAD_MODULE_EVENT_NAME:
                output.error = "Event not registered";
                break;
            case ERROR_BAD_PARAM:
                output.error = "Bad param";
        }

        return output;
    }

    private void populateSingleEvent(DebugEventInfo singleEventHistory,
                                     DebugEventInfoOutput output) {
        if (singleEventHistory != null) {
            output.eventMap.put(singleEventHistory.getEventInfo().getModuleEventName(),
                                singleEventHistory);
        }
    }

    private void populateEvents(List<DebugEventInfo> eventHistory,
                                DebugEventInfoOutput output) {
        if (eventHistory != null) {
            for (DebugEventInfo de : eventHistory)
                populateSingleEvent(de, output);
        }
    }
}
