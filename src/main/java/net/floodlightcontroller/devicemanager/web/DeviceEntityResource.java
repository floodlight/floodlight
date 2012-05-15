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

import java.util.Iterator;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.Entity;

import org.restlet.resource.Get;

/**
 * Resource for querying and displaying internal debug information on
 * network entities associated with devices
 */
public class DeviceEntityResource extends AbstractDeviceResource {
    @Get("json")
    public Iterator<Entity[]> getDeviceEntities() {
        final Iterator<? extends IDevice> devices = super.getDevices();
        return new Iterator<Entity[]>() {

            @Override
            public boolean hasNext() {
                return devices.hasNext();
            }

            @Override
            public Entity[] next() {
                Device d = (Device)devices.next();
                return d.getEntities();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
