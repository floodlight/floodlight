package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchDisconnectedException extends Exception {
    private static final long serialVersionUID = 1L;

    private final DatapathId id;

    public SwitchDisconnectedException(DatapathId id) {
        super(genMessage(id));
        this.id = id;
    }

    private static String genMessage(DatapathId id) {
        return String.format("Switch %s disconnected", id);
    }

    public DatapathId getId() {
        return id;
    }

}
