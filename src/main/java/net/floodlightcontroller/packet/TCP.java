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

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.TransportPort;

/**
 *
 * @author shudong.zhou@bigswitch.com
 */
public class TCP extends BasePacket {
    protected TransportPort sourcePort;
    protected TransportPort destinationPort;
    protected int sequence;
    protected int acknowledge;
    protected byte dataOffset;
    protected short flags;
    protected short windowSize;
    protected short checksum;
    protected short urgentPointer;
    protected byte[] options;

    /**
     * @return the sourcePort
     */
    public TransportPort getSourcePort() {
        return sourcePort;
    }

    /**
     * @param sourcePort the sourcePort to set
     */
    public TCP setSourcePort(TransportPort sourcePort) {
        this.sourcePort = sourcePort;
        return this;
    }
    
    /**
     * @param sourcePort the sourcePort to set
     */
    public TCP setSourcePort(int sourcePort) {
        this.sourcePort = TransportPort.of(sourcePort);
        return this;
    }

    /**
     * @return the destinationPort
     */
    public TransportPort getDestinationPort() {
        return destinationPort;
    }

    /**
     * @param destinationPort the destinationPort to set
     */
    public TCP setDestinationPort(TransportPort destinationPort) {
        this.destinationPort = destinationPort;
        return this;
    }
    
    /**
     * @param destinationPort the destinationPort to set
     */
    public TCP setDestinationPort(int destinationPort) {
        this.destinationPort = TransportPort.of(destinationPort);
        return this;
    }

    /**
     * @return the checksum
     */
    public short getChecksum() {
        return checksum;
    }
    
    public int getSequence() {
        return this.sequence;
    }
    public TCP setSequence(int seq) {
        this.sequence = seq;
        return this;
    }
    public int getAcknowledge() {
        return this.acknowledge;
    }
    public TCP setAcknowledge(int ack) {
        this.acknowledge = ack;
        return this;
    }
    public byte getDataOffset() {
        return this.dataOffset;
    }
    public TCP setDataOffset(byte offset) {
        this.dataOffset = offset;
        return this;
    }
    public short getFlags() {
        return this.flags;
    }
    public TCP setFlags(short flags) {
        this.flags = flags;
        return this;
    }
    public short getWindowSize() {
        return this.windowSize;
    }
    public TCP setWindowSize(short windowSize) {
        this.windowSize = windowSize;
        return this;
    }
    public short getTcpChecksum() {
        return this.checksum;
    }
    public TCP setTcpChecksum(short checksum) {
        this.checksum = checksum;
        return this;
    }
    
    @Override
    public void resetChecksum() {
        this.checksum = 0;
        super.resetChecksum();
    }
    
    public short getUrgentPointer(short urgentPointer) {
        return this.urgentPointer;
    }
    public TCP setUrgentPointer(short urgentPointer) {
        this.urgentPointer= urgentPointer;
        return this;
    }
    public byte[] getOptions() {
        return this.options;
    }
    public TCP setOptions(byte[] options) {
        this.options = options;
        this.dataOffset = (byte) ((20 + options.length + 3) >> 2);
        return this;
    }
    /**
     * @param checksum the checksum to set
     */
    public TCP setChecksum(short checksum) {
        this.checksum = checksum;
        return this;
    }

    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     *      -length : 0
     */
    public byte[] serialize() {
        int length;
        if (dataOffset == 0)
            dataOffset = 5;  // default header length
        length = dataOffset << 2;
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
            length += payloadData.length;
        }

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.putShort((short)this.sourcePort.getPort()); //TCP ports are defined to be 16 bits
        bb.putShort((short)this.destinationPort.getPort());
        bb.putInt(this.sequence);
        bb.putInt(this.acknowledge);
        bb.putShort((short) (this.flags | (dataOffset << 12)));
        bb.putShort(this.windowSize);
        bb.putShort(this.checksum);
        bb.putShort(this.urgentPointer);
        if (dataOffset > 5) {
            int padding;
            bb.put(options);
            padding = (dataOffset << 2) - 20 - options.length;
            for (int i = 0; i < padding; i++)
                bb.put((byte) 0);
        }
        if (payloadData != null)
            bb.put(payloadData);

        if (this.parent != null && this.parent instanceof IPv4)
            ((IPv4)this.parent).setProtocol(IpProtocol.TCP);

        // compute checksum if needed
		if (this.checksum == 0) {

			if (this.parent != null && this.parent instanceof IPv4) {
			    //Checksum calculation based on the JSocket Wrench code
				// https://github.com/ehrmann/jswrench
				//The original code can be found at 
				//https://github.com/ehrmann/jswrench/blob/master/src/com/act365/net/SocketUtils.java
				IPv4 ipv4 = (IPv4) this.parent;

				int bufferlength = length + 12;
				boolean odd = length % 2 == 1;
				byte[] source = ipv4.getSourceAddress().getBytes();
				byte[] destination = ipv4.getDestinationAddress().getBytes();

				if (odd) {
					++bufferlength;
				}

				byte[] buffer = new byte[bufferlength];

				buffer[0] = source[0];
				buffer[1] = source[1];
				buffer[2] = source[2];
				buffer[3] = source[3];

				buffer[4] = destination[0];
				buffer[5] = destination[1];
				buffer[6] = destination[2];
				buffer[7] = destination[3];

				buffer[8] = (byte) 0;
				buffer[9] = (byte) ipv4.getProtocol().getIpProtocolNumber();

				shortToBytes((short) length, buffer, 10);

				int i = 11;

				while (++i < length + 12) {
					buffer[i] = data[i + 0 - 12];
				}

				if (odd) {
					buffer[i] = (byte) 0;
				}

				this.checksum = checksum(buffer, buffer.length, 0);
			} else {
				bb.rewind();
				int accumulation = 0;
				for (int i = 0; i < length / 2; ++i) {
					accumulation += 0xffff & bb.getShort();
				}
				// pad to an even number of shorts
				if (length % 2 > 0) {
					accumulation += (bb.get() & 0xff) << 8;
				}

				accumulation = ((accumulation >> 16) & 0xffff)
						+ (accumulation & 0xffff);
				this.checksum = (short) (~accumulation & 0xffff);
			}

			bb.putShort(16, this.checksum);
		}
        
        return data;
    }
    
    //Checksum calculation based on the JSocket Wrench code
	// https://github.com/ehrmann/jswrench
	//The original code can be found at 
	//https://github.com/ehrmann/jswrench/blob/master/src/com/act365/net/SocketUtils.java
	private static long integralFromBytes(byte[] buffer, int offset, int length) {

		long answer = 0;

		while (--length >= 0) {
			answer = answer << 8;
			answer |= buffer[offset] >= 0 ? buffer[offset]
					: 0xffffff00 ^ buffer[offset];
			++offset;
		}

		return answer;
	}
    
    //Checksum calculation based on the JSocket Wrench code
	// https://github.com/ehrmann/jswrench
	//The original code can be found at 
	//https://github.com/ehrmann/jswrench/blob/master/src/com/act365/net/SocketUtils.java
	private static void shortToBytes(short value, byte[] buffer, int offset) {
		buffer[offset + 1] = (byte) (value & 0xff);
		value = (short) (value >> 8);
		buffer[offset] = (byte) (value);
	}
    
    //Checksum calculation based on the JSocket Wrench code
	// https://github.com/ehrmann/jswrench
	//The original code can be found at 
	//https://github.com/ehrmann/jswrench/blob/master/src/com/act365/net/SocketUtils.java
	private static short checksum(byte[] message, int length, int offset) {
		// Sum consecutive 16-bit words.

		int sum = 0;

		while (offset < length - 1) {

			sum += (int) integralFromBytes(message, offset, 2);

			offset += 2;
		}

		if (offset == length - 1) {

			sum += (message[offset] >= 0 ? message[offset]
					: message[offset] ^ 0xffffff00) << 8;
		}

		// Add upper 16 bits to lower 16 bits.

		sum = (sum >>> 16) + (sum & 0xffff);

		// Add carry

		sum += sum >>> 16;

		// Ones complement and truncate.

		return (short) ~sum;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5807;
        int result = super.hashCode();
        result = prime * result + checksum;
        result = prime * result + destinationPort.getPort();
        result = prime * result + sourcePort.getPort();
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof TCP))
            return false;
        TCP other = (TCP) obj;
        // May want to compare fields based on the flags set
        return (checksum == other.checksum) &&
               (destinationPort.equals(other.destinationPort)) &&
               (sourcePort.equals(other.sourcePort)) &&
               (sequence == other.sequence) &&
               (acknowledge == other.acknowledge) &&
               (dataOffset == other.dataOffset) &&
               (flags == other.flags) &&
               (windowSize == other.windowSize) &&
               (urgentPointer == other.urgentPointer) &&
               (dataOffset == 5 || Arrays.equals(options,other.options));
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length)
            throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        this.sourcePort = TransportPort.of((int) (bb.getShort() & 0xffff)); // short will be signed, pos or neg
        this.destinationPort = TransportPort.of((int) (bb.getShort() & 0xffff)); // convert range 0 to 65534, not -32768 to 32767
        this.sequence = bb.getInt();
        this.acknowledge = bb.getInt();
        this.flags = bb.getShort();
        this.dataOffset = (byte) ((this.flags >> 12) & 0xf);
        if (this.dataOffset < 5) {
            throw new PacketParsingException("Invalid tcp header length < 20");
        }
        this.flags = (short) (this.flags & 0x1ff);
        this.windowSize = bb.getShort();
        this.checksum = bb.getShort();
        this.urgentPointer = bb.getShort();
        if (this.dataOffset > 5) {
            int optLength = (dataOffset << 2) - 20;
            if (bb.limit() < bb.position()+optLength) {
                optLength = bb.limit() - bb.position();
            }
            try {
                this.options = new byte[optLength];
                bb.get(this.options, 0, optLength);
            } catch (IndexOutOfBoundsException e) {
                this.options = null;
            }
        }

        this.payload = new Data();
        int remLength = bb.limit()-bb.position();
        this.payload = payload.deserialize(data, bb.position(), remLength);
        this.payload.setParent(this);
        return this;
    }
}
