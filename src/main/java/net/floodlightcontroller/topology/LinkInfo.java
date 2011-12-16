/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.topology;

import org.openflow.protocol.OFPhysicalPort.OFPortState;

public class LinkInfo {
    public enum PortBroadcastState {
        PBS_BLOCK,
        PBS_FORWARD,
    };

    protected Long validTime;

    /** The port states stored here are topology's last knowledge of
     * the state of the port. This mostly mirrors the state
     * maintained in the port list in IOFSwitch (i.e. the one returned
     * from getPort), except that during a port status message the
     * IOFSwitch port state will already have been updated with the
     * new port state, so topology needs to keep its own copy so that
     * it can determine if the port state has changed and therefore
     * requires the new state to be written to storage and the clusters
     * recomputed.
     */
    protected Integer srcPortState;
    protected Integer dstPortState;

    /** If STP is not enabled in the natice switches or the STP state is
     * not known to the controller, controller constructs a broadcast tree
     * and blocks the links that may cause loop in an OF cluster.
     */
    protected PortBroadcastState bcState;

    public LinkInfo(Long validTime, Integer srcPortState, Integer dstPortState) {
        this.validTime = validTime;
        this.srcPortState = srcPortState;
        this.dstPortState = dstPortState;
        if (linkStpBlocked()) {
            this.bcState = PortBroadcastState.PBS_BLOCK;
        } else {
            this.bcState = PortBroadcastState.PBS_FORWARD;
        }
    }

    public boolean linkStpBlocked() {
        return ((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue()) ||
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue());
    }

    public boolean isBroadcastBlocked() {
        return (linkStpBlocked() || (bcState == PortBroadcastState.PBS_BLOCK));
    }
    public PortBroadcastState getBroadcastState() {
        return bcState;
    }

    public void setBroadcastState(PortBroadcastState bcState) {
        this.bcState = bcState;
    }

    public Long getValidTime() {
        return validTime;
    }

    public void setValidTime(Long validTime) {
        this.validTime = validTime;
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
        result = prime * result + ((validTime == null) ? 0 : validTime.hashCode());
        result = prime * result + ((srcPortState == null) ? 0 : validTime.hashCode());
        result = prime * result + ((dstPortState == null) ? 0 : dstPortState.hashCode());
        result = prime * result + ((bcState == null) ? 0 : bcState.hashCode());
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
        if (validTime == null) {
            if (other.validTime != null)
                return false;
        } else if (!validTime.equals(other.validTime))
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

        if (bcState == null) {
            if (other.bcState != null)
                return false;
        } else if (!bcState.equals(other.bcState))
            return false;

        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "LinkInfo [validTime=" + ((validTime == null) ? "null" : validTime)
                + ", srcPortState=" + ((srcPortState == null) ? "null" : srcPortState)
                + ", dstPortState=" + ((dstPortState == null) ? "null" : srcPortState)
                + ", bcState=" + ((bcState == null) ? "null" : bcState) + "]";
    }
}
