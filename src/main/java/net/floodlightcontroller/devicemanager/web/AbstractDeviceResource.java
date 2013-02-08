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
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.FilterIterator;

import org.openflow.util.HexString;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.ServerResource;

/**
 * Resource for querying and displaying devices that exist in the system
 */
public abstract class AbstractDeviceResource extends ServerResource {
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
    
    public Iterator<? extends IDevice> getDevices() {
        IDeviceService deviceManager = 
                (IDeviceService)getContext().getAttributes().
                    get(IDeviceService.class.getCanonicalName());  
                
        Long macAddress = null;
        Short vlan = null;
        Integer ipv4Address = null;
        Long switchDPID = null;
        Integer switchPort = null;
        
        Form form = getQuery();
        String macAddrStr = form.getFirstValue("mac", true);
        String vlanStr = form.getFirstValue("vlan", true);
        String ipv4Str = form.getFirstValue("ipv4", true);
        String dpid = form.getFirstValue("dpid", true);
        String port = form.getFirstValue("port", true);
        
        if (macAddrStr != null) {
            try {
                macAddress = HexString.toLong(macAddrStr);
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, MAC_ERROR);
                return null;
            }
        }
        if (vlanStr != null) {
            try {
                vlan = Short.parseShort(vlanStr);
                if (vlan > 4095 || vlan < 0) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, VLAN_ERROR);
                    return null;
                }
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, VLAN_ERROR);
                return null;
            }
        }
        if (ipv4Str != null) {
            try {
                ipv4Address = IPv4.toIPv4Address(ipv4Str);
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, IPV4_ERROR);
                return null;
            }
        }
        if (dpid != null) {
            try {
                switchDPID = HexString.toLong(dpid);
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, DPID_ERROR);
                return null;
            }
        }
        if (port != null) {
            try {
                switchPort = Integer.parseInt(port);
                if (switchPort < 0) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, PORT_ERROR);
                    return null;
                }
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, PORT_ERROR);
                return null;
            }
        }
        
        @SuppressWarnings("unchecked")
        Iterator<Device> diter = (Iterator<Device>)
                deviceManager.queryDevices(macAddress, 
                                           vlan, 
                                           ipv4Address, 
                                           switchDPID, 
                                           switchPort);
        
        final String macStartsWith = 
                form.getFirstValue("mac__startswith", true);
        final String vlanStartsWith = 
                form.getFirstValue("vlan__startswith", true);
        final String ipv4StartsWith = 
                form.getFirstValue("ipv4__startswith", true);
        final String dpidStartsWith = 
                form.getFirstValue("dpid__startswith", true);
        final String portStartsWith = 
                form.getFirstValue("port__startswith", true);
        
        return new FilterIterator<Device>(diter) {
            @Override
            protected boolean matches(Device value) {
                if (macStartsWith != null) {
                    if (!value.getMACAddressString().startsWith(macStartsWith))
                        return false;
                }
                if (vlanStartsWith != null) {
                    boolean match = false;
                    for (Short v : value.getVlanId()) {
                        if (v != null && 
                            v.toString().startsWith(vlanStartsWith)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }
                if (ipv4StartsWith != null) {
                    boolean match = false;
                    for (Integer v : value.getIPv4Addresses()) {
                        String str;
                        if (v != null && 
                            (str = IPv4.fromIPv4Address(v)) != null &&
                            str.startsWith(ipv4StartsWith)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }
                if (dpidStartsWith != null) {
                    boolean match = false;
                    for (SwitchPort v : value.getAttachmentPoints(true)) {
                        String str;
                        if (v != null && 
                            (str = HexString.toHexString(v.getSwitchDPID(), 
                                                         8)) != null &&
                            str.startsWith(dpidStartsWith)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }
                if (portStartsWith != null) {
                    boolean match = false;
                    for (SwitchPort v : value.getAttachmentPoints(true)) {
                        String str;
                        if (v != null && 
                            (str = Integer.toString(v.getPort())) != null &&
                            str.startsWith(portStartsWith)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }
                return true;
            }
        };
    }
}
