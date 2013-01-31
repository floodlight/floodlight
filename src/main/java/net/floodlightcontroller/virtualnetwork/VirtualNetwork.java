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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import org.codehaus.jackson.map.annotate.JsonSerialize;

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
    protected Collection<MACAddress> hosts; // array of hosts explicitly added to this network

    /**
     * Constructor requires network name and id
     * @param name: network name
     * @param guid: network id 
     */
    public VirtualNetwork(String name, String guid) {
        this.name = name;
        this.guid = guid;
        this.gateway = null;
        this.hosts = new ArrayList<MACAddress>();
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
    public void addHost(MACAddress host){
        this.hosts.add(host);
        return;        
    }
    
    /**
     * Removes a host from this network record
     * @param host: MAC address as MACAddress
     * @return boolean: true: removed, false: host not found
     */
    public boolean removeHost(MACAddress host){
        Iterator<MACAddress> iter = this.hosts.iterator();
        while(iter.hasNext()){
            MACAddress element = iter.next();
            if(element.equals(host) ){
                //assuming MAC address for host is unique
                iter.remove();
                return true;
            }                
        }
        return false;
    }
    
    /**
     * Removes all hosts from this network record
     */
    public void clearHosts(){
        this.hosts.clear();
    }
}