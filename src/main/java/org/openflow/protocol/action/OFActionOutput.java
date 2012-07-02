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
import org.openflow.util.U16;

/**
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 */
public class OFActionOutput extends OFAction implements Cloneable {
    public static int MINIMUM_LENGTH = 8;

    protected short port;
    protected short maxLength;

    public OFActionOutput() {
        super.setType(OFActionType.OUTPUT);
        super.setLength((short) MINIMUM_LENGTH);
    }

    /**
     * Create an Output Action sending packets out the specified
     * OpenFlow port.
     *
     * This is the most common creation pattern for OFActions.
     *
     * @param port
     */

    public OFActionOutput(short port) {
        this(port, (short) 65535);
    }

    /**
     * Create an Output Action specifying both the port AND
     * the snaplen of the packet to send out that port.
     * The length field is only meaningful when port == OFPort.OFPP_CONTROLLER
     * @param port
     * @param maxLength The maximum number of bytes of the packet to send.
     * Most hardware only supports this value for OFPP_CONTROLLER
     */

    public OFActionOutput(short port, short maxLength) {
        super();
        super.setType(OFActionType.OUTPUT);
        super.setLength((short) MINIMUM_LENGTH);
        this.port = port;
        this.maxLength = maxLength;
    }

    /**
     * Get the output port
     * @return
     */
    public short getPort() {
        return this.port;
    }

    /**
     * Set the output port
     * @param port
     */
    public OFActionOutput setPort(short port) {
        this.port = port;
        return this;
    }

    /**
     * Get the max length to send to the controller
     * @return
     */
    public short getMaxLength() {
        return this.maxLength;
    }

    /**
     * Set the max length to send to the controller
     * @param maxLength
     */
    public OFActionOutput setMaxLength(short maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.port = data.readShort();
        this.maxLength = data.readShort();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(port);
        data.writeShort(maxLength);
    }

    @Override
    public int hashCode() {
        final int prime = 367;
        int result = super.hashCode();
        result = prime * result + maxLength;
        result = prime * result + port;
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
        if (!(obj instanceof OFActionOutput)) {
            return false;
        }
        OFActionOutput other = (OFActionOutput) obj;
        if (maxLength != other.maxLength) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "OFActionOutput [maxLength=" + maxLength + ", port=" + U16.f(port)
                + ", length=" + length + ", type=" + type + "]";
    }
}