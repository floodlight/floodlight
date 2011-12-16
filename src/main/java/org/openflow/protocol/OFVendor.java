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

package org.openflow.protocol;

import java.util.Arrays;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

/**
 * Represents ofp_vendor_header
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFVendor extends OFMessage {
    public static int MINIMUM_LENGTH = 12;

    protected int vendor;
    protected byte[] data;

    public OFVendor() {
        super();
        this.type = OFType.VENDOR;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /**
     * @return the vendor
     */
    public int getVendor() {
        return vendor;
    }

    /**
     * @param vendor the vendor to set
     */
    public void setVendor(int vendor) {
        this.vendor = vendor;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.vendor = data.readInt();
        if (this.length > MINIMUM_LENGTH) {
            this.data = new byte[this.length - MINIMUM_LENGTH];
            data.readBytes(this.data);
        }
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.vendor);
        if (this.data != null)
            data.writeBytes(this.data);
    }

    /**
     * @return the data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(byte[] data) {
        this.data = data;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 337;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(data);
        result = prime * result + vendor;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        OFVendor other = (OFVendor) obj;
        if (!Arrays.equals(data, other.data))
            return false;
        if (vendor != other.vendor)
            return false;
        return true;
    }
}
