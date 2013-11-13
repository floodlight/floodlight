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

import java.util.List;

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(RouteResource.class);

    @Get("json")
    public List<NodePortTuple> retrieve() {
        IRoutingService routing = 
                (IRoutingService)getContext().getAttributes().
                    get(IRoutingService.class.getCanonicalName());
        
        String srcDpid = (String) getRequestAttributes().get("src-dpid");
        String srcPort = (String) getRequestAttributes().get("src-port");
        String dstDpid = (String) getRequestAttributes().get("dst-dpid");
        String dstPort = (String) getRequestAttributes().get("dst-port");

        log.debug( srcDpid + "--" + srcPort + "--" + dstDpid + "--" + dstPort);

        long longSrcDpid = HexString.toLong(srcDpid);
        short shortSrcPort = Short.parseShort(srcPort);
        long longDstDpid = HexString.toLong(dstDpid);
        short shortDstPort = Short.parseShort(dstPort);
        
        Route result = routing.getRoute(longSrcDpid, shortSrcPort, longDstDpid, shortDstPort, 0);
        
        if (result!=null) {
            return routing.getRoute(longSrcDpid, shortSrcPort, longDstDpid, shortDstPort, 0).getPath();
        }
        else {
            log.debug("ERROR! no route found");
            return null;
        }
    }
}
