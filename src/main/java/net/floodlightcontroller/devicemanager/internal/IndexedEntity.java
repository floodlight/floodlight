package net.floodlightcontroller.devicemanager.internal;

import java.util.Collection;
import net.floodlightcontroller.devicemanager.IEntityClassifier.EntityField;

/**
 * This is a thin wrapper around {@link Entity} that allows overriding
 * the behavior of {@link Object#hashCode()} and {@link Object#equals(Object)}
 * so that the keying behavior in a hash map can be changed dynamically
 * @author readams
 */
public class IndexedEntity {
    protected Collection<EntityField> keyFields;
    protected Entity entity;
    protected int hashCode = 0;
    
    /**
     * Create a new {@link IndexedEntity} for the given {@link Entity} using 
     * the provided key fields.
     * @param keyFields The key fields that will be used for computing
     * {@link IndexedEntity#hashCode()} and {@link IndexedEntity#equals(Object)}
     * @param entity the entity to wrap
     */
    public IndexedEntity(Collection<EntityField> keyFields, Entity entity) {
        super();
        this.keyFields = keyFields;
        this.entity = entity;
    }

    @Override
    public int hashCode() {
        if (hashCode != 0) return hashCode;

        final int prime = 31;
        hashCode = 1;
        for (EntityField f : keyFields) {
            switch (f) {
                case MAC:
                    hashCode = prime * hashCode
                        + ((entity.macAddress == null) 
                            ? 0 
                            : entity.macAddress.hashCode());
                case IP:
                    hashCode = prime * hashCode
                        + ((entity.ipv4Address == null) 
                            ? 0 
                            : entity.ipv4Address.hashCode());
                case SWITCH:
                    hashCode = prime * hashCode
                        + ((entity.switchDPID == null) 
                            ? 0 
                            : entity.switchDPID.hashCode());
                case PORT:
                    hashCode = prime * hashCode
                        + ((entity.switchPort == null) 
                            ? 0 
                            : entity.switchPort.hashCode());
                case VLAN:
                    hashCode = prime * hashCode 
                        + ((entity.vlan == null) 
                            ? 0 
                            : entity.vlan.hashCode());
            }
        }
        return hashCode;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        IndexedEntity other = (IndexedEntity) obj;
        
        for (EntityField f : keyFields) {
            switch (f) {
                case MAC:
                    if (entity.macAddress == null) {
                        if (other.entity.macAddress != null) return false;
                    } else if (!entity.macAddress.
                            equals(other.entity.macAddress)) return false;
                case IP:
                    if (entity.ipv4Address == null) {
                        if (other.entity.ipv4Address != null) return false;
                    } else if (!entity.ipv4Address.
                            equals(other.entity.ipv4Address)) return false;
                case SWITCH:
                    if (entity.switchDPID == null) {
                        if (other.entity.switchDPID != null) return false;
                    } else if (!entity.switchDPID.
                            equals(other.entity.switchDPID)) return false;
                case PORT:
                    if (entity.switchPort == null) {
                        if (other.entity.switchPort != null) return false;
                    } else if (!entity.switchPort.
                            equals(other.entity.switchPort)) return false;
                case VLAN:
                    if (entity.vlan == null) {
                        if (other.entity.vlan != null) return false;
                    } else if (!entity.vlan.
                            equals(other.entity.vlan)) return false;
            }
        }
        
        return true;
    }
    
    
}
