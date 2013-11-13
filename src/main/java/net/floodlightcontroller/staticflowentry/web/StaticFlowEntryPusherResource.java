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
import java.util.Map;

import org.restlet.resource.Delete;
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
 * Pushes a static flow entry to the storage source
 * @author alexreimers
 *
 */
@LogMessageCategory("Static Flow Pusher")
public class StaticFlowEntryPusherResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryPusherResource.class);

    /**
     * Checks to see if the user matches IP information without
     * checking for the correct ether-type (2048).
     * @param rows The Map that is a string representation of
     * the static flow.
     * @reutrn True if they checked the ether-type, false otherwise
     */
    private boolean checkMatchIp(Map<String, Object> rows) {
        boolean matchEther = false;
        String val = (String) rows.get(StaticFlowEntryPusher.COLUMN_DL_TYPE);
        if (val != null) {
            int type = 0;
            // check both hex and decimal
            if (val.startsWith("0x")) {
                type = Integer.parseInt(val.substring(2), 16);
            } else {
                try {
                    type = Integer.parseInt(val);
                } catch (NumberFormatException e) { /* fail silently */}
            }
            if (type == 2048) matchEther = true;
        }

        if ((rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_DST) ||
                rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_SRC) ||
                rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_PROTO) ||
                rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_TOS)) &&
                (matchEther == false))
            return false;

        return true;
    }

    /**
     * Takes a Static Flow Pusher string in JSON format and parses it into
     * our database schema then pushes it to the database.
     * @param fmJson The Static Flow Pusher entry in JSON format.
     * @return A string status message
     */
    @Post
    @LogMessageDoc(level="ERROR",
        message="Error parsing push flow mod request: {request}",
        explanation="An invalid request was sent to static flow pusher",
        recommendation="Fix the format of the static flow mod request")
    public String store(String fmJson) {
        IStorageSourceService storageSource =
                (IStorageSourceService)getContext().getAttributes().
                    get(IStorageSourceService.class.getCanonicalName());

        Map<String, Object> rowValues;
        try {
            rowValues = StaticFlowEntries.jsonToStorageEntry(fmJson);
            String status = null;
            if (!checkMatchIp(rowValues)) {
                status = "Warning! Pushing a static flow entry that matches IP " +
                        "fields without matching for IP payload (ether-type 2048) will cause " +
                        "the switch to wildcard higher level fields.";
                log.error(status);
            } else {
                status = "Entry pushed";
            }
            storageSource.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, rowValues);
            return ("{\"status\" : \"" + status + "\"}");
        } catch (IOException e) {
            log.error("Error parsing push flow mod request: " + fmJson, e);
            return "{\"status\" : \"Error! Could not parse flod mod, see log for details.\"}";
        }
    }

    @Delete
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
