package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFPortDesc;


/**
 * Describes a change of an open flow port
 */
public class PortChangeEvent {
    public final OFPortDesc port;
    public final PortChangeType type;
    /**
     * @param port
     * @param type
     */
    public PortChangeEvent(OFPortDesc port,
                           PortChangeType type) {
        this.port = port;
        this.type = type;
    }
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((port == null) ? 0 : port.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PortChangeEvent other = (PortChangeEvent) obj;
        if (port == null) {
            if (other.port != null) return false;
        } else if (!port.equals(other.port)) return false;
        if (type != other.type) return false;
        return true;
    }
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "[" + type + " " + String.format("%s (%d)", port.getName(), port.getPortNo().getPortNumber()) + "]";
    }
}