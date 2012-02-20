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
 * The base class for all vendor data.
 *
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public interface OFVendorData {
    /**
     * @return length of the data
     */
    public int getLength();
    
    /**
     * Read the vendor data from the specified ChannelBuffer
     * @param data
     */
    public void readFrom(ChannelBuffer data, int length);

    /**
     * Write the vendor data to the specified ChannelBuffer
     * @param data
     */
    public void writeTo(ChannelBuffer data);
}
