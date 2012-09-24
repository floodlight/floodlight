package net.floodlightcontroller.linkdiscovery.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class LinksResource extends ServerResource {

    @Get("json")
    public Set<LinkWithType> retrieve() {
        ILinkDiscoveryService ld = (ILinkDiscoveryService)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());
        Map<Link, LinkInfo> links = new HashMap<Link, LinkInfo>();
        Set<LinkWithType> returnLinkSet = new HashSet<LinkWithType>();

        if (ld != null) {
            links.putAll(ld.getLinks());
            for (Link link: links.keySet()) {
                LinkInfo info = links.get(link);
                LinkWithType lwt = new LinkWithType(link,
                                                    info.getSrcPortState(),
                                                    info.getDstPortState(),
                                                    ld.getLinkType(link, info));
                returnLinkSet.add(lwt);
            }
        }
        return returnLinkSet;
    }
}
