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

import org.restlet.Context;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;
import net.floodlightcontroller.routing.web.PathMetricsResource;
import net.floodlightcontroller.routing.web.PathResource;
import net.floodlightcontroller.routing.web.PathsResource;

public class RoutingWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/path/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/json", PathResource.class);
        router.attach("/paths/{src-dpid}/{dst-dpid}/{num-paths}/json", PathsResource.class);
        router.attach("/paths/fast/{src-dpid}/{dst-dpid}/{num-paths}/json", PathsResource.class);
        router.attach("/paths/slow/{src-dpid}/{dst-dpid}/{num-paths}/json", PathsResource.class);
        router.attach("/metric/json", PathMetricsResource.class);
        router.attach("/paths/force-recompute/json", ForceRecomputeResource.class);
        router.attach("/paths/max-fast-paths/json", MaxFastPathsResource.class);
        return router;
    }

    /**
     * Set the base path for routing service
     */
    @Override
    public String basePath() {
        return "/wm/routing";
    }
}
