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


import net.floodlightcontroller.staticflowentry.StaticFlowEntries;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;

/**
 * Pushes a static flow entry to the storage source
 * @author alexreimers
 *
 */
public class StaticFlowEntryPusherResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryPusherResource.class);
    
    @Post
    public void store(String fmJson) {
        IStorageSourceService storageSource =
                (IStorageSourceService)getContext().getAttributes().
                    get(IStorageSourceService.class.getCanonicalName());
        
        Map<String, Object> rowValues;
        try {
            rowValues = StaticFlowEntries.jsonToStorageEntry(fmJson);
            storageSource.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, rowValues);
        } catch (IOException e) {
            log.error("Error parsing push flow mod request: " + fmJson, e);
            e.printStackTrace();
        }
    }
    
    @Delete
    public void del(String fmJson) {
        IStorageSourceService storageSource =
                (IStorageSourceService)getContext().getAttributes().
                    get(IStorageSourceService.class.getCanonicalName());
        
        try {
            String fmName = StaticFlowEntries.getEntryNameFromJson(fmJson);
            storageSource.deleteRow(StaticFlowEntryPusher.TABLE_NAME, fmName);
        } catch (IOException e) {
            log.error("Error deleting flow mod request: " + fmJson, e);
            e.printStackTrace();
        }
    }
}
