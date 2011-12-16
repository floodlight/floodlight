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
import org.openflow.protocol.OFMatch;

/**
 * Represents an ofp_aggregate_stats_request structure
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFAggregateStatisticsRequest implements OFStatistics {
    protected OFMatch match;
    protected byte tableId;
    protected short outPort;

    /**
     * @return the match
     */
    public OFMatch getMatch() {
        return match;
    }

    /**
     * @param match the match to set
     */
    public void setMatch(OFMatch match) {
        this.match = match;
    }

    /**
     * @return the tableId
     */
    public byte getTableId() {
        return tableId;
    }

    /**
     * @param tableId the tableId to set
     */
    public void setTableId(byte tableId) {
        this.tableId = tableId;
    }

    /**
     * @return the outPort
     */
    public short getOutPort() {
        return outPort;
    }

    /**
     * @param outPort the outPort to set
     */
    public void setOutPort(short outPort) {
        this.outPort = outPort;
    }

    @Override
    public int getLength() {
        return 44;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        if (this.match == null)
            this.match = new OFMatch();
        this.match.readFrom(data);
        this.tableId = data.readByte();
        data.readByte(); // pad
        this.outPort = data.readShort();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        this.match.writeTo(data);
        data.writeByte(this.tableId);
        data.writeByte((byte) 0);
        data.writeShort(this.outPort);
    }

    @Override
    public int hashCode() {
        final int prime = 401;
        int result = 1;
        result = prime * result + ((match == null) ? 0 : match.hashCode());
        result = prime * result + outPort;
        result = prime * result + tableId;
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
        if (!(obj instanceof OFAggregateStatisticsRequest)) {
            return false;
        }
        OFAggregateStatisticsRequest other = (OFAggregateStatisticsRequest) obj;
        if (match == null) {
            if (other.match != null) {
                return false;
            }
        } else if (!match.equals(other.match)) {
            return false;
        }
        if (outPort != other.outPort) {
            return false;
        }
        if (tableId != other.tableId) {
            return false;
        }
        return true;
    }
}
