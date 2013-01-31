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

package net.floodlightcontroller.loadbalancer;

import java.util.ArrayList;

import org.codehaus.jackson.map.annotate.JsonSerialize;

import net.floodlightcontroller.loadbalancer.LoadBalancer.IPClient;
import net.floodlightcontroller.util.MACAddress;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

@JsonSerialize(using=LBVipSerializer.class)
public class LBVip {
    protected String id;    
    protected String name;
    protected String tenantId;
    protected String netId;
    protected int address;
    protected byte protocol;
    protected short lbMethod;
    protected short port;
    protected ArrayList<String> pools;
    protected boolean sessionPersistence;
    protected int connectionLimit;
    protected short adminState;
    protected short status;
    
    protected MACAddress proxyMac;
    
    public static String LB_PROXY_MAC= "12:34:56:78:90:12";
    
    public LBVip() {
        this.id = String.valueOf((int) (Math.random()*10000));
        this.name = null;
        this.tenantId = null;
        this.netId = null;
        this.address = 0;
        this.protocol = 0;
        this.lbMethod = 0;
        this.port = 0;
        this.pools = new ArrayList<String>();
        this.sessionPersistence = false;
        this.connectionLimit = 0;
        this.address = 0;
        this.status = 0;
        
        this.proxyMac = MACAddress.valueOf(LB_PROXY_MAC);
    }
    
    public String pickPool(IPClient client) {
        // for now, return the first pool; consider different pool choice policy later
        if (pools.size() > 0)
            return pools.get(0);
        else
            return null;
    }

}
