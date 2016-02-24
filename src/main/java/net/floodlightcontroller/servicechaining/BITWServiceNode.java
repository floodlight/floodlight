package net.floodlightcontroller.servicechaining;

import net.floodlightcontroller.topology.NodePortTuple;

public class BITWServiceNode extends ServiceNode {
    protected NodePortTuple ingressPort;
    protected NodePortTuple egressPort;

    public BITWServiceNode(String tenant, String name) {
        super(tenant, name, InsertionType.BUMPINTHEWIRE);
    }

    public NodePortTuple getIngressPort() {
        return ingressPort;
    }

    public void setIngressPort(NodePortTuple ingressPort) {
        this.ingressPort = ingressPort;
    }

    public NodePortTuple getEgressPort() {
        return egressPort;
    }

    public void setEgressPort(NodePortTuple egressPort) {
        this.egressPort = egressPort;
    }
}
