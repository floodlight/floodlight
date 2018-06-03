package net.floodlightcontroller.packet.gtp;

import net.floodlightcontroller.packet.IPacket;


public abstract class AbstractGTPHeader implements IGTPHeader {
	
	public static final short GTP_FLAG_MASK = (1 << AbstractGTP.GTP_VERSION_SHIFT) - 1;
	protected byte version;	
	protected byte[] sequenceNumber;
	protected short totalLength;

	
	protected byte[] createHeaderDataArray() {
		byte[] data = new byte[getSizeInBytes()];
		return data;
	}

	@Override
	public byte getVersion() {
		return this.version;
	}

	public abstract int getSizeInBytes();
	
	public abstract byte[] getNextSequenceNumber();
	
	@Override
	public IGTPHeader setSequenceNumber(byte[] sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
		return this;
	}
	
	@Override
	public void updateLength(IPacket oldPayload, IPacket newPayload) {
		// TODO review this!
		// Seems to be this fixed size but this may be excluding ext headers
		this.totalLength = (short)(4 + newPayload.serialize().length);
	}
}
