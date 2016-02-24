package net.floodlightcontroller.debugcounter;

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

/**
 * Resource class for DebugCounter
 * Serves the REST api with dynamic data
 */
@Immutable
public class DebugCounterResource {

    public static final String MODULE_NAME_PREDICATE = "modulename";
    public static final String HIERARCHY_PREDICATE = "hierarchy";
    private static final Joiner joiner = Joiner.on(", ");


    private final Long counterValue;
    private final Long lastModified;
    private final String counterDesc;
    private final String counterHierarchy;
    private final String moduleName;
    private final ImmutableSet<MetaData> metadata;
    private final String metadataString;

    public DebugCounterResource(DebugCounterImpl counter) {
        this.moduleName = counter.getModuleName();
        this.counterHierarchy = counter.getCounterHierarchy();
        this.counterDesc = counter.getDescription();
        this.metadata = counter.getMetaData();
        this.counterValue = counter.getCounterValue();
        this.metadataString = joiner.join(metadata);
        this.lastModified = counter.getLastModified();
    }

    public Long getCounterValue() {
        return counterValue;
    }
    
    public Long getCounterLastModified() {
        return lastModified;
    }

    public String getCounterDesc() {
        return counterDesc;
    }

    public String getCounterHierarchy() {
        return counterHierarchy;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Set<MetaData> getMetadata() {
        return metadata;
    }

    public String getMetadataString() {
        return metadataString;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((counterDesc == null) ? 0 : counterDesc.hashCode());
        result = prime
                 * result
                 + ((counterHierarchy == null) ? 0
                                              : counterHierarchy.hashCode());
        result = prime * result
                 + ((counterValue == null) ? 0 : counterValue.hashCode());
        result = prime * result
                 + ((metadata == null) ? 0 : metadata.hashCode());
        result = prime
                 * result
                 + ((metadataString == null) ? 0 : metadataString.hashCode());
        result = prime * result
                 + ((moduleName == null) ? 0 : moduleName.hashCode());
        return result;
    }

    /**
     * Compare all fields, not only the "key" fields
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        DebugCounterResource other = (DebugCounterResource) obj;
        if (counterDesc == null) {
            if (other.counterDesc != null) return false;
        } else if (!counterDesc.equals(other.counterDesc)) return false;
        if (counterHierarchy == null) {
            if (other.counterHierarchy != null) return false;
        } else if (!counterHierarchy.equals(other.counterHierarchy))
                                                                    return false;
        if (counterValue == null) {
            if (other.counterValue != null) return false;
        } else if (!counterValue.equals(other.counterValue)) return false;
        if (metadata == null) {
            if (other.metadata != null) return false;
        } else if (!metadata.equals(other.metadata)) return false;
        if (metadataString == null) {
            if (other.metadataString != null) return false;
        } else if (!metadataString.equals(other.metadataString))
                                                                return false;
        if (moduleName == null) {
            if (other.moduleName != null) return false;
        } else if (!moduleName.equals(other.moduleName)) return false;
        return true;
    }


}