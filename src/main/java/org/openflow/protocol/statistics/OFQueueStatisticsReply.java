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


import org.codehaus.jackson.annotate.JsonIgnore;
import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Represents an ofp_queue_stats structure
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFQueueStatisticsReply implements OFStatistics {
    protected short portNumber;
    protected int queueId;
    protected long transmitBytes;
    protected long transmitPackets;
    protected long transmitErrors;

    /**
     * @return the portNumber
     */
    public short getPortNumber() {
        return portNumber;
    }

    /**
     * @param portNumber the portNumber to set
     */
    public void setPortNumber(short portNumber) {
        this.portNumber = portNumber;
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

    /**
     * @return the transmitBytes
     */
    public long getTransmitBytes() {
        return transmitBytes;
    }

    /**
     * @param transmitBytes the transmitBytes to set
     */
    public void setTransmitBytes(long transmitBytes) {
        this.transmitBytes = transmitBytes;
    }

    /**
     * @return the transmitPackets
     */
    public long getTransmitPackets() {
        return transmitPackets;
    }

    /**
     * @param transmitPackets the transmitPackets to set
     */
    public void setTransmitPackets(long transmitPackets) {
        this.transmitPackets = transmitPackets;
    }

    /**
     * @return the transmitErrors
     */
    public long getTransmitErrors() {
        return transmitErrors;
    }

    /**
     * @param transmitErrors the transmitErrors to set
     */
    public void setTransmitErrors(long transmitErrors) {
        this.transmitErrors = transmitErrors;
    }

    @Override
    @JsonIgnore
    public int getLength() {
        return 32;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        this.portNumber = data.readShort();
        data.readShort(); // pad
        this.queueId = data.readInt();
        this.transmitBytes = data.readLong();
        this.transmitPackets = data.readLong();
        this.transmitErrors = data.readLong();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        data.writeShort(this.portNumber);
        data.writeShort((short) 0); // pad
        data.writeInt(this.queueId);
        data.writeLong(this.transmitBytes);
        data.writeLong(this.transmitPackets);
        data.writeLong(this.transmitErrors);
    }

    @Override
    public int hashCode() {
        final int prime = 439;
        int result = 1;
        result = prime * result + portNumber;
        result = prime * result + queueId;
        result = prime * result
                + (int) (transmitBytes ^ (transmitBytes >>> 32));
        result = prime * result
                + (int) (transmitErrors ^ (transmitErrors >>> 32));
        result = prime * result
                + (int) (transmitPackets ^ (transmitPackets >>> 32));
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
        if (!(obj instanceof OFQueueStatisticsReply)) {
            return false;
        }
        OFQueueStatisticsReply other = (OFQueueStatisticsReply) obj;
        if (portNumber != other.portNumber) {
            return false;
        }
        if (queueId != other.queueId) {
            return false;
        }
        if (transmitBytes != other.transmitBytes) {
            return false;
        }
        if (transmitErrors != other.transmitErrors) {
            return false;
        }
        if (transmitPackets != other.transmitPackets) {
            return false;
        }
        return true;
    }
}
