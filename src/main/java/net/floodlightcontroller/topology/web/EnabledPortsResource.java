package net.floodlightcontroller.topology.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class EnabledPortsResource extends ServerResource {
    @Get("json")
    public List<NodePortTuple> retrieve() {
        List<NodePortTuple> result = new ArrayList<NodePortTuple>();

        IFloodlightProviderService floodlightProvider =
                (IFloodlightProviderService)getContext().getAttributes().
                get(IFloodlightProviderService.class.getCanonicalName());

        ITopologyService topology= 
                (ITopologyService)getContext().getAttributes().
                get(ITopologyService.class.getCanonicalName());

        if (floodlightProvider == null || topology == null)
            return result;

        Set<Long> switches = floodlightProvider.getSwitches().keySet();
        if (switches == null) return result;

        for(long sw: switches) {
            Set<Short> ports = topology.getPorts(sw);
            if (ports == null) continue;
            for(short p: ports) {
                result.add(new NodePortTuple(sw, p));
            }
        }
        return result;
    }
}
