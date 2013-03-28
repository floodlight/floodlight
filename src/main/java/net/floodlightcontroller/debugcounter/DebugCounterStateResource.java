package net.floodlightcontroller.debugcounter;

/**
 * Enable/disable on-demand counters
 *
 * URI must be in one of the following forms: " +
 * "http://<controller-hostname>:8080/wm/core/debugcounter/<moduleCounterName>/<state>/json
 *
 *  where <state> must be one of (no quotes)
 *       enable          enables counter <moduleCounterName> if it is an on-demand counter
 *       disable         disables counter <moduleCounterName> if it is an on-demand counter
 *
 * @author Saurav
 */
public class DebugCounterStateResource extends DebugCounterResourceBase {

}
