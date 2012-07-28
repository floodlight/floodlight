package net.floodlightcontroller.linkdiscovery.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class LinksResource extends ServerResource {

    public class LinkWithType {
        long src;
        short srcPort;
        long dst;
        short dstPort;
        String type;

        public LinkWithType(Link link, String type) {
            this.src = link.getSrc();
            this.srcPort = link.getSrcPort();
            this.dst = link.getDst();
            this.dstPort = link.getDstPort();
            this.type = type;
        }
    };

    @Get("json")
    public Set<LinkWithType> retrieve() {
        String str;

        ILinkDiscoveryService ld = (ILinkDiscoveryService)getContext().getAttributes().
                get(ILinkDiscoveryService.class.getCanonicalName());
        Map<Link, LinkInfo> links = new HashMap<Link, LinkInfo>();
        Set<LinkWithType> returnLinkSet = new HashSet<LinkWithType>();

        if (ld != null) {
            links.putAll(ld.getLinks());
            for (Link link: links.keySet()) {
                LinkInfo info = links.get(link);
                LinkType type = ld.getLinkType(info);

                if (type == LinkType.DIRECT_LINK) 
                    str = "internal";
                else if (type == LinkType.MULTIHOP_LINK)
                    str = "external";
                else if (type == LinkType.TUNNEL)
                    str = "tunnel";
                else str = "invalid";

                LinkWithType lwt = new LinkWithType(link, str);
                returnLinkSet.add(lwt);
            }
        }
        return returnLinkSet;
    }
}
