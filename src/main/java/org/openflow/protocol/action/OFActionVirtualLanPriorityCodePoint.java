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


import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Represents an ofp_action_vlan_pcp
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
public class OFActionVirtualLanPriorityCodePoint extends OFAction {
    public static int MINIMUM_LENGTH = 8;

    protected byte virtualLanPriorityCodePoint;

    public OFActionVirtualLanPriorityCodePoint() {
        super.setType(OFActionType.SET_VLAN_PCP);
        super.setLength((short) MINIMUM_LENGTH);
    }
    
    public OFActionVirtualLanPriorityCodePoint(byte priority) {
        this();
        this.virtualLanPriorityCodePoint = priority;
    }

    /**
     * @return the virtualLanPriorityCodePoint
     */
    public byte getVirtualLanPriorityCodePoint() {
        return virtualLanPriorityCodePoint;
    }

    /**
     * @param virtualLanPriorityCodePoint the virtualLanPriorityCodePoint to set
     */
    public void setVirtualLanPriorityCodePoint(byte virtualLanPriorityCodePoint) {
        this.virtualLanPriorityCodePoint = virtualLanPriorityCodePoint;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.virtualLanPriorityCodePoint = data.readByte();
        data.readShort(); // pad
        data.readByte(); // pad
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeByte(this.virtualLanPriorityCodePoint);
        data.writeShort((short) 0);
        data.writeByte((byte) 0);
    }

    @Override
    public int hashCode() {
        final int prime = 389;
        int result = super.hashCode();
        result = prime * result + virtualLanPriorityCodePoint;
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
        if (!(obj instanceof OFActionVirtualLanPriorityCodePoint)) {
            return false;
        }
        OFActionVirtualLanPriorityCodePoint other = (OFActionVirtualLanPriorityCodePoint) obj;
        if (virtualLanPriorityCodePoint != other.virtualLanPriorityCodePoint) {
            return false;
        }
        return true;
    }
}