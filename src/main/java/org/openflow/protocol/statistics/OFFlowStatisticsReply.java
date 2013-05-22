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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.factory.OFActionFactory;
import org.openflow.protocol.factory.OFActionFactoryAware;
import org.openflow.util.U16;

/**
 * Represents an ofp_flow_stats structure
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFFlowStatisticsReply implements OFStatistics, OFActionFactoryAware {
    public static int MINIMUM_LENGTH = 88;

    protected OFActionFactory actionFactory;
    protected short length = (short) MINIMUM_LENGTH;
    protected byte tableId;
    protected OFMatch match;
    protected int durationSeconds;
    protected int durationNanoseconds;
    protected short priority;
    protected short idleTimeout;
    protected short hardTimeout;
    protected long cookie;
    protected long packetCount;
    protected long byteCount;
    protected List<OFAction> actions;

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
     * @return the durationSeconds
     */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * @param durationSeconds the durationSeconds to set
     */
    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /**
     * @return the durationNanoseconds
     */
    public int getDurationNanoseconds() {
        return durationNanoseconds;
    }

    /**
     * @param durationNanoseconds the durationNanoseconds to set
     */
    public void setDurationNanoseconds(int durationNanoseconds) {
        this.durationNanoseconds = durationNanoseconds;
    }

    /**
     * @return the priority
     */
    public short getPriority() {
        return priority;
    }

    /**
     * @param priority the priority to set
     */
    public void setPriority(short priority) {
        this.priority = priority;
    }

    /**
     * @return the idleTimeout
     */
    public short getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * @param idleTimeout the idleTimeout to set
     */
    public void setIdleTimeout(short idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    /**
     * @return the hardTimeout
     */
    public short getHardTimeout() {
        return hardTimeout;
    }

    /**
     * @param hardTimeout the hardTimeout to set
     */
    public void setHardTimeout(short hardTimeout) {
        this.hardTimeout = hardTimeout;
    }

    /**
     * @return the cookie
     */
    public long getCookie() {
        return cookie;
    }

    /**
     * @param cookie the cookie to set
     */
    public void setCookie(long cookie) {
        this.cookie = cookie;
    }

    /**
     * @return the packetCount
     */
    public long getPacketCount() {
        return packetCount;
    }

    /**
     * @param packetCount the packetCount to set
     */
    public void setPacketCount(long packetCount) {
        this.packetCount = packetCount;
    }

    /**
     * @return the byteCount
     */
    public long getByteCount() {
        return byteCount;
    }

    /**
     * @param byteCount the byteCount to set
     */
    public void setByteCount(long byteCount) {
        this.byteCount = byteCount;
    }

    /**
     * @param length the length to set
     */
    public void setLength(short length) {
        this.length = length;
    }

    @Override
    @JsonIgnore
    public int getLength() {
        return U16.f(length);
    }

    /**
     * @param actionFactory the actionFactory to set
     */
    @Override
    public void setActionFactory(OFActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    /**
     * @return the actions
     */
    public List<OFAction> getActions() {
        return actions;
    }

    /**
     * @param actions the actions to set
     */
    public void setActions(List<OFAction> actions) {
        this.actions = actions;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        this.length = data.readShort();
        this.tableId = data.readByte();
        data.readByte(); // pad
        if (this.match == null)
            this.match = new OFMatch();
        this.match.readFrom(data);
        this.durationSeconds = data.readInt();
        this.durationNanoseconds = data.readInt();
        this.priority = data.readShort();
        this.idleTimeout = data.readShort();
        this.hardTimeout = data.readShort();
        data.readInt(); // pad
        data.readShort(); // pad
        this.cookie = data.readLong();
        this.packetCount = data.readLong();
        this.byteCount = data.readLong();
        if (this.actionFactory == null)
            throw new RuntimeException("OFActionFactory not set");
        this.actions = this.actionFactory.parseActions(data, getLength() -
                MINIMUM_LENGTH);
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        data.writeShort(this.length);
        data.writeByte(this.tableId);
        data.writeByte((byte) 0);
        this.match.writeTo(data);
        data.writeInt(this.durationSeconds);
        data.writeInt(this.durationNanoseconds);
        data.writeShort(this.priority);
        data.writeShort(this.idleTimeout);
        data.writeShort(this.hardTimeout);
        data.writeInt(0); // pad
        data.writeShort((short)0); // pad
        data.writeLong(this.cookie);
        data.writeLong(this.packetCount);
        data.writeLong(this.byteCount);
        if (actions != null) {
            for (OFAction action : actions) {
                action.writeTo(data);
            }
        }
    }

    @Override
    public String toString() {
    	String str = "match=" + this.match;
    	str += " tableId=" + this.tableId;
    	str += " durationSeconds=" + this.durationSeconds;
    	str += " durationNanoseconds=" + this.durationNanoseconds;
    	str += " priority=" + this.priority;
    	str += " idleTimeout=" + this.idleTimeout;
    	str += " hardTimeout=" + this.hardTimeout;
        str += " cookie=" + Long.toHexString(this.cookie);
    	str += " packetCount=" + this.packetCount;
    	str += " byteCount=" + this.byteCount;
    	str += " action=" + this.actions;
    	
    	return str;
    }
    
    @Override
    public int hashCode() {
        final int prime = 419;
        int result = 1;
        result = prime * result + (int) (byteCount ^ (byteCount >>> 32));
        result = prime * result + (int) (cookie ^ (cookie >>> 32));
        result = prime * result + durationNanoseconds;
        result = prime * result + durationSeconds;
        result = prime * result + hardTimeout;
        result = prime * result + idleTimeout;
        result = prime * result + length;
        result = prime * result + ((match == null) ? 0 : match.hashCode());
        result = prime * result + (int) (packetCount ^ (packetCount >>> 32));
        result = prime * result + priority;
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
        if (!(obj instanceof OFFlowStatisticsReply)) {
            return false;
        }
        OFFlowStatisticsReply other = (OFFlowStatisticsReply) obj;
        if (byteCount != other.byteCount) {
            return false;
        }
        if (cookie != other.cookie) {
            return false;
        }
        if (durationNanoseconds != other.durationNanoseconds) {
            return false;
        }
        if (durationSeconds != other.durationSeconds) {
            return false;
        }
        if (hardTimeout != other.hardTimeout) {
            return false;
        }
        if (idleTimeout != other.idleTimeout) {
            return false;
        }
        if (length != other.length) {
            return false;
        }
        if (match == null) {
            if (other.match != null) {
                return false;
            }
        } else if (!match.equals(other.match)) {
            return false;
        }
        if (packetCount != other.packetCount) {
            return false;
        }
        if (priority != other.priority) {
            return false;
        }
        if (tableId != other.tableId) {
            return false;
        }
        return true;
    }
}
