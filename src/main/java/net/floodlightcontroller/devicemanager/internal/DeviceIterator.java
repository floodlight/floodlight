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

package net.floodlightcontroller.devicemanager.internal;

import java.util.Arrays;
import java.util.Iterator;

import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.util.FilterIterator;

/**
 * An iterator for handling device queries
 */
public class DeviceIterator extends FilterIterator<Device> {
    private IEntityClass[] entityClasses;
    
    private Long macAddress;
    private Short vlan;
    private Integer ipv4Address; 
    private Long switchDPID;
    private Integer switchPort;
    
    /**
     * Construct a new device iterator over the key fields
     * @param subIterator an iterator over the full data structure to scan
     * @param entityClasses the entity classes to search for
     * @param macAddress The MAC address
     * @param vlan the VLAN
     * @param ipv4Address the ipv4 address
     * @param switchDPID the switch DPID
     * @param switchPort the switch port
     */
    public DeviceIterator(Iterator<Device> subIterator, 
                          IEntityClass[] entityClasses,
                          Long macAddress,
                          Short vlan, 
                          Integer ipv4Address, 
                          Long switchDPID,
                          Integer switchPort) {
        super(subIterator);
        this.entityClasses = entityClasses;
        this.subIterator = subIterator;
        this.macAddress = macAddress;
        this.vlan = vlan;
        this.ipv4Address = ipv4Address;
        this.switchDPID = switchDPID;
        this.switchPort = switchPort;
    }

    @Override
    protected boolean matches(Device value) {
        boolean match;
        if (entityClasses != null) {
            IEntityClass clazz = value.getEntityClass();
            if (clazz == null) return false;

            match = false;
            for (IEntityClass entityClass : entityClasses) {
                if (clazz.equals(entityClass)) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;                
        }
        if (macAddress != null) {
            if (macAddress.longValue() != value.getMACAddress())
                return false;
        }
        if (vlan != null) {
            Short[] vlans = value.getVlanId();
            if (Arrays.binarySearch(vlans, vlan) < 0) 
                return false;
        }
        if (ipv4Address != null) {
            Integer[] ipv4Addresses = value.getIPv4Addresses();
            if (Arrays.binarySearch(ipv4Addresses, ipv4Address) < 0) 
                return false;
        }
        if (switchDPID != null || switchPort != null) {
            SwitchPort[] sps = value.getAttachmentPoints();
            if (sps == null) return false;
            
            match = false;
            for (SwitchPort sp : sps) {
                if (switchDPID != null) {
                    if (switchDPID.longValue() != sp.getSwitchDPID())
                        return false;
                }
                if (switchPort != null) {
                    if (switchPort.intValue() != sp.getPort())
                        return false;
                }
                match = true;
                break;
            }
            if (!match) return false;
        }
        return true;
    }
}
