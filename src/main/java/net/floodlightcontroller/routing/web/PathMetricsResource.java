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

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.IRoutingService.PATH_METRIC;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

public class PathMetricsResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(PathMetricsResource.class);

    @Put
    @Post
    public Map<String, String> changeMetric() {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        String metric = (String) getRequestAttributes().get("metric");
        metric = metric.trim().toLowerCase();

        PATH_METRIC type;

        switch (metric) {
            case "latency":
                type = PATH_METRIC.LATENCY;
                break;
            case "utilization":
                type = PATH_METRIC.UTILIZATION;
                break;
            case "hopcount":
                type = PATH_METRIC.HOPCOUNT;
                break;
            case "hopcount_avoid_tunnels":
                type = PATH_METRIC.HOPCOUNT_AVOID_TUNNELS;
                break;
            case "link_speed":
                type = PATH_METRIC.LINK_SPEED;
                break;
            default:
                log.error("Invalid input {}", metric);
                return Collections.singletonMap("error", "invalid path metric " + metric);
        }

        if (routing.setPathMetric(type) != type) {
            log.error("Failed to set valid path metric {}. Bug?", type.getMetricName());
            return Collections.singletonMap("error", "failed to set valid path metric " + type.getMetricName());
        }

        log.debug("Set path metric to {}", type.getMetricName());
        return Collections.singletonMap("success", "path metric set to " + type.getMetricName());
    }
}