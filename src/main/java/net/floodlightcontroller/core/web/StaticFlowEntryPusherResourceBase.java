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

package net.floodlightcontroller.core.web;

import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import net.floodlightcontroller.staticflowentry.*;

public class StaticFlowEntryPusherResourceBase extends ServerResource {
    protected StaticFlowEntryPusher staticFlowEntryPusher;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        staticFlowEntryPusher = 
             (StaticFlowEntryPusher)getContext().getAttributes().get("staticFlowEntryPusher");
    }
}