/**
*    Copyright 2011, Big Switch Networks, Inc.*    Originally created by David Erickson, Stanford University
**    Licensed under the Apache License, Version 2.0 (the "License"); you may
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

package net.floodlightcontroller.linkdiscovery;

import org.openflow.protocol.OFPhysicalPort.OFPortState;

public class LinkInfo {
    public enum PortBroadcastState {
        PBS_BLOCK,
        PBS_FORWARD,
    };

    /**
     * The term unicastValidTime may be slightly misleading here.
     * The standard LLDP destination MAC address that is currently
     * used is also a multicast address, however since this address
     * is specified in the standard, we expect all switches to
     * absorb this packet, thus making the standard LLDP packet
     * traverse only one link.
     */
    protected Long unicastValidTime;
    protected Long multicastValidTime;

    /** The port states stored here are topology's last knowledge of
     * the state of the port. This mostly mirrors the state
     * maintained in the port list in IOFSwitch (i.e. the one returned
     * from getPort), except that during a port status message the
     * IOFSwitch port state will already have been updated with the
     * new port state, so topology needs to keep its own copy so that
     * it can determine if the port state has changed and therefore
     * requires the new state to be written to storage.
     */
    protected Integer srcPortState;
    protected Integer dstPortState;

    public LinkInfo(Long unicastValidTime,
                    Long broadcastValidTime,
                    Integer srcPortState,
                    Integer dstPortState) {
        this.unicastValidTime = unicastValidTime;
        this.multicastValidTime = broadcastValidTime;
        this.srcPortState = srcPortState;
        this.dstPortState = dstPortState;
    }

    public boolean linkStpBlocked() {
        return ((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue()) ||
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue());
    }

    public Long getUnicastValidTime() {
        return unicastValidTime;
    }

    public void setUnicastValidTime(Long unicastValidTime) {
        this.unicastValidTime = unicastValidTime;
    }

    public Long getMulticastValidTime() {
        return multicastValidTime;
    }

    public void setMulticastValidTime(Long multicastValidTime) {
        this.multicastValidTime = multicastValidTime;
    }

    public Integer getSrcPortState() {
        return srcPortState;
    }

    public void setSrcPortState(Integer srcPortState) {
        this.srcPortState = srcPortState;
    }

    public Integer getDstPortState() {
        return dstPortState;
    }

    public void setDstPortState(int dstPortState) {
        this.dstPortState = dstPortState;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5557;
        int result = 1;
        result = prime * result + ((unicastValidTime == null) ? 0 : unicastValidTime.hashCode());
        result = prime * result + ((multicastValidTime == null) ? 0 : multicastValidTime.hashCode());
        result = prime * result + ((srcPortState == null) ? 0 : unicastValidTime.hashCode());
        result = prime * result + ((dstPortState == null) ? 0 : dstPortState.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof LinkInfo))
            return false;
        LinkInfo other = (LinkInfo) obj;

        if (unicastValidTime == null) {
            if (other.unicastValidTime != null)
                return false;
        } else if (!unicastValidTime.equals(other.unicastValidTime))
            return false;

        if (multicastValidTime == null) {
            if (other.multicastValidTime != null)
                return false;
        } else if (!multicastValidTime.equals(other.multicastValidTime))
            return false;

        if (srcPortState == null) {
            if (other.srcPortState != null)
                return false;
        } else if (!srcPortState.equals(other.srcPortState))
            return false;

        if (dstPortState == null) {
            if (other.dstPortState != null)
                return false;
        } else if (!dstPortState.equals(other.dstPortState))
            return false;

        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "LinkInfo [unicastValidTime=" + ((unicastValidTime == null) ? "null" : unicastValidTime)
                + "multicastValidTime=" + ((multicastValidTime == null) ? "null" : multicastValidTime)
                + ", srcPortState=" + ((srcPortState == null) ? "null" : srcPortState)
                + ", dstPortState=" + ((dstPortState == null) ? "null" : srcPortState)
                + "]";
    }
}
