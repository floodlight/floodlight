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
 * Subclass of OFVendorDataType that works with any vendor data format that
 * begins with a integral value to indicate the format of the remaining data.
 * It maps from the per-vendor-id integral data type code to the object
 * used to instantiate the class associated with that vendor data type.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public class OFBasicVendorDataType extends OFVendorDataType {
    
    /**
     * The data type value at the beginning of the vendor data.
     */
    protected long type;
    
    /**
     * Construct an empty (i.e. no specified data type value) vendor data type.
     */
    public OFBasicVendorDataType() {
        super();
        this.type = 0;
    }
    
    /**
     * Store some information about the vendor data type, including wire protocol
     * type number, derived class and instantiator.
     *
     * @param type Wire protocol number associated with this vendor data type
     * @param instantiator An Instantiator<OFVendorData> implementation that
     *              creates an instance of an appropriate subclass of OFVendorData.
     */
    public OFBasicVendorDataType(long type, Instantiable<OFVendorData> instantiator) {
        super(instantiator);
        this.type = type;
    }

    /**
     * @return Returns the wire protocol value corresponding to this OFVendorDataType
     */
    public long getTypeValue() {
        return this.type;
    }
    
    /**
     * @param type the wire protocol value for this data type
     */
    public void setTypeValue(long type) {
        this.type = type;
    }
}
