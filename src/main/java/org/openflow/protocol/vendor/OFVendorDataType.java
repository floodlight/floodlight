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

package org.openflow.protocol.vendor;

import org.openflow.protocol.Instantiable;

/**
 * Class that represents a specific vendor data type format in an
 * OFVendor message. Typically the vendor data will begin with an integer
 * code that determines the format of the rest of the data, but this
 * class does not assume that. It's basically just a holder for an
 * instantiator of the appropriate subclass of OFVendorData.
 *
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public class OFVendorDataType {

    /**
     * Object that instantiates the subclass of OFVendorData 
     * associated with this data type.
     */
    protected Instantiable<OFVendorData> instantiable;

    /**
     * Construct an empty vendor data type.
     */
    public OFVendorDataType() {
        super();
    }

    /**
     * Construct a vendor data type with the specified instantiable.
     * @param instantiable object that creates the subclass of OFVendorData
     *     associated with this data type.
     */
    public OFVendorDataType(Instantiable<OFVendorData> instantiable) {
        this.instantiable = instantiable;
    }
    
    /**
     * Returns a new instance of a subclass of OFVendorData associated with
     * this OFVendorDataType.
     * 
     * @return the new object
     */
    public OFVendorData newInstance() {
        return instantiable.instantiate();
    }

    /**
     * @return the instantiable
     */
    public Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * @param instantiable the instantiable to set
     */
    public void setInstantiable(Instantiable<OFVendorData> instantiable) {
        this.instantiable = instantiable;
    }

}
