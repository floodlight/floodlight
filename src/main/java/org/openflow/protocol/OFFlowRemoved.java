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


import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

/**
 * Represents an ofp_flow_removed message
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class OFFlowRemoved extends OFMessage {
    public static int MINIMUM_LENGTH = 88;

    public enum OFFlowRemovedReason {
        OFPRR_IDLE_TIMEOUT,
        OFPRR_HARD_TIMEOUT,
        OFPRR_DELETE
    }

    protected OFMatch match;
    protected long cookie;
    protected short priority;
    protected OFFlowRemovedReason reason;
    protected int durationSeconds;
    protected int durationNanoseconds;
    protected short idleTimeout;
    protected long packetCount;
    protected long byteCount;
    
    public OFFlowRemoved() {
        super();
        this.type = OFType.FLOW_REMOVED;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /**
     * Get cookie
     * @return
     */
    public long getCookie() {
        return this.cookie;
    }

    /**
     * Set cookie
     * @param cookie
     */
    public void setCookie(long cookie) {
        this.cookie = cookie;
    }

    /**
     * Get idle_timeout
     * @return
     */
    public short getIdleTimeout() {
        return this.idleTimeout;
    }

    /**
     * Set idle_timeout
     * @param idleTimeout
     */
    public void setIdleTimeout(short idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    /**
     * Gets a copy of the OFMatch object for this FlowMod, changes to this
     * object do not modify the FlowMod
     * @return
     */
    public OFMatch getMatch() {
        return this.match;
    }

    /**
     * Set match
     * @param match
     */
    public void setMatch(OFMatch match) {
        this.match = match;
    }

    /**
     * Get priority
     * @return
     */
    public short getPriority() {
        return this.priority;
    }

    /**
     * Set priority
     * @param priority
     */
    public void setPriority(short priority) {
        this.priority = priority;
    }

    /**
     * @return the reason
     */
    public OFFlowRemovedReason getReason() {
        return reason;
    }

    /**
     * @param reason the reason to set
     */
    public void setReason(OFFlowRemovedReason reason) {
        this.reason = reason;
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

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        if (this.match == null)
            this.match = new OFMatch();
        this.match.readFrom(data);
        this.cookie = data.readLong();
        this.priority = data.readShort();
        this.reason = OFFlowRemovedReason.values()[(0xff & data.readByte())];
        data.readByte(); // pad
        this.durationSeconds = data.readInt();
        this.durationNanoseconds = data.readInt();
        this.idleTimeout = data.readShort();
        data.readByte(); // pad
        data.readByte(); // pad
        this.packetCount = data.readLong();
        this.byteCount = data.readLong();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        this.match.writeTo(data);
        data.writeLong(cookie);
        data.writeShort(priority);
        data.writeByte((byte) this.reason.ordinal());
        data.writeByte((byte) 0);
        data.writeInt(this.durationSeconds);
        data.writeInt(this.durationNanoseconds);
        data.writeShort(idleTimeout);
        data.writeByte((byte) 0); // pad
        data.writeByte((byte) 0); // pad
        data.writeLong(this.packetCount);
        data.writeLong(this.byteCount);
    }

    @Override
    public int hashCode() {
        final int prime = 271;
        int result = super.hashCode();
        result = prime * result + (int) (byteCount ^ (byteCount >>> 32));
        result = prime * result + (int) (cookie ^ (cookie >>> 32));
        result = prime * result + durationNanoseconds;
        result = prime * result + durationSeconds;
        result = prime * result + idleTimeout;
        result = prime * result + ((match == null) ? 0 : match.hashCode());
        result = prime * result + (int) (packetCount ^ (packetCount >>> 32));
        result = prime * result + priority;
        result = prime * result + ((reason == null) ? 0 : reason.hashCode());
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
        if (!(obj instanceof OFFlowRemoved)) {
            return false;
        }
        OFFlowRemoved other = (OFFlowRemoved) obj;
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
        if (idleTimeout != other.idleTimeout) {
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
        if (reason == null) {
            if (other.reason != null) {
                return false;
            }
        } else if (!reason.equals(other.reason)) {
            return false;
        }
        return true;
    }
}
