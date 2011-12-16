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
 * Represents an ofp_action_enqueue
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
public class OFActionEnqueue extends OFAction {
    public static int MINIMUM_LENGTH = 16;

    protected short port;
    protected int queueId;

    public OFActionEnqueue() {
        super.setType(OFActionType.OPAQUE_ENQUEUE);
        super.setLength((short) MINIMUM_LENGTH);
    }

    /**
     * Get the output port
     * @return
     */
    public short getPort() {
        return this.port;
    }

    /**
     * Set the output port
     * @param port
     */
    public void setPort(short port) {
        this.port = port;
    }

    /**
     * @return the queueId
     */
    public int getQueueId() {
        return queueId;
    }

    /**
     * @param queueId the queueId to set
     */
    public void setQueueId(int queueId) {
        this.queueId = queueId;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.port = data.readShort();
        data.readShort();
        data.readInt();
        this.queueId = data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.port);
        data.writeShort((short) 0);
        data.writeInt(0);
        data.writeInt(this.queueId);
    }

    @Override
    public int hashCode() {
        final int prime = 349;
        int result = super.hashCode();
        result = prime * result + port;
        result = prime * result + queueId;
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
        if (!(obj instanceof OFActionEnqueue)) {
            return false;
        }
        OFActionEnqueue other = (OFActionEnqueue) obj;
        if (port != other.port) {
            return false;
        }
        if (queueId != other.queueId) {
            return false;
        }
        return true;
    }
}