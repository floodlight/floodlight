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

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;

import org.openflow.protocol.OFPhysicalPort.OFPortState;

public class LinkInfo {

    public LinkInfo(Long firstSeenTime,
                    Long lastLldpReceivedTime,
                    Long lastBddpReceivedTime,
                    int srcPortState,
                    int dstPortState) {
        super();
        this.srcPortState = srcPortState;
        this.dstPortState = dstPortState;
        this.firstSeenTime = firstSeenTime;
        this.lastLldpReceivedTime = lastLldpReceivedTime;
        this.lastBddpReceivedTime = lastBddpReceivedTime;
    }

    protected Integer srcPortState;
    protected Integer dstPortState;
    protected Long firstSeenTime;
    protected Long lastLldpReceivedTime; /* Standard LLLDP received time */
    protected Long lastBddpReceivedTime; /* Modified LLDP received time  */

    /** The port states stored here are topology's last knowledge of
     * the state of the port. This mostly mirrors the state
     * maintained in the port list in IOFSwitch (i.e. the one returned
     * from getPort), except that during a port status message the
     * IOFSwitch port state will already have been updated with the
     * new port state, so topology needs to keep its own copy so that
     * it can determine if the port state has changed and therefore
     * requires the new state to be written to storage.
     */



    public boolean linkStpBlocked() {
        return ((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue()) ||
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue());
    }

    public Long getFirstSeenTime() {
        return firstSeenTime;
    }

    public void setFirstSeenTime(Long firstSeenTime) {
        this.firstSeenTime = firstSeenTime;
    }

    public Long getUnicastValidTime() {
        return lastLldpReceivedTime;
    }

    public void setUnicastValidTime(Long unicastValidTime) {
        this.lastLldpReceivedTime = unicastValidTime;
    }

    public Long getMulticastValidTime() {
        return lastBddpReceivedTime;
    }

    public void setMulticastValidTime(Long multicastValidTime) {
        this.lastBddpReceivedTime = multicastValidTime;
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

    public LinkType getLinkType() {
        if (lastLldpReceivedTime != null) {
            return LinkType.DIRECT_LINK;
        } else if (lastBddpReceivedTime != null) {
            return LinkType.MULTIHOP_LINK;
        }
        return LinkType.INVALID_LINK;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5557;
        int result = 1;
        result = prime * result + ((firstSeenTime == null) ? 0 : firstSeenTime.hashCode());
        result = prime * result + ((lastLldpReceivedTime == null) ? 0 : lastLldpReceivedTime.hashCode());
        result = prime * result + ((lastBddpReceivedTime == null) ? 0 : lastBddpReceivedTime.hashCode());
        result = prime * result + ((srcPortState == null) ? 0 : srcPortState.hashCode());
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

        if (firstSeenTime == null) {
            if (other.firstSeenTime != null)
                return false;
        } else if (!firstSeenTime.equals(other.firstSeenTime))
            return false;

        if (lastLldpReceivedTime == null) {
            if (other.lastLldpReceivedTime != null)
                return false;
        } else if (!lastLldpReceivedTime.equals(other.lastLldpReceivedTime))
            return false;

        if (lastBddpReceivedTime == null) {
            if (other.lastBddpReceivedTime != null)
                return false;
        } else if (!lastBddpReceivedTime.equals(other.lastBddpReceivedTime))
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
        return "LinkInfo [unicastValidTime=" + ((lastLldpReceivedTime == null) ? "null" : lastLldpReceivedTime)
                + ", multicastValidTime=" + ((lastBddpReceivedTime == null) ? "null" : lastBddpReceivedTime)
                + ", srcPortState=" + ((srcPortState == null) ? "null" : srcPortState)
                + ", dstPortState=" + ((dstPortState == null) ? "null" : srcPortState)
                + "]";
    }
}
