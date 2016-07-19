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
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ForceRecomputeResource extends ServerResource {
    private static final Logger log = LoggerFactory.getLogger(ForceRecomputeResource.class);

    @Put
    @Post
    public Map<String, String> forceRecompute() {
        IRoutingService routing =
                (IRoutingService)getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        boolean result = routing.forceRecompute();
        log.debug("Force recompute result {}", result);
        return ImmutableMap.of("result", result ? "paths recomputed" : "error recomputing paths");
    }
}