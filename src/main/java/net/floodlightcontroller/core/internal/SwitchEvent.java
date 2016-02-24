package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchEvent {
    @EventColumn(name = "dpid", description = EventFieldType.DPID)
    DatapathId dpid;

    @EventColumn(name = "reason", description = EventFieldType.STRING)
    String reason;

    public SwitchEvent(DatapathId dpid, String reason) {
        this.dpid = dpid;
        this.reason = reason;
    }
}