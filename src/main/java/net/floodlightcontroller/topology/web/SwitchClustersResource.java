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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.topology.ITopologyService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Form;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * Returns a JSON map of <ClusterId, List<SwitchDpids>>
 */
public class SwitchClustersResource extends ServerResource {
    @Get("json")
    public Map<String, List<String>> retrieve() {
        IOFSwitchService switchService =
                (IOFSwitchService) getContext().getAttributes().
                    get(IOFSwitchService.class.getCanonicalName());
        ITopologyService topologyService =
                (ITopologyService) getContext().getAttributes().
                    get(ITopologyService.class.getCanonicalName());

        Form form = getQuery();
        String queryType = form.getFirstValue("type", true);
        boolean openflowDomain = true;
        if (queryType != null && "l2".equals(queryType)) {
            openflowDomain = false;
        }

        Map<String, List<String>> switchClusterMap = new HashMap<String, List<String>>();
        for (DatapathId dpid: switchService.getAllSwitchDpids()) {
            DatapathId clusterDpid =
                    (openflowDomain
                     ? topologyService.getOpenflowDomainId(dpid)
                     :topologyService.getL2DomainId(dpid));
            List<String> switchesInCluster = switchClusterMap.get(clusterDpid.toString());
            if (switchesInCluster != null) {
                switchesInCluster.add(dpid.toString());
            } else {
                List<String> l = new ArrayList<String>();
                l.add(dpid.toString());
                switchClusterMap.put(clusterDpid.toString(), l);
            }
        }
        return switchClusterMap;
    }
}
