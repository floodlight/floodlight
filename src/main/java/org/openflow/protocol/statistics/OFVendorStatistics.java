/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol.statistics;


import org.jboss.netty.buffer.ChannelBuffer;

/**
 * The base class for vendor implemented statistics
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFVendorStatistics implements OFStatistics {
    protected int vendor;
    protected byte[] body;

    // non-message fields
    protected int length = 0;

    @Override
    public void readFrom(ChannelBuffer data) {
        this.vendor = data.readInt();
        if (body == null)
            body = new byte[length - 4];
        data.readBytes(body);
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        data.writeInt(this.vendor);
        if (body != null)
            data.writeBytes(body);
    }

    @Override
    public int hashCode() {
        final int prime = 457;
        int result = 1;
        result = prime * result + vendor;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OFVendorStatistics)) {
            return false;
        }
        OFVendorStatistics other = (OFVendorStatistics) obj;
        if (vendor != other.vendor) {
            return false;
        }
        return true;
    }

    @Override
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
}
