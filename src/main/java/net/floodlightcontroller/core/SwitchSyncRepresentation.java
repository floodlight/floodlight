package net.floodlightcontroller.core;

import java.util.ArrayList;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;

/**
 * Represents a switch in the BigSync store. It works out nicely that we
 * just need to store the FeaturesReply and the DescriptionStatistics in the
 * store.
 * @author gregor
 *
 */
public class SwitchSyncRepresentation {
    // Alaes, these can't be final since we need to de-serialize them from
    // Jackson
    private OFFeaturesReply featuresReply;
    private OFDescriptionStatistics description;

    public SwitchSyncRepresentation() {
        featuresReply = new OFFeaturesReply();
        description = new OFDescriptionStatistics();
    }

    public SwitchSyncRepresentation(IOFSwitch sw) {
        description = sw.getDescriptionStatistics();
        featuresReply = new OFFeaturesReply();
        featuresReply.setDatapathId(sw.getId());
        featuresReply.setBuffers(sw.getBuffers());
        featuresReply.setTables(sw.getTables());
        featuresReply.setCapabilities(sw.getCapabilities());
        featuresReply.setActions(sw.getActions());
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>(sw.getPorts()));
    }

    public OFFeaturesReply getFeaturesReply() {
        return featuresReply;
    }

    public void setFeaturesReply(OFFeaturesReply featuresReply) {
        this.featuresReply = featuresReply;
    }

    public OFDescriptionStatistics getDescription() {
        return description;
    }

    public void setDescription(OFDescriptionStatistics description) {
        this.description = description;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((description == null) ? 0 : description.hashCode());
        result = prime * result
                 + ((featuresReply == null) ? 0 : featuresReply.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SwitchSyncRepresentation other = (SwitchSyncRepresentation) obj;
        if (description == null) {
            if (other.description != null) return false;
        } else if (!description.equals(other.description)) return false;
        if (featuresReply == null) {
            if (other.featuresReply != null) return false;
        } else if (!featuresReply.equals(other.featuresReply)) return false;
        return true;
    }

    @Override
    public String toString() {
        String dpidString;
        if (featuresReply == null)
            dpidString = "?";
        else
            dpidString = HexString.toHexString(featuresReply.getDatapathId());
        return "SwitchSyncRepresentation [DPID=" + dpidString + "]";
    }
}
