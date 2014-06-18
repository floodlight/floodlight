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

import java.util.HashMap;

import org.openflow.util.HexString;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.RoleInfo;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchRoleResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(SwitchRoleResource.class);

    @Get("json")
    public Object getRole() {
        IFloodlightProviderService floodlightProvider =
                (IFloodlightProviderService)getContext().getAttributes().
                    get(IFloodlightProviderService.class.getCanonicalName());

        String switchId = (String) getRequestAttributes().get("switchId");

        RoleInfo roleInfo;

        if (switchId.equalsIgnoreCase("all")) {
            HashMap<String,RoleInfo> model = new HashMap<String,RoleInfo>();
            for (IOFSwitch sw: floodlightProvider.getAllSwitchMap().values()) {
                switchId = sw.getStringId();
                roleInfo = new RoleInfo(sw.getHARole(), null);
                model.put(switchId, roleInfo);
            }
            return model;
        }

        Long dpid = HexString.toLong(switchId);
        IOFSwitch sw = floodlightProvider.getSwitch(dpid);
        if (sw == null)
            return null;
        roleInfo = new RoleInfo(sw.getHARole(), null);
        return roleInfo;
    }
}
