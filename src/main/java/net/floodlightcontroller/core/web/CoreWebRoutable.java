/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.core.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * Creates a router to handle all the core web URIs
 * @author readams
 */
public class CoreWebRoutable implements RestletRoutable {
    @Override
    public String basePath() {
        return "/wm/core";
    }

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/switch/all/{statType}/json", AllSwitchStatisticsResource.class);
        router.attach("/switch/{switchId}/{statType}/json", SwitchStatisticsResource.class);
        router.attach("/controller/switches/json", ControllerSwitchesResource.class);
        router.attach("/counter/{counterTitle}/json", CounterResource.class);
        router.attach("/counter/{switchId}/{counterName}/json", SwitchCounterResource.class);
        router.attach("/counter/categories/{switchId}/{counterName}/{layer}/json", SwitchCounterCategoriesResource.class);
        router.attach("/memory/json", ControllerMemoryResource.class);
        router.attach("/packettrace/json", PacketTraceResource.class);
        // Get the last {count} events from the event histories
        router.attach("/event-history/attachment-point/{count}/json",
                EventHistoryAttachmentPointResource.class);
        router.attach("/event-history/packet-in/{count}/json",
                EventHistoryPacketInResource.class);
        router.attach("/event-history/topology-switch/{count}/json",
                EventHistoryTopologySwitchResource.class);
        router.attach("/event-history/topology-link/{count}/json",
                EventHistoryTopologyLinkResource.class);
        router.attach("/event-history/topology-cluster/{count}/json",
                EventHistoryTopologyClusterResource.class);
        router.attach("/storage/tables/json", StorageSourceTablesResource.class);
        return router;
    }
}
