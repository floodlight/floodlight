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
import org.python.google.common.collect.ImmutableList;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import java.util.List;

public class PathsResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(PathsResource.class);

    @Get("json")
    public Object retrieve() {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        String url = getRequest().getResourceRef().toString();

        DatapathId srcDpid;
        DatapathId dstDpid;
        try {
            srcDpid = DatapathId.of((String) getRequestAttributes().get("src-dpid"));
            dstDpid = DatapathId.of((String) getRequestAttributes().get("dst-dpid"));
        } catch (Exception e) {
            return ImmutableMap.of("ERROR", "Could not parse source or destination DPID from URI");
        }     
        log.debug("Asking for paths from {} to {}", srcDpid, dstDpid);
        
        Integer numRoutes;
        try {
            numRoutes = Integer.parseInt((String) getRequestAttributes().get("num-paths"));
        } catch (NumberFormatException e) {
            return ImmutableMap.of("ERROR", "Could not parse number of paths from URI");
        }
        log.debug("Asking for {} paths", numRoutes);

        List<Path> results = null;
        try {
            if (url.contains("fast")) {
                results = routing.getPathsFast(srcDpid, dstDpid, numRoutes);
            } else if (url.contains("slow")) {
                results = routing.getPathsSlow(srcDpid, dstDpid, numRoutes);
            } else {
                results = routing.getPathsFast(srcDpid, dstDpid);
            }
        } catch (Exception e) {
            return JsonObjectWrapper.of(ImmutableList.of());
        }

        if (results == null || results.isEmpty()) {
            log.debug("No routes found in request for routes from {} to {}", srcDpid, dstDpid);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Got {} routes from {} to {}", new Object[] { results.size(), srcDpid, dstDpid });
                log.debug("These are the routes ---------------------------");
                log.debug("{}", results);
                log.debug("------------------------------------------------");
            }

            return JsonObjectWrapper.of(results);
        }
        return JsonObjectWrapper.of(ImmutableList.of());
    }
}