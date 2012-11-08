/**
*    Copyright 2011, Big Switch Networks, Inc. 
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

package net.floodlightcontroller.routing;

import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.FloodlightContextStore;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;

public interface IRoutingDecision {
    public enum RoutingAction {
        /*
         * NONE:                    NO-OP, continue with the packet processing chain
         * DROP:                    Drop this packet and this flow
         * FORWARD:                 Forward this packet, and this flow, to the first (and only device) in getDestinationDevices(),
         *                          if the destination is not known at this time, initiate a discovery action for it (e.g. ARP)
         * FORWARD_OR_FLOOD:        Forward this packet, and this flow, to the first (and only device) in getDestinationDevices(),
         *                          if the destination is not known at this time, flood this packet on the source switch
         * MULTICAST:               Multicast this packet to all the interfaces and devices attached
         */
        NONE, DROP, FORWARD, FORWARD_OR_FLOOD, MULTICAST
    }
    
    public static final FloodlightContextStore<IRoutingDecision> rtStore =
        new FloodlightContextStore<IRoutingDecision>();
    public static final String CONTEXT_DECISION = 
            "net.floodlightcontroller.routing.decision";

    public void addToContext(FloodlightContext cntx);
    public RoutingAction getRoutingAction();
    public void setRoutingAction(RoutingAction action);
    public SwitchPort getSourcePort();
    public IDevice getSourceDevice();
    public List<IDevice> getDestinationDevices();
    public void addDestinationDevice(IDevice d);
    public List<SwitchPort> getMulticastInterfaces();
    public void setMulticastInterfaces(List<SwitchPort> lspt);
    public Integer getWildcards();
    public void setWildcards(Integer wildcards);
}
