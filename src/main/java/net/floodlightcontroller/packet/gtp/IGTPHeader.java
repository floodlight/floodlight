package net.floodlightcontroller.packet.gtp;

import java.nio.ByteBuffer;

import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.PacketParsingException;

public interface IGTPHeader {
	
	/**
	 * Returns the GTP version of the header represented by this object represents. 
	 * @return the GTP version of the header represented by this object represents
	 */
	public byte getVersion();
	
	public byte[] serialize();

	public IGTPHeader deserialize(ByteBuffer bb, byte scratch) throws PacketParsingException;

	/**
	 * Returns the size in number of bytes of this headers. This size include any optional information
	 * that this header carry. For instance, extension headers of GTP V1. 
	 * @return
	 */
	public int getSizeInBytes();
	
	public byte[] getNextSequenceNumber();
	
	public byte[] getSequenceNumber();
	
	public IGTPHeader setSequenceNumber(byte[] sequenceNumber);

	public void updateLength(IPacket oldPayload, IPacket newPayload);
	
}
