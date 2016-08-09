/**
 * Created by geddingsbarrineau on 4/1/16.
 */
package net.floodlightcontroller.topology;

import net.floodlightcontroller.routing.BroadcastTree;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashSet;
import java.util.Set;

public class Archipelago {
    private DatapathId id; // the lowest id of the nodes
    private final Set<Cluster> clusters;
    private BroadcastTree destinationRootedFullTree;

    public Archipelago() {
        id = DatapathId.NONE;
        clusters = new HashSet<Cluster>();
        destinationRootedFullTree = null;
    }

    public DatapathId getId() {
        return id;
    }

    protected Set<Cluster> getClusters() {
        return clusters;
    }

    Archipelago add(Cluster c) {
        if (clusters.add(c)) {
            if (id.equals(DatapathId.NONE) || c.getId().compareTo(id) < 0) {
                id = c.getId();
            }
        }
        return this;
    }

    boolean isMember(Cluster c) {
        return clusters.contains(c);
    }

    boolean isMember(DatapathId id) {
        for (Cluster c : clusters) {
            if(c.getNodes().contains(id)) return true;
        }
        return false;
    }

    void merge(Archipelago a) {
        clusters.addAll(a.getClusters());
        if (id.equals(DatapathId.NONE) || !a.getId().equals(DatapathId.NONE) || a.getId().compareTo(id) < 0) {
            id = a.getId();
        }
    }
    
    Set<DatapathId> getSwitches() {
        Set<DatapathId> allSwitches = new HashSet<DatapathId>();
        for (Cluster c : clusters) {
            for (DatapathId d : c.getNodes()) {
                allSwitches.add(d);
            }
        }
        return allSwitches;
    }

    BroadcastTree getBroadcastTree() {
        return destinationRootedFullTree;
    }

    void setBroadcastTree(BroadcastTree bt) {
        destinationRootedFullTree = bt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Archipelago that = (Archipelago) o;

        if (!id.equals(that.id)) return false;
        return clusters.equals(that.clusters);

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + clusters.hashCode();
        return result;
    }

    public String toString() {
        return "[Archipelago id=" + id.toString() + ", " + clusters.toString() + "]";
    }
}
