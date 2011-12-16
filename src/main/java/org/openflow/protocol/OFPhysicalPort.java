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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Represents ofp_phy_port
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 25, 2010
 */
public class OFPhysicalPort {
    public static int MINIMUM_LENGTH = 48;
    public static int OFP_ETH_ALEN = 6;

    public enum OFPortConfig {
        OFPPC_PORT_DOWN    (1 << 0),
        OFPPC_NO_STP       (1 << 1),
        OFPPC_NO_RECV      (1 << 2),
        OFPPC_NO_RECV_STP  (1 << 3),
        OFPPC_NO_FLOOD     (1 << 4),
        OFPPC_NO_FWD       (1 << 5),
        OFPPC_NO_PACKET_IN (1 << 6);

        protected int value;

        private OFPortConfig(int value) {
            this.value = value;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }

    public enum OFPortState {
        OFPPS_LINK_DOWN   (1 << 0),
        OFPPS_STP_LISTEN  (0 << 8),
        OFPPS_STP_LEARN   (1 << 8),
        OFPPS_STP_FORWARD (2 << 8),
        OFPPS_STP_BLOCK   (3 << 8),
        OFPPS_STP_MASK    (3 << 8);

        protected int value;

        private OFPortState(int value) {
            this.value = value;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }

    public enum OFPortFeatures {
        OFPPF_10MB_HD    (1 << 0),
        OFPPF_10MB_FD    (1 << 1),
        OFPPF_100MB_HD   (1 << 2),
        OFPPF_100MB_FD   (1 << 3),
        OFPPF_1GB_HD     (1 << 4),
        OFPPF_1GB_FD     (1 << 5),
        OFPPF_10GB_FD    (1 << 6),
        OFPPF_COPPER     (1 << 7),
        OFPPF_FIBER      (1 << 8),
        OFPPF_AUTONEG    (1 << 9),
        OFPPF_PAUSE      (1 << 10),
        OFPPF_PAUSE_ASYM (1 << 11);

        protected int value;

        private OFPortFeatures(int value) {
            this.value = value;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }

    protected short portNumber;
    protected byte[] hardwareAddress;
    protected String name;
    protected int config;
    protected int state;
    protected int currentFeatures;
    protected int advertisedFeatures;
    protected int supportedFeatures;
    protected int peerFeatures;

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
     * @return the hardwareAddress
     */
    public byte[] getHardwareAddress() {
        return hardwareAddress;
    }

    /**
     * @param hardwareAddress the hardwareAddress to set
     */
    public void setHardwareAddress(byte[] hardwareAddress) {
        if (hardwareAddress.length != OFP_ETH_ALEN)
            throw new RuntimeException("Hardware address must have length "
                    + OFP_ETH_ALEN);
        this.hardwareAddress = hardwareAddress;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the config
     */
    public int getConfig() {
        return config;
    }

    /**
     * @param config the config to set
     */
    public void setConfig(int config) {
        this.config = config;
    }

    /**
     * @return the state
     */
    public int getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(int state) {
        this.state = state;
    }

    /**
     * @return the currentFeatures
     */
    public int getCurrentFeatures() {
        return currentFeatures;
    }

    /**
     * @param currentFeatures the currentFeatures to set
     */
    public void setCurrentFeatures(int currentFeatures) {
        this.currentFeatures = currentFeatures;
    }

    /**
     * @return the advertisedFeatures
     */
    public int getAdvertisedFeatures() {
        return advertisedFeatures;
    }

    /**
     * @param advertisedFeatures the advertisedFeatures to set
     */
    public void setAdvertisedFeatures(int advertisedFeatures) {
        this.advertisedFeatures = advertisedFeatures;
    }

    /**
     * @return the supportedFeatures
     */
    public int getSupportedFeatures() {
        return supportedFeatures;
    }

    /**
     * @param supportedFeatures the supportedFeatures to set
     */
    public void setSupportedFeatures(int supportedFeatures) {
        this.supportedFeatures = supportedFeatures;
    }

    /**
     * @return the peerFeatures
     */
    public int getPeerFeatures() {
        return peerFeatures;
    }

    /**
     * @param peerFeatures the peerFeatures to set
     */
    public void setPeerFeatures(int peerFeatures) {
        this.peerFeatures = peerFeatures;
    }

    /**
     * Read this message off the wire from the specified ByteBuffer
     * @param data
     */
    public void readFrom(ChannelBuffer data) {
        this.portNumber = data.readShort();
        if (this.hardwareAddress == null)
            this.hardwareAddress = new byte[OFP_ETH_ALEN];
        data.readBytes(this.hardwareAddress);
        byte[] name = new byte[16];
        data.readBytes(name);
        // find the first index of 0
        int index = 0;
        for (byte b : name) {
            if (0 == b)
                break;
            ++index;
        }
        this.name = new String(Arrays.copyOf(name, index),
                Charset.forName("ascii"));
        this.config = data.readInt();
        this.state = data.readInt();
        this.currentFeatures = data.readInt();
        this.advertisedFeatures = data.readInt();
        this.supportedFeatures = data.readInt();
        this.peerFeatures = data.readInt();
    }

    /**
     * Write this message's binary format to the specified ByteBuffer
     * @param data
     */
    public void writeTo(ChannelBuffer data) {
        data.writeShort(this.portNumber);
        data.writeBytes(hardwareAddress);
        try {
            byte[] name = this.name.getBytes("ASCII");
            if (name.length < 16) {
                data.writeBytes(name);
                for (int i = name.length; i < 16; ++i) {
                    data.writeByte((byte) 0);
                }
            } else {
                data.writeBytes(name, 0, 15);
                data.writeByte((byte) 0);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        data.writeInt(this.config);
        data.writeInt(this.state);
        data.writeInt(this.currentFeatures);
        data.writeInt(this.advertisedFeatures);
        data.writeInt(this.supportedFeatures);
        data.writeInt(this.peerFeatures);
    }

    @Override
    public int hashCode() {
        final int prime = 307;
        int result = 1;
        result = prime * result + advertisedFeatures;
        result = prime * result + config;
        result = prime * result + currentFeatures;
        result = prime * result + Arrays.hashCode(hardwareAddress);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + peerFeatures;
        result = prime * result + portNumber;
        result = prime * result + state;
        result = prime * result + supportedFeatures;
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
        if (!(obj instanceof OFPhysicalPort)) {
            return false;
        }
        OFPhysicalPort other = (OFPhysicalPort) obj;
        if (advertisedFeatures != other.advertisedFeatures) {
            return false;
        }
        if (config != other.config) {
            return false;
        }
        if (currentFeatures != other.currentFeatures) {
            return false;
        }
        if (!Arrays.equals(hardwareAddress, other.hardwareAddress)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (peerFeatures != other.peerFeatures) {
            return false;
        }
        if (portNumber != other.portNumber) {
            return false;
        }
        if (state != other.state) {
            return false;
        }
        if (supportedFeatures != other.supportedFeatures) {
            return false;
        }
        return true;
    }
}
