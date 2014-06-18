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

import net.floodlightcontroller.core.web.serializers.ByteArrayMACSerializer;
import net.floodlightcontroller.core.web.serializers.UShortSerializer;
import net.floodlightcontroller.util.EnumBitmaps;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.HexString;

/**
 * Represents ofp_phy_port
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 25, 2010
 */
public class OFPhysicalPort {
    public final static int MINIMUM_LENGTH = 48;
    public final static int OFP_ETH_ALEN = 6;

    public enum OFPortConfig implements EnumBitmaps.BitmapableEnum {
        OFPPC_PORT_DOWN    (1 << 0) {
            @Override
            public String toString() {
                return "port-down (0x1)";
            }
        },
        OFPPC_NO_STP       (1 << 1) {
            @Override
            public String toString() {
                return "no-stp (0x2)";
            }
        },
        OFPPC_NO_RECV      (1 << 2) {
            @Override
            public String toString() {
                return "no-recv (0x4)";
            }
        },
        OFPPC_NO_RECV_STP  (1 << 3) {
            @Override
            public String toString() {
                return "no-recv-stp (0x8)";
            }
        },
        OFPPC_NO_FLOOD     (1 << 4) {
            @Override
            public String toString() {
                return "no-flood (0x10)";
            }
        },
        OFPPC_NO_FWD       (1 << 5) {
            @Override
            public String toString() {
                return "no-fwd (0x20)";
            }
        },
        OFPPC_NO_PACKET_IN (1 << 6) {
            @Override
            public String toString() {
                return "no-pkt-in (0x40)";
            }
        },
        OFPPC_BSN_MIRROR_DEST (1 << 31) {
            @Override
            public String toString() {
                return "bsn-mirror-dest (0x80000000)";
            }
        };


        protected int value;

        private OFPortConfig(int value) {
            this.value = value;
        }

        /**
         * @return the value
         */
        @Override
        public int getValue() {
            return value;
        }
    }

    public enum OFPortState {
        OFPPS_LINK_DOWN   (1 << 0, false) {
            @Override
            public String toString() {
                return "link-down (0x1)";
            }
        },
        OFPPS_STP_LISTEN  (0 << 8, true) {
            @Override
            public String toString() {
                return "listen (0x0)";
            }
        },
        OFPPS_STP_LEARN   (1 << 8, true) {
            @Override
            public String toString() {
                return "learn-no-relay (0x100)";
            }
        },
        OFPPS_STP_FORWARD (2 << 8, true) {
            @Override
            public String toString() {
                return "forward (0x200)";
            }
        },
        OFPPS_STP_BLOCK   (3 << 8, true) {
            @Override
            public String toString() {
                return "block-broadcast (0x300)";
            }
        },
        OFPPS_STP_MASK    (3 << 8, false) { // used for STP but not an STP state
            @Override
            public String toString() {
                return "block-broadcast (0x300)";
            }
        };

        protected int value;
        protected boolean isStpState;

        private OFPortState(int value, boolean isStpState) {
            this.value = value;
            this.isStpState = isStpState;
        }

        /**
         * Returns true if this constants represents one of the four STP
         * states
         * @return
         */
        public boolean isStpState() {
            return isStpState;
        }

        /**
         * return the STP state represented by the given integer.
         * the returned value will have isStpState() == true
         * @param state
         * @return
         */
        public static OFPortState getStpState(int state) {
            // this ain't pretty
            state = state & OFPortState.OFPPS_STP_MASK.getValue();
            for (OFPortState s: OFPortState.values()) {
                if (!s.isStpState())
                    continue;
                if (state == s.getValue())
                    return s;
            }
            return null; // will never happen
        }

        public static boolean isPortDown(int state) {
            if ((state & OFPPS_LINK_DOWN.getValue()) != 0)
                return true;
            else
                return false;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }

    /**
     * Represents the speed of a port
     */
    public enum PortSpeed {
        /** no speed set */
        SPEED_NONE(0),
        SPEED_10MB(10),
        SPEED_100MB(100),
        SPEED_1GB(1000),
        SPEED_10GB(10000);

        private long speedInBps;
        private PortSpeed(int speedInMbps) {
            this.speedInBps = speedInMbps * 1000*1000;
        }

        public long getSpeedBps() {
            return this.speedInBps;
        }

        public static PortSpeed max(PortSpeed s1, PortSpeed s2) {
            return (s1.getSpeedBps() > s2.getSpeedBps()) ? s1 : s2;
        }

        public static PortSpeed min(PortSpeed s1, PortSpeed s2) {
            return (s1.getSpeedBps() < s2.getSpeedBps()) ? s1 : s2;
        }
    }

    public enum OFPortFeatures implements EnumBitmaps.BitmapableEnum {
        OFPPF_10MB_HD    (1 << 0, PortSpeed.SPEED_10MB) {
            @Override
            public String toString() {
                return "10mb-hd (0x1)";
            }
        },
        OFPPF_10MB_FD    (1 << 1, PortSpeed.SPEED_10MB) {
            @Override
            public String toString() {
                return "10mb-fd (0x2)";
            }
        },
        OFPPF_100MB_HD   (1 << 2, PortSpeed.SPEED_100MB) {
            @Override
            public String toString() {
                return "100mb-hd (0x4)";
            }
        },
        OFPPF_100MB_FD   (1 << 3, PortSpeed.SPEED_100MB) {
            @Override
            public String toString() {
                return "100mb-fd (0x8)";
            }
        },
        OFPPF_1GB_HD     (1 << 4, PortSpeed.SPEED_1GB) {
            @Override
            public String toString() {
                return "1gb-hd (0x10)";
            }
        },
        OFPPF_1GB_FD     (1 << 5, PortSpeed.SPEED_1GB) {
            @Override
            public String toString() {
                return "1gb-fd (0x20)";
            }
        },
        OFPPF_10GB_FD    (1 << 6, PortSpeed.SPEED_10GB) {
            @Override
            public String toString() {
                return "10gb-fd (0x40)";
            }
        },
        OFPPF_COPPER     (1 << 7, PortSpeed.SPEED_NONE) {
            @Override
            public String toString() {
                return "copper (0x80)";
            }
        },
        OFPPF_FIBER      (1 << 8, PortSpeed.SPEED_NONE) {
            @Override
            public String toString() {
                return "fiber (0x100)";
            }
        },
        OFPPF_AUTONEG    (1 << 9, PortSpeed.SPEED_NONE) {
            @Override
            public String toString() {
                return "autoneg (0x200)";
            }
        },
        OFPPF_PAUSE      (1 << 10, PortSpeed.SPEED_NONE) {
            @Override
            public String toString() {
                return "pause (0x400)";
            }
        },
        OFPPF_PAUSE_ASYM (1 << 11, PortSpeed.SPEED_NONE) {
            @Override
            public String toString() {
                return "pause-asym (0x800)";
            }
        };

        protected int value;
        protected PortSpeed speed;

        private OFPortFeatures(int value, PortSpeed speed) {
            this.value = value;
            if (speed == null)
                throw new NullPointerException();
            this.speed = speed;
        }

        /**
         * @return the bitmap value for this constant
         */
        @Override
        public int getValue() {
            return value;
        }

        /**
         * @return the port speed associated with this constant. If the
         * constant doesn't represent a port speed it returns SPEED_NONE
         */
        public PortSpeed getSpeed() {
            return speed;
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
    @JsonSerialize(using=UShortSerializer.class)
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
    @JsonSerialize(using=ByteArrayMACSerializer.class)
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

    @Override
    public String toString() {
        String linkState, linkSpeed;
        if ((state & OFPortState.OFPPS_LINK_DOWN.getValue()) != 0) {
            linkState = "down";
        } else {
            linkState = "up";
        }
        if ((currentFeatures & OFPortFeatures.OFPPF_10GB_FD.getValue()) != 0) {
            linkSpeed = "10G";
        } else if ((currentFeatures & OFPortFeatures.OFPPF_1GB_FD.getValue()) != 0) {
            linkSpeed = "1G";
        } else if ((currentFeatures & OFPortFeatures.OFPPF_1GB_HD.getValue()) != 0) {
            linkSpeed = "1G(half-duplex)";
        } else if ((currentFeatures & OFPortFeatures.OFPPF_100MB_FD.getValue()) != 0) {
            linkSpeed = "100M";
        } else if ((currentFeatures & OFPortFeatures.OFPPF_100MB_HD.getValue()) != 0) {
            linkSpeed = "100M(half-duplex)";
        } else if ((currentFeatures & OFPortFeatures.OFPPF_10MB_FD.getValue()) != 0) {
            linkSpeed = "10M";
        } else if ((currentFeatures & OFPortFeatures.OFPPF_10MB_HD.getValue()) != 0) {
            linkSpeed = "10M(half-duplex)";
        } else {
            linkSpeed = "unknown";
        }
        return "port " + name + " (" +  portNumber + ")" +
               ", mac " + HexString.toHexString(hardwareAddress) +
               ", state " + linkState +
               ", speed " + linkSpeed;
    }
}
