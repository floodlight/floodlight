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

public class MaxFastPathsResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(MaxFastPathsResource.class);

    private static String maxPathsFromJson(String json) {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        String max = "";
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

                if (n.equalsIgnoreCase("max_fast_paths")) {
                    max = jp.getText();
                }
            }
        } catch (IOException e) {
            log.error("Unable to parse JSON string: {}", e);
        }
        return max.trim().toLowerCase();
    }

    @Put
    @Post
    public Map<String, String> changeMaxPathsToCompute(String json) {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                get(IRoutingService.class.getCanonicalName());

        int max = 0;
        
        try {
            max = Integer.parseInt(maxPathsFromJson(json));
            if (max < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            log.error("Could not parse max_fast_paths {}", max);
            return Collections.singletonMap("error", "invalid max_fast_paths: " + max);
        }

        log.debug("Setting max_fast_paths to {}", max);
        routing.setMaxPathsToCompute(max);
        return ImmutableMap.of("max_fast_paths", Integer.toString(routing.getMaxPathsToCompute()));
    }

    @Get
    public Map<String, String> getMaxPaths() {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                get(IRoutingService.class.getCanonicalName());
        return ImmutableMap.of("max_fast_paths", Integer.toString(routing.getMaxPathsToCompute()));
    }
}