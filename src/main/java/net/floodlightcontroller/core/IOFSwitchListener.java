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

package net.floodlightcontroller.core;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IOFSwitchListener {

    /**
     * Fired when a switch is connected to the controller, and has sent
     * a features reply.
     * @param sw
     */
    public void addedSwitch(IOFSwitch sw);

    /**
     * Fired when a switch is disconnected from the controller.
     * @param sw
     */
    public void removedSwitch(IOFSwitch sw);
    
    /**
     * Fired when ports on a switch change (any change to the collection
     * of OFPhysicalPorts and/or to a particular port)
     */
    public void switchPortChanged(Long switchId);
    
    /**
     * The name assigned to this listener
     * @return
     */
    public String getName();
}
