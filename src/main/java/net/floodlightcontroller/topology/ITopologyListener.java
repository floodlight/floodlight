package net.floodlightcontroller.topology;

public interface ITopologyListener {
    /**
     * Happens when the switch clusters are recomputed
     */
    void topologyChanged();
}
