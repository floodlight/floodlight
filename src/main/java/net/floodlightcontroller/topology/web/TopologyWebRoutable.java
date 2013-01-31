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

package net.floodlightcontroller.topology.web;

import org.restlet.Context;
import org.restlet.routing.Router;

import net.floodlightcontroller.linkdiscovery.web.ExternalLinksResource;
import net.floodlightcontroller.linkdiscovery.web.LinksResource;
import net.floodlightcontroller.restserver.RestletRoutable;

public class TopologyWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/links/json", LinksResource.class);
        router.attach("/external-links/json", ExternalLinksResource.class);
        router.attach("/tunnellinks/json", TunnelLinksResource.class);
        router.attach("/switchclusters/json", SwitchClustersResource.class);
        router.attach("/broadcastdomainports/json", BroadcastDomainPortsResource.class);
        router.attach("/enabledports/json", EnabledPortsResource.class);
        router.attach("/blockedports/json", BlockedPortsResource.class);
        router.attach("/route/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/json", RouteResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    @Override
    public String basePath() {
        return "/wm/topology";
    }
}
