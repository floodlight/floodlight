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

package net.floodlightcontroller.restserver;

import org.restlet.Context;
import org.restlet.Restlet;

/**
 * Register a set of REST resources with the central controller
 * @author readams
 */
public interface RestletRoutable {
    /**
     * Get the restlet that will map to the resources
     * @param context the context for constructing the restlet
     * @return the restlet
     */
    Restlet getRestlet(Context context);
    
    /**
     * Get the base path URL where the router should be registered
     * @return the base path URL where the router should be registered
     */
    String basePath();
}
