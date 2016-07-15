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

package net.floodlightcontroller.routing.web;

import net.floodlightcontroller.core.types.JsonObjectWrapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.python.google.common.collect.ImmutableList;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class PathResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(PathResource.class);

    @Get("json")
    public Object retrieve() {
        IRoutingService routing = 
                (IRoutingService)getContext().getAttributes().
                    get(IRoutingService.class.getCanonicalName());
        
        DatapathId srcDpid;
        DatapathId dstDpid;
        try {
            srcDpid = DatapathId.of((String) getRequestAttributes().get("src-dpid"));
            dstDpid = DatapathId.of((String) getRequestAttributes().get("dst-dpid"));
        } catch (Exception e) {
            return ImmutableMap.of("ERROR", "Could not parse source or destination DPID from URI");
        }     
        log.debug("Asking for paths from {} to {}", srcDpid, dstDpid);
        
        OFPort srcPort;
        OFPort dstPort;
        try {
            srcPort = OFPort.of(Integer.parseInt((String) getRequestAttributes().get("src-port")));
            dstPort = OFPort.of(Integer.parseInt((String) getRequestAttributes().get("dst-port")));
        } catch (Exception e) {
            return ImmutableMap.of("ERROR", "Could not parse source or destination port from URI");
        }     
        log.debug("Asking for paths from {} to {}", srcPort, dstPort);
        
        Path result = routing.getPath(srcDpid, srcPort, dstDpid, dstPort);
        
        if (result != null) {
            return JsonObjectWrapper.of(routing.getPath(srcDpid, srcPort, dstDpid, dstPort).getPath());
        }
        else {
            log.debug("ERROR! no path found");
            return JsonObjectWrapper.of(ImmutableList.of());
        }
    }
}
