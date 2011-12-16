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

package net.floodlightcontroller.core.types;

import org.openflow.protocol.OFMessage;

import net.floodlightcontroller.core.IOFSwitch;

public class SwitchMessagePair {
    private final IOFSwitch sw;
    private final OFMessage msg;
    
    public SwitchMessagePair(IOFSwitch sw, OFMessage msg) {
        this.sw = sw;
        this.msg = msg;
    }
    
    public IOFSwitch getSwitch() {
        return this.sw;
    }
    
    public OFMessage getMessage() {
        return this.msg;
    }
}
