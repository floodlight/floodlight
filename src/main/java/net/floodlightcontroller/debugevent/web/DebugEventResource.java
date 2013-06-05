package net.floodlightcontroller.debugevent.web;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.debugevent.DebugEvent.EventInfo;
import net.floodlightcontroller.debugevent.IDebugEventService.DebugEventInfo;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web interface for Debug Events
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
        public Map<String, DebugEventInfo> eventMap = null;
        public List<EventInfo> eventList = null;
        public String error = null;

        DebugEventInfoOutput(boolean getList) {
            if (!getList) {
                eventMap = new HashMap<String, DebugEventInfo>();
            }
        }
        public Map<String, DebugEventInfo> getEventMap() {
            return eventMap;
        }
        public List<EventInfo> getEventList() {
            return eventList;
        }
        public String getError() {
            return error;
        }

    }

    public enum Option {
        ALL, ONE_MODULE, ONE_MODULE_EVENT, ERROR_BAD_MODULE_NAME, ERROR_BAD_PARAM,
        ERROR_BAD_MODULE_EVENT_NAME
    }

    public static class DebugEventPost {
        public Boolean reset;

        public Boolean getReset() {
            return reset;
        }
        public void setReset(Boolean reset) {
            this.reset = reset;
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
     * Reset events
     *
     * If using curl:
     * curl -X POST -d {\"reset\":true} -H "Content-Type: application/json" URL
     * where URL must be in one of the following forms for resetting registered events:
     * "http://{controller-hostname}:8080/wm/debugevent/
     * "http://{controller-hostname}:8080/wm/debugevent/{param1}
     * "http://{controller-hostname}:8080/wm/debugevent/{param1}/{param2}
     *
     * Not giving {param1} will reset all events
     * {param1} can be 'all' or the name of a module. The former case will reset
     * all events, while the latter will reset all events for the moduleName.
     * {param2} must be an eventName for the given moduleName
     */
    @Post
    public ResetOutput postHandler(DebugEventPost postData) {
        ResetOutput output = new ResetOutput();
        String param1 = (String)getRequestAttributes().get("param1");
        String param2 = (String)getRequestAttributes().get("param2");

        if (postData.getReset() != null && postData.getReset()) {
            Option choice = Option.ERROR_BAD_PARAM;

            if (param1 == null) {
                param1 = "all";
                choice = Option.ALL;
            } else if (param1.equals("all")) {
                choice = Option.ALL;
            } else if (param2 == null) {
                boolean isRegistered = debugEvent.containsModuleName(param1);
                if (isRegistered) {
                    choice = Option.ONE_MODULE;
                } else {
                    choice = Option.ERROR_BAD_MODULE_NAME;
                }
            } else {
                // differentiate between disabled and non-existing events
                boolean isRegistered = debugEvent.containsModuleEventName(param1, param2);
                if (isRegistered) {
                    choice = Option.ONE_MODULE_EVENT;
                } else {
                    choice = Option.ERROR_BAD_MODULE_EVENT_NAME;
                }
            }

            switch (choice) {
                case ALL:
                    debugEvent.resetAllEvents();
                    break;
                case ONE_MODULE:
                    debugEvent.resetAllModuleEvents(param1);
                    break;
                case ONE_MODULE_EVENT:
                    debugEvent.resetSingleEvent(param1, param2);
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
        }

        return output;

    }

    /**
     * Return the debug event data for the get rest-api call
     *
     * URL must be in one of the following forms for retrieving a list of all
     * registered events:
     * "http://{controller-hostname}:8080/wm/debugevent/
     *
     * URL must be in one of the following forms for retrieving event data:
     * "http://{controller-hostname}:8080/wm/debugevent/{param1}
     * "http://{controller-hostname}:8080/wm/debugevent/{param1}/{param2}
     *
     *  where {param1} must be one of (no quotes):
     *       null                   if nothing is given then by default the list
     *                              of all events is returned, not their histories.
     *       "all"                  returns value/info on all active events.
     *       "{moduleName}"         returns value/info on events for the specified module
     *                              depending on the value of param2
     *  and   {param2} must be one of (no quotes):
     *       "{eventName}"          returns value/info for specific event if it is active.
     *
     *  {param2} is optional; in which case the event history for all the events registered
     *  for that moduleName will be returned.
     *
     */
    @Get("json")
    public DebugEventInfoOutput handleEventInfoQuery() {
        Option choice = Option.ERROR_BAD_PARAM;
        DebugEventInfoOutput output;

        String param1 = (String)getRequestAttributes().get("param1");
        String param2 = (String)getRequestAttributes().get("param2");

        if (param1 == null && param2 == null) {
            output = new DebugEventInfoOutput(true);
            return listEvents(output);
        }
        output = new DebugEventInfoOutput(false);

        if (param1 == null && param2 != null) {
            choice = Option.ERROR_BAD_PARAM;
        } else if (param1.equals("all")) {
            choice = Option.ALL;
        } else if (param2 == null) {
            boolean isRegistered = debugEvent.containsModuleName(param1);
            if (isRegistered) {
                choice = Option.ONE_MODULE;
            } else {
                choice = Option.ERROR_BAD_MODULE_NAME;
            }
        } else {
            // differentiate between disabled and non-existing events
            boolean isRegistered = debugEvent.containsModuleEventName(param1, param2);
            if (isRegistered) {
                choice = Option.ONE_MODULE_EVENT;
            } else {
                choice = Option.ERROR_BAD_MODULE_EVENT_NAME;
            }
        }

        switch (choice) {
            case ALL:
                populateEvents(debugEvent.getAllEventHistory(), output);
                break;
            case ONE_MODULE:
                populateEvents(debugEvent.getModuleEventHistory(param1), output);
                break;
            case ONE_MODULE_EVENT:
                populateSingleEvent(debugEvent.getSingleEventHistory(param1, param2),
                                    output);
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

    private DebugEventInfoOutput listEvents(DebugEventInfoOutput output) {
        output.eventList = debugEvent.getEventList();
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
