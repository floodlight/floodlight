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

import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Base class for the vendor ID corresponding to vendor extensions from a
 * given vendor. It is responsible for knowing how to parse out some sort of
 * data type value from the vendor data in an OFVendor message so that we can
 * dispatch to the different subclasses of OFVendorData corresponding to the
 * different formats of data for the vendor extensions.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public abstract class OFVendorId {
    static Map<Integer, OFVendorId> mapping = new HashMap<Integer, OFVendorId>();

    /**
     * The vendor id value, typically the OUI of the vendor prefixed with 0.
     */
    protected int id;
    
    /**
     * Register a new vendor id.
     * @param vendorId the vendor id to register
     */
    public static void registerVendorId(OFVendorId vendorId) {
        mapping.put(vendorId.getId(), vendorId);
    }
    
    /**
     * Lookup the OFVendorId instance corresponding to the given id value.
     * @param id the integer vendor id value
     * @return the corresponding OFVendorId that's been registered for the
     *     given value, or null if there id has not been registered.
     */
    public static OFVendorId lookupVendorId(int id) {
        return mapping.get(id);
    }
    
    /**
     * Create an OFVendorId with the give vendor id value
     * @param id
     */
    public OFVendorId(int id) {
        this.id = id;
    }
    
    /**
     * @return the vendor id value
     */
    public int getId() {
        return id;
    }
    
    /**
     * This function parses enough of the data from the channel buffer to be
     * able to determine the appropriate OFVendorDataType for the data.
     * 
     * @param data the channel buffer containing the vendor data.
     * @param length the length to the end of the enclosing message
     * @return the OFVendorDataType that can be used to instantiate the
     *         appropriate subclass of OFVendorData.
     */
    public abstract OFVendorDataType parseVendorDataType(ChannelBuffer data, int length);
}
