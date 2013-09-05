/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.staticflowentry.web;

import java.io.IOException;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.staticflowentry.StaticFlowEntries;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;

/**
 * Deletes a static flow entry to the storage source
 * @author alexreimers
 * 
 * @author Henrique Rodrigues <hsr@cs.ucsd.edu>
 * 		Contributed with splitting StaticFlowEntryPusherResource into 
 * 		two, to make Floodlight's StaticFlowEntryPusher restlet resource
 * 		REST compliant.
 * 
 */
@LogMessageCategory("Static Flow Pusher Delete Resource")
public class StaticFlowEntryDeleteResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryDeleteResource.class);

    @Post
    @LogMessageDoc(level="ERROR",
        message="Error deleting flow mod request: {request}",
        explanation="An invalid delete request was sent to static flow pusher",
        recommendation="Fix the format of the static flow mod request")
    public String del(String fmJson) {
        IStorageSourceService storageSource =
                (IStorageSourceService)getContext().getAttributes().
                    get(IStorageSourceService.class.getCanonicalName());
        String fmName = null;
        if (fmJson == null) {
            return "{\"status\" : \"Error! No data posted.\"}";
        }
        try {
            fmName = StaticFlowEntries.getEntryNameFromJson(fmJson);
            if (fmName == null) {
                return "{\"status\" : \"Error deleting entry, no name provided\"}";
            }
        } catch (IOException e) {
            log.error("Error deleting flow mod request: " + fmJson, e);
            return "{\"status\" : \"Error deleting entry, see log for details\"}";
        }

        storageSource.deleteRowAsync(StaticFlowEntryPusher.TABLE_NAME, fmName);
        return "{\"status\" : \"Entry " + fmName + " deleted\"}";
    }
}
