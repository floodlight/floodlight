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
 * Represents an ofp_action_tp_port
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
public abstract class OFActionTransportLayer extends OFAction {
    public static int MINIMUM_LENGTH = 8;

    protected short transportPort;

    /**
     * @return the transportPort
     */
    public short getTransportPort() {
        return transportPort;
    }

    /**
     * @param transportPort the transportPort to set
     */
    public void setTransportPort(short transportPort) {
        this.transportPort = transportPort;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.transportPort = data.readShort();
        data.readShort();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.transportPort);
        data.writeShort((short) 0);
    }

    @Override
    public int hashCode() {
        final int prime = 373;
        int result = super.hashCode();
        result = prime * result + transportPort;
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
        if (!(obj instanceof OFActionTransportLayer)) {
            return false;
        }
        OFActionTransportLayer other = (OFActionTransportLayer) obj;
        if (transportPort != other.transportPort) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(type);
        builder.append("[");
        builder.append(transportPort);
        builder.append("]");
        return builder.toString();
    }
}