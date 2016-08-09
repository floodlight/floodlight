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

package net.floodlightcontroller.topology.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.topology.ITopologyService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * Returns a JSON map of <Archipelago-ID, List<Cluster-IDs>>
 */
public class SwitchArchipelagosResource extends ServerResource {
    @Get("json")
    public Map<String, Map<String, Set<String>>> retrieve() {
        ITopologyService topologyService =
                (ITopologyService) getContext().getAttributes().
                get(ITopologyService.class.getCanonicalName());

        Map<String, Map<String, Set<String>>> switchArchMap = new HashMap<String, Map<String, Set<String>>>();
        for (DatapathId a : topologyService.getArchipelagoIds()) {
            switchArchMap.put(a.toString(), new HashMap<String, Set<String>>());
            for (DatapathId c : topologyService.getClusterIdsInArchipelago(a)) {
                switchArchMap.get(a.toString()).put(c.toString(), new HashSet<String>());
                for (DatapathId s : topologyService.getSwitchesInCluster(c)) {
                    switchArchMap.get(a.toString()).get(c.toString()).add(s.toString());
                }
            }
        }

        return switchArchMap; /* map serialized as object */
    }
}
