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

package net.floodlightcontroller.virtualnetwork;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.util.MACAddress;

/**
 * Data structure for storing and outputing information of a virtual network created
 * by VirtualNetworkFilter
 * 
 * @author KC Wang
 */

@JsonSerialize(using=VirtualNetworkSerializer.class)
public class VirtualNetwork{
    protected String name; // network name
    protected String guid; // network id
    protected String gateway; // network gateway
	protected Map<String,MACAddress> portToMac; //port's logical namd and the host's mac address connected
    /**
     * Constructor requires network name and id
     * @param name: network name
     * @param guid: network id 
     */
    public VirtualNetwork(String name, String guid) {
        this.name = name;
        this.guid = guid;
        this.gateway = null;
		this.portToMac = new ConcurrentHashMap<String,MACAddress>();
        return;        
    }

    /**
     * Sets network name
     * @param gateway: IP address as String
     */
    public void setName(String name){
        this.name = name;
        return;                
    }
    
    /**
     * Sets network gateway IP address
     * @param gateway: IP address as String
     */
    public void setGateway(String gateway){
        this.gateway = gateway;
        return;                
    }
    
    /**
     * Adds a host to this network record
     * @param host: MAC address as MACAddress
     */
    public void addHost(String port,MACAddress host){
        this.portToMac.put(port,host); // ignore old mapping
        return;        
    }
    
    /**
     * Removes a host from this network record
     * @param host: MAC address as MACAddress
     * @return boolean: true: removed, false: host not found
     */
    public boolean removeHost(MACAddress host){
		for (Entry<String,MACAddress> entry : this.portToMac.entrySet()){
			if(entry.getValue().equals(host)){
				this.portToMac.remove(entry.getKey());
				return true;
			}
		}
		return false;
    }
    
    /**
     * Removes all hosts from this network record
     */
    public void clearHosts(){
		this.portToMac.clear();
    }
}
