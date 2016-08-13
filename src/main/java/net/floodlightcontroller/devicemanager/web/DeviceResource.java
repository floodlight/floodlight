/**
*    Copyright 2012, Big Switch Networks, Inc.
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

package net.floodlightcontroller.devicemanager.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.floodlightcontroller.devicemanager.IDevice;
import org.restlet.resource.Get;

/**
 * Resource for querying and displaying devices that exist in the system
 */
public class DeviceResource extends AbstractDeviceResource {
    public Iterator<? extends IDevice> getDevices() {
        return super.getDevices();
    }

    @Get("json")
    public Map<String, Iterator<? extends IDevice>> getNamedDeviceList() {
        Map<String, Iterator<? extends IDevice>> result = new HashMap<String, Iterator<? extends IDevice>>();
        result.put("devices", getDevices());
        return result;
    }
}
