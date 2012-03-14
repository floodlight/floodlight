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
import org.restlet.resource.Get;

/**
 * Resource for querying and displaying devices that exist in the system
 */
public class DeviceResource extends AbstractDeviceResource {
    public static final String MAC_ERROR = 
            "Invalid MAC address: must be a 48-bit quantity, " + 
            "expressed in hex as AA:BB:CC:DD:EE:FF";
    public static final String VLAN_ERROR = 
            "Invalid VLAN: must be an integer in the range 0-4095";
    public static final String IPV4_ERROR = 
            "Invalid IPv4 address: must be in dotted decimal format, " + 
            "234.0.59.1";
    public static final String DPID_ERROR = 
            "Invalid Switch DPID: must be a 64-bit quantity, expressed in " + 
            "hex as AA:BB:CC:DD:EE:FF:00:11";
    public static final String PORT_ERROR = 
            "Invalid Port: must be a positive integer";
    
    @Get("json")
    public Iterator<? extends IDevice> getDevices() {
        return super.getDevices();
    }
}
