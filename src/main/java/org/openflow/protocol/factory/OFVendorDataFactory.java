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

package org.openflow.protocol.factory;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.vendor.OFVendorData;
import org.openflow.protocol.vendor.OFVendorDataType;
import org.openflow.protocol.vendor.OFVendorId;

/**
 * The interface to factories used for parsing/creating OFVendorData instances.
 * All methods are expected to be thread-safe.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public interface OFVendorDataFactory {
    /**
     * Retrieves an OFVendorData instance corresponding to the specified
     * OFVendorId and OFVendorDataType. There are 3 possible cases for
     * how this will be called:
     * 
     * 1) If the vendor id in the OFVendor message is an unknown value, 
     *    then this method is called with both vendorId and vendorDataType
     *    set to null. In this case typically the factory method should
     *    return an instance of OFGenericVendorData that just contains
     *    the raw byte array of the vendor data.
     *    
     * 2) If the vendor id is known but no vendor data type has been
     *    registered for the data in the message, then vendorId is set to
     *    the appropriate OFVendorId instance and OFVendorDataType is set
     *    to null. This would typically be handled the same way as #1
     *    
     * 3) If both the vendor id and and vendor data type are known, then
     *    typically you'd just call the method in OFVendorDataType to
     *    instantiate the appropriate subclass of OFVendorData.
     *    
     * @param vendorId the vendorId of the containing OFVendor message
     * @param vendorDataType the type of the OFVendorData to be retrieved
     * @return an OFVendorData instance
     */
    public OFVendorData getVendorData(OFVendorId vendorId,
            OFVendorDataType vendorDataType);
    
    /**
     * Attempts to parse and return the OFVendorData contained in the given
     * ChannelBuffer, beginning right after the vendor id.
     * @param vendorId the vendor id that was parsed from the OFVendor message.
     * @param data the ChannelBuffer from which to parse the vendor data
     * @param length the length to the end of the enclosing message.
     * @return an OFVendorData instance
     */
    public OFVendorData parseVendorData(int vendorId, ChannelBuffer data,
            int length);
}
