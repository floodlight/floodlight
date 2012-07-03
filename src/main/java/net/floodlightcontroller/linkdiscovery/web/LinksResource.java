package net.floodlightcontroller.linkdiscovery.web;

import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.linkdiscovery.LinkTuple;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class LinksResource extends ServerResource {
    @Get("json")
    public Set<LinkTuple> retrieve() {
        ILinkDiscoveryService topo = (ILinkDiscoveryService)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());
        Set <LinkTuple> links = new HashSet<LinkTuple>();
        if (topo != null) {
            for (Set<LinkTuple> linkSet : topo.getSwitchLinks().values()) {
                for (LinkTuple lt : linkSet) {
                    LinkInfo info = topo.getLinkInfo(lt.getSrc(), true);
                    LinkTuple withType = new LinkTuple(lt.getSrc(), lt.getDst());
                    withType.setType(LinkInfo.getLinkType(lt, info));
                    links.add(withType);
                }
            }
        }
        return links;
    }
}
