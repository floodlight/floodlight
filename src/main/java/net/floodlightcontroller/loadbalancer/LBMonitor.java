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

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

public class LBMonitor {
    protected String id;
    protected String name;
    protected short type;
    protected short delay;
    protected short timeout;
    protected short attemptsBeforeDeactivation;
    
    protected String netId;
    protected int address;
    protected byte protocol;
    protected short port;

    //protected path??
    
    protected short adminState;
    protected short status;

    public LBMonitor() {
        id = null;
        name = null;
        type = 0;
        delay = 0;
        timeout = 0;
        attemptsBeforeDeactivation = 0;
        netId = null;
        address = 0;
        protocol = 0;
        port = 0;
        adminState = 0;
        status = 0;
        
    }
    
}
