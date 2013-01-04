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


import java.util.Arrays;

import org.jboss.netty.buffer.ChannelBuffer;

/** A generic / unparsed vendor action. This action is returned by
 *  BasicFactory.readFromWire if no more specific OFVendorActionFactory
 *  is registered.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class OFActionVendorGeneric extends OFActionVendor {
    public static int MINIMUM_LENGTH = 8;

    private final static byte[] EMPTY_ARRAY = new byte[0];

    protected byte[] vendorData;

    public OFActionVendorGeneric() {
        super();
    }

    public byte[] getVendorData() {
        return vendorData;
    }

    public void setVendorData(byte[] vendorData) {
        this.vendorData = vendorData;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);

        int vendorDataLength = this.getLength() - MINIMUM_LENGTH;
        if (vendorDataLength > 0) {
            vendorData = new byte[vendorDataLength];
            data.readBytes(vendorData);
        } else {
            vendorData = EMPTY_ARRAY;
        }
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(this.vendor);
        data.writeBytes(vendorData);
    }

    @Override
    public int hashCode() {
        final int prime = 379;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(vendorData);
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
        if (!(obj instanceof OFActionVendorGeneric)) {
            return false;
        }
        OFActionVendorGeneric other = (OFActionVendorGeneric) obj;
        if (!Arrays.equals(vendorData, other.vendorData)) {
            return false;
        }
        return true;
    }
}
