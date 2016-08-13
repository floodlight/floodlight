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

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class PathMetricsResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(PathMetricsResource.class);

    private static String metricFromJson(String json) {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        String metric = "";
        try {
            try {
                jp = f.createParser(json);
            } catch (JsonParseException e) {
                throw new IOException(e);
            }

            jp.nextToken();
            if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Expected FIELD_NAME");
                }

                String n = jp.getCurrentName();
                jp.nextToken();
                if (jp.getText().equals("")) {
                    continue;
                }

                if (n.equalsIgnoreCase("metric")) {
                    metric = jp.getText();
                }
            }
        } catch (IOException e) {
            log.error("Unable to parse JSON string: {}", e);
        }
        return metric.trim().toLowerCase();
    }

    @Put
    @Post
    public Map<String, String> changeMetric(String json) {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                get(IRoutingService.class.getCanonicalName());

        String metric = metricFromJson(json);

        PATH_METRIC type;

        if (metric.equals(PATH_METRIC.LATENCY.getMetricName())) {
            type = PATH_METRIC.LATENCY;
        } else if (metric.equals(PATH_METRIC.UTILIZATION.getMetricName())) {
            type = PATH_METRIC.UTILIZATION;
        } else if (metric.equals(PATH_METRIC.HOPCOUNT.getMetricName())) {
            type = PATH_METRIC.HOPCOUNT;
        } else if (metric.equals(PATH_METRIC.HOPCOUNT_AVOID_TUNNELS.getMetricName())) {
            type = PATH_METRIC.HOPCOUNT_AVOID_TUNNELS;
        } else if (metric.equals(PATH_METRIC.LINK_SPEED.getMetricName())) {
            type = PATH_METRIC.LINK_SPEED;
        } else {
            log.error("Invalid input {}", metric);
            return Collections.singletonMap("error", "invalid path metric: " + metric);
        }

        log.debug("Setting path metric to {}", type.getMetricName());
        routing.setPathMetric(type);
        return Collections.singletonMap("metric", type.getMetricName());
    }

    @Get
    public Map<String, String> getMetric() {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                get(IRoutingService.class.getCanonicalName());
        PATH_METRIC metric = routing.getPathMetric();
        return ImmutableMap.of("metric", metric.getMetricName());
    }
}