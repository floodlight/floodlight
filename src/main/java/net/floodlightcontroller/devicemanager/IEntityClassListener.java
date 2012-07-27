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

package net.floodlightcontroller.devicemanager;

import java.util.Set;

/**
 * Implementors of this interface can receive updates from DeviceManager about
 * the changes entity Classes.
 *
 * @author Ananth Suryanarayana (Ananth.Suryanarayana@bigswitch.com)
 */
public interface IEntityClassListener {

    /**
     * Process entity classes change event.
     * @param  entityClassNames Set of entity classes changed
     */
    public void entityClassChanged(Set<String> entityClassNames);
}
