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

package org.openflow.protocol.action;


import org.jboss.netty.buffer.ChannelBuffer;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public abstract class OFActionVendor extends OFAction {
    public static int MINIMUM_LENGTH = 8;

    protected int vendor;

    public OFActionVendor() {
        super();
        super.setType(OFActionType.VENDOR);
        super.setLength((short) MINIMUM_LENGTH);
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
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.vendor);
    }

    @Override
    public int hashCode() {
        final int prime = 379;
        int result = super.hashCode();
        result = prime * result + vendor;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof OFActionVendor)) {
            return false;
        }
        OFActionVendor other = (OFActionVendor) obj;
        if (vendor != other.vendor) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + "; vendor=" + vendor;
    }
}
