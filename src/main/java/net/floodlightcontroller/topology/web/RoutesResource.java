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

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class RoutesResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(RoutesResource.class);

    @Get("json")
    public List<Route> retrieve() {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        String srcDpid = (String) getRequestAttributes().get("src-dpid");
        String dstDpid = (String) getRequestAttributes().get("dst-dpid");
        Integer numRoutes = Integer.parseInt((String) getRequestAttributes().get("num-routes"));

        log.info("REQUEST: {}", getRequest().getRootRef().toString());
        log.info("REQUEST: {}", getRequest().getResourceRef().toString());
        log.info("REQUEST: {}", getContext().getAttributes().get("contextPath").toString());

        log.debug("Asking for routes from {} to {}", srcDpid, dstDpid);
        log.debug("Asking for {} routes", numRoutes);

        DatapathId longSrcDpid = DatapathId.of(srcDpid);
        DatapathId longDstDpid = DatapathId.of(dstDpid);

        List<Route> results = null;
        try {
            results = routing.getRoutes(longSrcDpid, longDstDpid, numRoutes);
        } catch (Exception e) {
            log.warn("{}", e);
            log.warn("EXCEPTION: No routes found in request for routes from {} to {}", srcDpid, dstDpid);
        }

        if (results == null || results.isEmpty()) {
            log.warn("No routes found in request for routes from {} to {}", srcDpid, dstDpid);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Got {} routes from {} to {}", new Object[] { results.size(), srcDpid, dstDpid });
                log.debug("These are the routes ---------------------------");
                log.debug("{}", results);
                log.debug("------------------------------------------------");
            }

            if (results.size() > 0 && results.contains(null)) {
                log.error("Geddings, Junaid, Scott, etc., how is this happening if there should be no routes? I tested b/t 2 non-existing switches and using the same switch as src and dst.");
            } else {
                return results;
            }
        }
        return Collections.emptyList();
    }
}