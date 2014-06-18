/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson & Rob Sherwood, Stanford University
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

package org.openflow.vendor.nicira;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

/**
 * Subclass of OFVendorData representing the vendor data associated with
 * a role request vendor extension.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public class OFRoleRequestVendorData extends OFRoleVendorData {

    protected static Instantiable<OFVendorData> instantiable =
            new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new OFRoleRequestVendorData();
                }
            };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFRoleRequestVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * The data type value for a role request
     */
    public static final int NXT_ROLE_REQUEST = 10;

    /**
     * Construct a role request vendor data with an unspecified role value.
     */
    public OFRoleRequestVendorData() {
        super(NXT_ROLE_REQUEST);
    }
    
    /**
     * Construct a role request vendor data with the specified role value.
     * @param role the role value for the role request. Should be one of
     *      NX_ROLE_OTHER, NX_ROLE_MASTER or NX_ROLE_SLAVE.
     */
    public OFRoleRequestVendorData(int role) {
        super(NXT_ROLE_REQUEST, role);
    }
}
