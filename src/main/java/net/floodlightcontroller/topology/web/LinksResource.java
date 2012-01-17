package net.floodlightcontroller.topology.web;

import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.topology.ITopology;
import net.floodlightcontroller.topology.LinkTuple;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class LinksResource extends ServerResource {
    @Get("json")
    public Set<LinkTuple> retrieve() {
        ITopology topo = (ITopology)getContext().getAttributes().get("topology");
        Set <LinkTuple> links = new HashSet<LinkTuple>();
        if (topo != null) {
            for (Set<LinkTuple> linkSet : topo.getSwitchLinks().values()) {
                links.addAll(linkSet);
            }
        }
        return links;
    }
}
