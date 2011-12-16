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

package net.floodlightcontroller.packet;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class DHCP extends BasePacket {
    /**
     * ------------------------------------------
     * |op (1)  | htype(1) | hlen(1) | hops(1)  |
     * ------------------------------------------
     * |        xid (4)                         |
     * ------------------------------------------
     * |  secs (2)          |   flags (2)       |
     * ------------------------------------------
     * |            ciaddr (4)                  |
     * ------------------------------------------
     * |            yiaddr (4)                  |
     * ------------------------------------------
     * |            siaddr (4)                  |
     * ------------------------------------------
     * |            giaddr (4)                  |
     * ------------------------------------------
     * |            chaddr (16)                  |
     * ------------------------------------------
     * |            sname (64)                  |
     * ------------------------------------------
     * |            file (128)                  |
     * ------------------------------------------
     * |            options (312)               |
     * ------------------------------------------
     * 
     */
    // Header + magic without options
    public static int MIN_HEADER_LENGTH = 240;
    public static byte OPCODE_REQUEST = 0x1;
    public static byte OPCODE_REPLY = 0x2;

    public static byte HWTYPE_ETHERNET = 0x1;
    
    public enum DHCPOptionCode {
        OptionCode_SubnetMask           ((byte)1),
        OptionCode_RequestedIP          ((byte)50),
        OptionCode_LeaseTime            ((byte)51),
        OptionCode_MessageType          ((byte)53),
        OptionCode_DHCPServerIp         ((byte)54),
        OptionCode_RequestedParameters  ((byte)55),
        OptionCode_RenewalTime          ((byte)58),
        OPtionCode_RebindingTime        ((byte)59),
        OptionCode_ClientID             ((byte)61),
        OptionCode_END                  ((byte)255);
    
        protected byte value;
        
        private DHCPOptionCode(byte value) {
            this.value = value;
        }
        
        public byte getValue() {
            return value;
        }
    }
    
    protected byte opCode;
    protected byte hardwareType;
    protected byte hardwareAddressLength;
    protected byte hops;
    protected int transactionId;
    protected short seconds;
    protected short flags;
    protected int clientIPAddress;
    protected int yourIPAddress;
    protected int serverIPAddress;
    protected int gatewayIPAddress;
    protected byte[] clientHardwareAddress;
    protected String serverName;
    protected String bootFileName;
    protected List<DHCPOption> options = new ArrayList<DHCPOption>();

    /**
     * @return the opCode
     */
    public byte getOpCode() {
        return opCode;
    }

    /**
     * @param opCode the opCode to set
     */
    public DHCP setOpCode(byte opCode) {
        this.opCode = opCode;
        return this;
    }

    /**
     * @return the hardwareType
     */
    public byte getHardwareType() {
        return hardwareType;
    }

    /**
     * @param hardwareType the hardwareType to set
     */
    public DHCP setHardwareType(byte hardwareType) {
        this.hardwareType = hardwareType;
        return this;
    }

    /**
     * @return the hardwareAddressLength
     */
    public byte getHardwareAddressLength() {
        return hardwareAddressLength;
    }

    /**
     * @param hardwareAddressLength the hardwareAddressLength to set
     */
    public DHCP setHardwareAddressLength(byte hardwareAddressLength) {
        this.hardwareAddressLength = hardwareAddressLength;
        return this;
    }

    /**
     * @return the hops
     */
    public byte getHops() {
        return hops;
    }

    /**
     * @param hops the hops to set
     */
    public DHCP setHops(byte hops) {
        this.hops = hops;
        return this;
    }

    /**
     * @return the transactionId
     */
    public int getTransactionId() {
        return transactionId;
    }

    /**
     * @param transactionId the transactionId to set
     */
    public DHCP setTransactionId(int transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    /**
     * @return the seconds
     */
    public short getSeconds() {
        return seconds;
    }

    /**
     * @param seconds the seconds to set
     */
    public DHCP setSeconds(short seconds) {
        this.seconds = seconds;
        return this;
    }

    /**
     * @return the flags
     */
    public short getFlags() {
        return flags;
    }

    /**
     * @param flags the flags to set
     */
    public DHCP setFlags(short flags) {
        this.flags = flags;
        return this;
    }

    /**
     * @return the clientIPAddress
     */
    public int getClientIPAddress() {
        return clientIPAddress;
    }

    /**
     * @param clientIPAddress the clientIPAddress to set
     */
    public DHCP setClientIPAddress(int clientIPAddress) {
        this.clientIPAddress = clientIPAddress;
        return this;
    }

    /**
     * @return the yourIPAddress
     */
    public int getYourIPAddress() {
        return yourIPAddress;
    }

    /**
     * @param yourIPAddress the yourIPAddress to set
     */
    public DHCP setYourIPAddress(int yourIPAddress) {
        this.yourIPAddress = yourIPAddress;
        return this;
    }

    /**
     * @return the serverIPAddress
     */
    public int getServerIPAddress() {
        return serverIPAddress;
    }

    /**
     * @param serverIPAddress the serverIPAddress to set
     */
    public DHCP setServerIPAddress(int serverIPAddress) {
        this.serverIPAddress = serverIPAddress;
        return this;
    }

    /**
     * @return the gatewayIPAddress
     */
    public int getGatewayIPAddress() {
        return gatewayIPAddress;
    }

    /**
     * @param gatewayIPAddress the gatewayIPAddress to set
     */
    public DHCP setGatewayIPAddress(int gatewayIPAddress) {
        this.gatewayIPAddress = gatewayIPAddress;
        return this;
    }

    /**
     * @return the clientHardwareAddress
     */
    public byte[] getClientHardwareAddress() {
        return clientHardwareAddress;
    }

    /**
     * @param clientHardwareAddress the clientHardwareAddress to set
     */
    public DHCP setClientHardwareAddress(byte[] clientHardwareAddress) {
        this.clientHardwareAddress = clientHardwareAddress;
        return this;
    }
    
    /**
     * Gets a specific DHCP option parameter
     * @param opetionCode The option code to get
     * @return The value of the option if it exists, null otherwise
     */
    public DHCPOption getOption(DHCPOptionCode optionCode) {
        for (DHCPOption opt : options) {
            if (opt.code == optionCode.value)
                return opt;
        }
        return null;
    }

    /**
     * @return the options
     */
    public List<DHCPOption> getOptions() {
        return options;
    }

    /**
     * @param options the options to set
     */
    public DHCP setOptions(List<DHCPOption> options) {
        this.options = options;
        return this;
    }

    /**
     * @return the packetType base on option 53
     */
    public DHCPPacketType getPacketType() {
        ListIterator<DHCPOption> lit = options.listIterator();
        while (lit.hasNext()) {
            DHCPOption option = lit.next();
            // only care option 53
            if (option.getCode() == 53) {
                return DHCPPacketType.getType(option.getData()[0]);
            }
        }
        return null;
    }
    
    /**
     * @return the serverName
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * @param serverName the serverName to set
     */
    public DHCP setServerName(String serverName) {
        this.serverName = serverName;
        return this;
    }

    /**
     * @return the bootFileName
     */
    public String getBootFileName() {
        return bootFileName;
    }

    /**
     * @param bootFileName the bootFileName to set
     */
    public DHCP setBootFileName(String bootFileName) {
        this.bootFileName = bootFileName;
        return this;
    }

    @Override
    public byte[] serialize() {
        // minimum size 240 including magic cookie, options generally padded to 300
        int optionsLength = 0;
        for (DHCPOption option : this.options) {
            if (option.getCode() == 0 || option.getCode() == 255) {
                optionsLength += 1;
            } else {
                optionsLength += 2 + (int)(0xff & option.getLength());
            }
        }
        int optionsPadLength = 0;
        if (optionsLength < 60)
            optionsPadLength = 60 - optionsLength;

        byte[] data = new byte[240+optionsLength+optionsPadLength];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(this.opCode);
        bb.put(this.hardwareType);
        bb.put(this.hardwareAddressLength);
        bb.put(this.hops);
        bb.putInt(this.transactionId);
        bb.putShort(this.seconds);
        bb.putShort(this.flags);
        bb.putInt(this.clientIPAddress);
        bb.putInt(this.yourIPAddress);
        bb.putInt(this.serverIPAddress);
        bb.putInt(this.gatewayIPAddress);
        bb.put(this.clientHardwareAddress);
        if (this.clientHardwareAddress.length < 16) {
            for (int i = 0; i < (16 - this.clientHardwareAddress.length); ++i) {
                bb.put((byte) 0x0);
            }
        }
        writeString(this.serverName, bb, 64);
        writeString(this.bootFileName, bb, 128);
        // magic cookie
        bb.put((byte) 0x63);
        bb.put((byte) 0x82);
        bb.put((byte) 0x53);
        bb.put((byte) 0x63);
        for (DHCPOption option : this.options) {
            int code = option.getCode() & 0xff;
            bb.put((byte) code);
            if ((code != 0) && (code != 255)) {
                bb.put(option.getLength());
                bb.put(option.getData());
            }
        }
        // assume the rest is padded out with zeroes
        return data;
    }

    protected void writeString(String string, ByteBuffer bb, int maxLength) {
        if (string == null) {
            for (int i = 0; i < maxLength; ++i) {
                bb.put((byte) 0x0);
            }
        } else {
            byte[] bytes = null;
            try {
                 bytes = string.getBytes("ascii");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Failure encoding server name", e);
            }
            int writeLength = bytes.length;
            if (writeLength > maxLength) {
                writeLength = maxLength;
            }
            bb.put(bytes, 0, writeLength);
            for (int i = writeLength; i < maxLength; ++i) {
                bb.put((byte) 0x0);
            }
        }
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        if (bb.remaining() < MIN_HEADER_LENGTH) {
            return this;
        }
        
        this.opCode = bb.get();
        this.hardwareType = bb.get();
        this.hardwareAddressLength = bb.get();
        this.hops = bb.get();
        this.transactionId = bb.getInt();
        this.seconds = bb.getShort();
        this.flags = bb.getShort();
        this.clientIPAddress = bb.getInt();
        this.yourIPAddress = bb.getInt();
        this.serverIPAddress = bb.getInt();
        this.gatewayIPAddress = bb.getInt();
        int hardwareAddressLength = 0xff & this.hardwareAddressLength;
        this.clientHardwareAddress = new byte[hardwareAddressLength];

        bb.get(this.clientHardwareAddress);
        for (int i = hardwareAddressLength; i < 16; ++i)
            bb.get();
        this.serverName = readString(bb, 64);
        this.bootFileName = readString(bb, 128);
        // read the magic cookie
        // magic cookie
        bb.get();
        bb.get();
        bb.get();
        bb.get();
        // read options
        while (bb.hasRemaining()) {
            DHCPOption option = new DHCPOption();
            int code = 0xff & bb.get(); // convert signed byte to int in range [0,255]
            option.setCode((byte) code);
            if (code == 0) {
                // skip these
                continue;
            } else if (code != 255) {
                if (bb.hasRemaining()) {
                    int l = 0xff & bb.get(); // convert signed byte to int in range [0,255]
                    option.setLength((byte) l);
                    if (bb.remaining() >= l) {
                        byte[] optionData = new byte[l];
                        bb.get(optionData);
                        option.setData(optionData);
                    } else {
                        // Skip the invalid option and set the END option
                        code = 0xff;
                        option.setCode((byte)code);
                        option.setLength((byte) 0);
                    }
                } else {
                    // Skip the invalid option and set the END option
                    code = 0xff;
                    option.setCode((byte)code);
                    option.setLength((byte) 0);
                }
            }
            this.options.add(option);
            if (code == 255) {
                // remaining bytes are supposed to be 0, but ignore them just in case
                break;
            }
        }

        return this;
    }

    protected String readString(ByteBuffer bb, int maxLength) {
        byte[] bytes = new byte[maxLength];
        bb.get(bytes);
        String result = null;
        try {
            result = new String(bytes, "ascii").trim();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failure decoding string", e);
        }
        return result;
    }
}
