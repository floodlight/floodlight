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

package net.floodlightcontroller.core.web;

import java.util.Set;

import net.floodlightcontroller.storage.IStorageSourceService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class StorageSourceTablesResource extends ServerResource {
    @Get("json")
    public Set<String> retrieve() {
        IStorageSourceService storageSource = (IStorageSourceService)getContext().
                getAttributes().get(IStorageSourceService.class.getCanonicalName());
        Set<String> allTableNames = storageSource.getAllTableNames();
        return allTableNames;
    }
}
