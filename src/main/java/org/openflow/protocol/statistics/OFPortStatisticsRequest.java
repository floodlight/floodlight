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
 * Represents an ofp_port_stats_request structure
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFPortStatisticsRequest implements OFStatistics {
    protected short portNumber;

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

    @Override
    public int getLength() {
        return 8;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        this.portNumber = data.readShort();
        data.readShort(); // pad
        data.readInt(); // pad
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        data.writeShort(this.portNumber);
        data.writeShort((short) 0); // pad
        data.writeInt(0); // pad
    }

    @Override
    public int hashCode() {
        final int prime = 433;
        int result = 1;
        result = prime * result + portNumber;
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
        if (!(obj instanceof OFPortStatisticsRequest)) {
            return false;
        }
        OFPortStatisticsRequest other = (OFPortStatisticsRequest) obj;
        if (portNumber != other.portNumber) {
            return false;
        }
        return true;
    }
}
