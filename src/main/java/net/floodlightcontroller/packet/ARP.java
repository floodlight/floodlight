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

import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class ARP extends BasePacket {
	public static short HW_TYPE_ETHERNET = 0x1;

	public static short PROTO_TYPE_IP = 0x800;

	public static ArpOpcode OP_REQUEST = ArpOpcode.REQUEST;
	public static ArpOpcode OP_REPLY = ArpOpcode.REPLY;
	public static ArpOpcode OP_RARP_REQUEST = ArpOpcode.REQUEST_REVERSE;
	public static ArpOpcode OP_RARP_REPLY = ArpOpcode.REPLY_REVERSE;

	protected short hardwareType;
	protected short protocolType;
	protected byte hardwareAddressLength;
	protected byte protocolAddressLength;
	protected ArpOpcode opCode;
	protected MacAddress senderHardwareAddress;
	protected IPv4Address senderProtocolAddress;
	protected MacAddress targetHardwareAddress;
	protected IPv4Address targetProtocolAddress;

	/**
	 * @return the hardwareType
	 */
	public short getHardwareType() {
		return hardwareType;
	}

	/**
	 * @param hardwareType the hardwareType to set
	 */
	public ARP setHardwareType(short hardwareType) {
		this.hardwareType = hardwareType;
		return this;
	}

	/**
	 * @return the protocolType
	 */
	public short getProtocolType() {
		return protocolType;
	}

	/**
	 * @param protocolType the protocolType to set
	 */
	public ARP setProtocolType(short protocolType) {
		this.protocolType = protocolType;
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
	public ARP setHardwareAddressLength(byte hardwareAddressLength) {
		this.hardwareAddressLength = hardwareAddressLength;
		return this;
	}

	/**
	 * @return the protocolAddressLength
	 */
	public byte getProtocolAddressLength() {
		return protocolAddressLength;
	}

	/**
	 * @param protocolAddressLength the protocolAddressLength to set
	 */
	public ARP setProtocolAddressLength(byte protocolAddressLength) {
		this.protocolAddressLength = protocolAddressLength;
		return this;
	}

	/**
	 * @return the opCode
	 */
	public ArpOpcode getOpCode() {
		return opCode;
	}

	/**
	 * @param opCode the opCode to set
	 */
	public ARP setOpCode(ArpOpcode opCode) {
		this.opCode = opCode;
		return this;
	}

	/**
	 * @return the senderHardwareAddress
	 */
	public MacAddress getSenderHardwareAddress() {
		return senderHardwareAddress;
	}

	/**
	 * @param senderHardwareAddress the senderHardwareAddress to set
	 */
	public ARP setSenderHardwareAddress(MacAddress senderHardwareAddress) {
		this.senderHardwareAddress = senderHardwareAddress;
		return this;
	}

	/**
	 * @return the senderProtocolAddress
	 */
	public IPv4Address getSenderProtocolAddress() {
		return senderProtocolAddress;
	}

	/**
	 * @param senderProtocolAddress the senderProtocolAddress to set
	 */
	public ARP setSenderProtocolAddress(IPv4Address senderProtocolAddress) {
		this.senderProtocolAddress = senderProtocolAddress;
		return this;
	}

	/**
	 * @return the targetHardwareAddress
	 */
	public MacAddress getTargetHardwareAddress() {
		return targetHardwareAddress;
	}

	/**
	 * @param targetHardwareAddress the targetHardwareAddress to set
	 */
	public ARP setTargetHardwareAddress(MacAddress targetHardwareAddress) {
		this.targetHardwareAddress = targetHardwareAddress;
		return this;
	}

	/**
	 * @return the targetProtocolAddress
	 */
	public IPv4Address getTargetProtocolAddress() {
		return targetProtocolAddress;
	}

	/**
	 * @return True if gratuitous ARP (SPA = TPA), false otherwise
	 */
	public boolean isGratuitous() {        
		assert(senderProtocolAddress.getLength() == targetProtocolAddress.getLength());

		return senderProtocolAddress.equals(targetProtocolAddress);
	}

	/**
	 * @param targetProtocolAddress the targetProtocolAddress to set
	 */
	public ARP setTargetProtocolAddress(IPv4Address targetProtocolAddress) {
		this.targetProtocolAddress = targetProtocolAddress;
		return this;
	}

	@Override
	public byte[] serialize() {
		int length = 8 + (2 * (0xff & this.hardwareAddressLength))
				+ (2 * (0xff & this.protocolAddressLength));
		byte[] data = new byte[length];
		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.putShort(this.hardwareType);
		bb.putShort(this.protocolType);
		bb.put(this.hardwareAddressLength);
		bb.put(this.protocolAddressLength);
		bb.putShort((short) this.opCode.getOpcode());
		bb.put(this.senderHardwareAddress.getBytes());
		bb.put(this.senderProtocolAddress.getBytes());
		bb.put(this.targetHardwareAddress.getBytes());
		bb.put(this.targetProtocolAddress.getBytes());
		return data;
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length)
			throws PacketParsingException {
		ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
		this.hardwareType = bb.getShort();
		this.protocolType = bb.getShort();
		this.hardwareAddressLength = bb.get();
		this.protocolAddressLength = bb.get();
		if (this.hardwareAddressLength != 6) {
			String msg = "Incorrect ARP hardware address length: " +
					hardwareAddressLength;
			throw new PacketParsingException(msg);
		}
		if (this.protocolAddressLength != 4) {
			String msg = "Incorrect ARP protocol address length: " +
					protocolAddressLength;
			throw new PacketParsingException(msg);
		}
		this.opCode = ArpOpcode.of(bb.getShort());

		byte[] tmpMac = new byte[0xff & this.hardwareAddressLength];
		byte[] tmpIp = new byte[0xff & this.protocolAddressLength];

		bb.get(tmpMac, 0, this.hardwareAddressLength);
		this.senderHardwareAddress = MacAddress.of(tmpMac);  
		bb.get(tmpIp, 0, this.protocolAddressLength);
		this.senderProtocolAddress = IPv4Address.of(tmpIp);

		bb.get(tmpMac, 0, this.hardwareAddressLength);
		this.targetHardwareAddress = MacAddress.of(tmpMac);  
		bb.get(tmpIp, 0, this.protocolAddressLength);
		this.targetProtocolAddress = IPv4Address.of(tmpIp);

		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + hardwareAddressLength;
		result = prime * result + hardwareType;
		result = prime * result + ((opCode == null) ? 0 : opCode.hashCode());
		result = prime * result + protocolAddressLength;
		result = prime * result + protocolType;
		result = prime
				* result
				+ ((senderHardwareAddress == null) ? 0 : senderHardwareAddress
						.hashCode());
		result = prime
				* result
				+ ((senderProtocolAddress == null) ? 0 : senderProtocolAddress
						.hashCode());
		result = prime
				* result
				+ ((targetHardwareAddress == null) ? 0 : targetHardwareAddress
						.hashCode());
		result = prime
				* result
				+ ((targetProtocolAddress == null) ? 0 : targetProtocolAddress
						.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ARP other = (ARP) obj;
		if (hardwareAddressLength != other.hardwareAddressLength)
			return false;
		if (hardwareType != other.hardwareType)
			return false;
		if (opCode == null) {
			if (other.opCode != null)
				return false;
		} else if (!opCode.equals(other.opCode))
			return false;
		if (protocolAddressLength != other.protocolAddressLength)
			return false;
		if (protocolType != other.protocolType)
			return false;
		if (senderHardwareAddress == null) {
			if (other.senderHardwareAddress != null)
				return false;
		} else if (!senderHardwareAddress.equals(other.senderHardwareAddress))
			return false;
		if (senderProtocolAddress == null) {
			if (other.senderProtocolAddress != null)
				return false;
		} else if (!senderProtocolAddress.equals(other.senderProtocolAddress))
			return false;
		if (targetHardwareAddress == null) {
			if (other.targetHardwareAddress != null)
				return false;
		} else if (!targetHardwareAddress.equals(other.targetHardwareAddress))
			return false;
		if (targetProtocolAddress == null) {
			if (other.targetProtocolAddress != null)
				return false;
		} else if (!targetProtocolAddress.equals(other.targetProtocolAddress))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ARP [hardwareType=" + hardwareType + ", protocolType="
				+ protocolType + ", hardwareAddressLength="
				+ hardwareAddressLength + ", protocolAddressLength="
				+ protocolAddressLength + ", opCode=" + opCode
				+ ", senderHardwareAddress=" + senderHardwareAddress
				+ ", senderProtocolAddress=" + senderProtocolAddress
				+ ", targetHardwareAddress=" + targetHardwareAddress
				+ ", targetProtocolAddress=" + targetProtocolAddress + "]";
	}

}
