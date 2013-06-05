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

package net.floodlightcontroller.storage.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageSourceNotification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageNotifyResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StorageNotifyResource.class);
    
    @Post("json")
    public Map<String,Object> notify(String entity) throws Exception {
        List<StorageSourceNotification> notifications = null;
        ObjectMapper mapper = new ObjectMapper();
        notifications = 
            mapper.readValue(entity, 
                    new TypeReference<List<StorageSourceNotification>>(){});
        
        IStorageSourceService storageSource = 
            (IStorageSourceService)getContext().getAttributes().
                get(IStorageSourceService.class.getCanonicalName());
        storageSource.notifyListeners(notifications);
        
        HashMap<String, Object> model = new HashMap<String,Object>();
        model.put("output", "OK");
        return model;
    }
    
}
