package net.floodlightcontroller.packet.gtp;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.PacketParsingException;

public abstract class AbstractGTP extends BasePacket {
	
	public static final byte GTP_VERSION_SHIFT = 5;
	public static final byte GTP_VERSION_MASK = 0x7;
	private boolean controlPacket;

    public static Map<Byte, Class<? extends IGTPHeader>> decodeMap;
    static {
        decodeMap = new HashMap<Byte, Class<? extends IGTPHeader>>();
        /*
         * Disable DHCP until the deserialize code is hardened to deal with garbage input
         */
        AbstractGTP.decodeMap.put((byte)1, GTPHeaderV1.class);
        AbstractGTP.decodeMap.put((byte)2, GTPHeaderV2.class);
    }
    
    
	private IGTPHeader header;

	public byte getVersion(){
		return this.header.getVersion();
	}

	@Override
	public byte[] serialize() {
		if(this.header == null){
			throw new RuntimeException("Malformed GTP Packet, no header info. Something really wrong happened.");
		}
		
		byte[] payloadData = null;
		if (payload != null) {
			payload.setParent(this);
			payloadData = payload.serialize();
		}

		int headerSizeInBytes = this.header.getSizeInBytes();

		//
		int totalNumberOfBytes = headerSizeInBytes
				+ (payloadData != null ? payloadData.length : 0);
		byte[] data = new byte[totalNumberOfBytes];
		ByteBuffer bb = ByteBuffer.wrap(data);
		
		byte[] headerData = this.header.serialize();
		bb.put(headerData);

		if (payloadData != null) {
			bb.put(payloadData);
		}

		return data;
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length)
			throws PacketParsingException {

		ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

		byte scratch = bb.get();
		byte version = extractVersionFromScratch(scratch);
		
        try {
        	this.header = AbstractGTP.decodeMap.get(version).getConstructor().newInstance();
		} catch (Exception e) {
            throw new RuntimeException("Failure instantiating GTP Header class", e);
        }

        this.header.deserialize(bb, scratch);

		IPacket payload;
		if(!this.isControlPacket()){
			payload = new IPv4();
		} else {
			payload = new Data();
		}
		
		this.payload = payload.deserialize(data, bb.position(),
				bb.limit() - bb.position());
        this.payload.setParent(this);
		return this;
	}
	
	static byte extractVersionFromScratch(byte scratch) {
		byte version = (byte) ((scratch >> GTP_VERSION_SHIFT) & GTP_VERSION_MASK);
		return version;
	}

	protected void setControlPacket(boolean b) {
		this.controlPacket = b;
	}

	public boolean isControlPacket() {
		return controlPacket;
	}

	public IGTPHeader getHeader() {
		return header;
	}

	public AbstractGTP setHeader(IGTPHeader header) {
		this.header = header;
		return this;
	}
	
    @Override
    public IPacket setPayload(IPacket payload) {
        if(this.header == null){
			throw new RuntimeException("Setting payload in a malformed GTP Packet, no header info. Something really wrong happened.");
		}
        
        this.header.updateLength(this.payload, payload);
        this.payload = payload;

        return this;
    }
	
}
