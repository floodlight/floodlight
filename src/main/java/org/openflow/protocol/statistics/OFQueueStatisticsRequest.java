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
 * Represents an ofp_queue_stats_request structure
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFQueueStatisticsRequest implements OFStatistics {
    protected short portNumber;
    protected int queueId;

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

    @Override
    public int getLength() {
        return 8;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        this.portNumber = data.readShort();
        data.readShort(); // pad
        this.queueId = data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        data.writeShort(this.portNumber);
        data.writeShort((short) 0); // pad
        data.writeInt(this.queueId);
    }

    @Override
    public int hashCode() {
        final int prime = 443;
        int result = 1;
        result = prime * result + portNumber;
        result = prime * result + queueId;
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
        if (!(obj instanceof OFQueueStatisticsRequest)) {
            return false;
        }
        OFQueueStatisticsRequest other = (OFQueueStatisticsRequest) obj;
        if (portNumber != other.portNumber) {
            return false;
        }
        if (queueId != other.queueId) {
            return false;
        }
        return true;
    }
}
