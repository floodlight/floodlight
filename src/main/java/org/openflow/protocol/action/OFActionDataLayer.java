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

/**
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
package org.openflow.protocol.action;

import java.util.Arrays;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFPhysicalPort;

/**
 * Represents an ofp_action_dl_addr
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
public abstract class OFActionDataLayer extends OFAction {
    public static int MINIMUM_LENGTH = 16;

    protected byte[] dataLayerAddress;

    /**
     * @return the dataLayerAddress
     */
    public byte[] getDataLayerAddress() {
        return dataLayerAddress;
    }

    /**
     * @param dataLayerAddress the dataLayerAddress to set
     */
    public void setDataLayerAddress(byte[] dataLayerAddress) {
        this.dataLayerAddress = dataLayerAddress;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        if (this.dataLayerAddress == null)
            this.dataLayerAddress = new byte[OFPhysicalPort.OFP_ETH_ALEN];
        data.readBytes(this.dataLayerAddress);
        data.readInt();
        data.readShort();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeBytes(this.dataLayerAddress);
        data.writeInt(0);
        data.writeShort((short) 0);
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(dataLayerAddress);
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
        if (!(obj instanceof OFActionDataLayer)) {
            return false;
        }
        OFActionDataLayer other = (OFActionDataLayer) obj;
        if (!Arrays.equals(dataLayerAddress, other.dataLayerAddress)) {
            return false;
        }
        return true;
    }
}