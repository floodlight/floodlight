/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.linkdiscovery.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkDirection;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
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
                LinkType type = ld.getLinkType(link, info);
                if (type == LinkType.DIRECT_LINK || type == LinkType.TUNNEL) {
                    LinkWithType lwt;

                    DatapathId src = link.getSrc();
                    DatapathId dst = link.getDst();
                    OFPort srcPort = link.getSrcPort();
                    OFPort dstPort = link.getDstPort();
                    Link otherLink = new Link(dst, dstPort, src, srcPort);
                    LinkInfo otherInfo = links.get(otherLink);
                    LinkType otherType = null;
                    if (otherInfo != null)
                        otherType = ld.getLinkType(otherLink, otherInfo);
                    if (otherType == LinkType.DIRECT_LINK ||
                            otherType == LinkType.TUNNEL) {
                        // This is a bi-direcitonal link.
                        // It is sufficient to add only one side of it.
                        if ((src.getLong() < dst.getLong()) || (src.getLong() == dst.getLong()
                        		&& srcPort.getPortNumber() < dstPort.getPortNumber())) {
                            lwt = new LinkWithType(link,
                                    type,
                                    LinkDirection.BIDIRECTIONAL);
                            returnLinkSet.add(lwt);
                        }
                    } else {
                        // This is a unidirectional link.
                        lwt = new LinkWithType(link,
                                type,
                                LinkDirection.UNIDIRECTIONAL);
                        returnLinkSet.add(lwt);

                    }
                }
            }
        }
        return returnLinkSet;
    }
}
