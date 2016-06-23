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

package net.floodlightcontroller.staticentry.web;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * As Floodlight has evolved, the purpose and capabilities 
 * of the original Static Flow Entry Pusher (SFEP) have changed.
 * First, many simply referred to the SFEP as Static Flow
 * Pusher (SFP), which resulted in incorrect API usage. So, we
 * shortened the API. Now, the SFP/SFEP can do more than flows.
 * It can also push groups and might also be able to push meters
 * and other OpenFlow table entries in the future. Thus, the name
 * SFP is misleading and credits the SFP will less than it's
 * actually capable of accomplishing. So, the name now is changing
 * to an even broader Static Entry Pusher (SEP), where "entry" is
 * vague enough to encompasses flows, groups, and other potential
 * types.
 * 
 * One thing that hasn't been addressed is that the SEP is also
 * capable of pushing non-static entries. (It relies on entry
 * removal messages being sent from the switch to the controller
 * in order to update its internal store.) Such entries have
 * timeouts configured. IMHO, it's still okay to call the SEP
 * static though, since this feature isn't used very often at all.
 */
public class StaticFlowWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/json", StaticEntryPusherResource.class); /* backwards compatibility w/ v1.0-v1.2 (v1.3?) */
        router.attach("/clear/{switch}/json", ClearStaticEntriesResource.class);
        router.attach("/list/{switch}/json", ListStaticEntriesResource.class);
        router.attach("/usage/json", StaticEntryUsageResource.class);
        return router;
    }

    /**
     * Set the base path for the SFP
     */
    @Override
    public String basePath() {
        return "/wm/staticflowpusher";
    }
}
