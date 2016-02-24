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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class EnabledPortsResource extends ServerResource {
    @Get("json")
    public List<NodePortTuple> retrieve() {
        List<NodePortTuple> result = new ArrayList<NodePortTuple>();

        IOFSwitchService switchService =
                (IOFSwitchService) getContext().getAttributes().
                get(IOFSwitchService.class.getCanonicalName());

        ITopologyService topologyService =
                (ITopologyService) getContext().getAttributes().
                get(ITopologyService.class.getCanonicalName());

        if (switchService == null || topologyService == null)
            return result;

        Set<DatapathId> switches = switchService.getAllSwitchDpids();
        if (switches == null) return result;

        for(DatapathId sw: switches) {
            Set<OFPort> ports = topologyService.getPorts(sw);
            if (ports == null) continue;
            for(OFPort p: ports) {
                result.add(new NodePortTuple(sw, p));
            }
        }
        return result;
    }
}
