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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * Returns a JSON map of <ClusterId, List<SwitchDpids>>
 */
public class SwitchClustersResource extends ServerResource {
    @Get("json")
    public Map<String, List<String>> retrieve() {
        IFloodlightProvider floodlightProvider = (IFloodlightProvider)getApplication();
        Map<String, List<String>> switchClusterMap = new HashMap<String, List<String>>();
        for (Entry<Long, IOFSwitch> entry : floodlightProvider.getSwitches().entrySet()) {
            Long clusterDpid = entry.getValue().getSwitchClusterId();
            List<String> switchesInCluster = switchClusterMap.get(HexString.toHexString(clusterDpid));
            if (switchesInCluster != null) {
                switchesInCluster.add(HexString.toHexString(entry.getKey()));              
            } else {
                List<String> l = new ArrayList<String>();
                l.add(HexString.toHexString(entry.getKey()));
                switchClusterMap.put(HexString.toHexString(clusterDpid), l);
            }
        }
        return switchClusterMap;
    }
}
