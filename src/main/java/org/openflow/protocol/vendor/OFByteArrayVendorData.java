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

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Basic implementation of OFVendorData that just treats the data as a
 * byte array. This is used if there's an OFVendor message where there's
 * no registered OFVendorId or no specific OFVendorDataType that can be
 * determined from the data.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public class OFByteArrayVendorData implements OFVendorData {

    protected byte[] bytes;
    
    /**
     * Construct vendor data with an empty byte array.
     */
    public OFByteArrayVendorData() {
    }
    
    /**
     * Construct vendor data with the specified byte array.
     * @param bytes
     */
    public OFByteArrayVendorData(byte[] bytes) {
        this.bytes = bytes;
    }
    
    /**
     * Get the associated byte array for this vendor data.
     * @return the byte array containing the raw vendor data.
     */
    public byte[] getBytes() {
        return bytes;
    }
    
    /**
     * Set the byte array for the vendor data.
     * @param bytes the raw byte array containing the vendor data.
     */
    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
    
    /**
     * Get the length of the vendor data. In this case it's just then length
     * of the underlying byte array.
     * @return the length of the vendor data
     */
    @Override
    public int getLength() {
        return (bytes != null) ? bytes.length : 0;
    }

    /**
     * Read the vendor data from the ChannelBuffer into the byte array.
     * @param data the channel buffer from which we're deserializing
     * @param length the length to the end of the enclosing message
     */
    @Override
    public void readFrom(ChannelBuffer data, int length) {
        bytes = new byte[length];
        data.readBytes(bytes);
    }

    /**
     * Write the vendor data bytes to the ChannelBuffer
     * @param data the channel buffer to which we're serializing
     */
    @Override
    public void writeTo(ChannelBuffer data) {
        if (bytes != null)
            data.writeBytes(bytes);
    }
}
