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

import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.internal.Controller;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;

public class StaticFlowEntryPusherResource extends StaticFlowEntryPusherResourceBase {
    protected static Logger log = LoggerFactory.getLogger(Controller.class);
          
    @Post
    public void store(String flowmod) {
        ((StaticFlowEntryPusher)this.staticFlowEntryPusher).addEntry(flowmod);    
    }
    
    @Delete
    public void del(String flowmod) {
        ((StaticFlowEntryPusher)this.staticFlowEntryPusher).removeEntry(flowmod);
    }
}
