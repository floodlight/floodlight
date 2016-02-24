package net.floodlightcontroller.core;

import javax.annotation.Nonnull;

import org.projectfloodlight.openflow.types.OFAuxId;

/**
 * Immutable class for logical OF message category.
 * Applications should use these to define the OF Aux connections
 * that they desire.
 * @author Jason Parraga <Jason.Parraga@bigswitch.com>
 */
public class LogicalOFMessageCategory {

    public static final LogicalOFMessageCategory MAIN =  new LogicalOFMessageCategory("MAIN", OFAuxId.MAIN);

    final private String name;
    final private OFAuxId auxId;


    public LogicalOFMessageCategory(@Nonnull String name, int auxId) {
        this(name, OFAuxId.of(auxId));
    }

    public LogicalOFMessageCategory(@Nonnull String name, OFAuxId auxId) {
        if (name == null)
            throw new NullPointerException("name must not be null");
        this.name = name;
        this.auxId = auxId;
    }

    public OFAuxId getAuxId(){
        return this.auxId;
    }

    public String getName(){
        return this.name;
    }

    @Override
    public String toString(){
        return "LogicalOFMessageCategory [name=" + getName() + " OFAuxId=" + getAuxId() + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((auxId == null) ? 0 : auxId.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        LogicalOFMessageCategory other = (LogicalOFMessageCategory) obj;
        if (auxId == null) {
            if (other.auxId != null) return false;
        } else if (!auxId.equals(other.auxId)) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        return true;
    }
}