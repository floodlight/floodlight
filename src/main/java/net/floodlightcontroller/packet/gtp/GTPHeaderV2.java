package net.floodlightcontroller.packet.gtp;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class GTPHeaderV2 extends AbstractGTPHeader {
	
    private static final byte[] SIZE1_ZERO_BYTE_ARRAY = { 0x00 };
	/**
	 * 
	 * GTPv2 as seen in 3GPP TS 29.274 V13.2.0 (2015-06)
	 * 
     * ------------------------------------------
     * |        Version (3)         |   P (1)   |
     * ------------------------------------------
     * |  T  (1)  |          Spare (3) [*]      | 
     * ------------------------------------------
     * |           Message Type (4)             |
     * ------------------------------------------
     * |            Length 1st (4)              |
     * ------------------------------------------
     * |            Length 2nd (4)              |
     * ------------------------------------------
     * | Tunnel Endpoint Identifier 1st (4) [1] |
     * ------------------------------------------
     * | Tunnel Endpoint Identifier 2nd (4) [1] |
     * ------------------------------------------
     * | Tunnel Endpoint Identifier 3rd (4) [1] |
     * ------------------------------------------
     * | Tunnel Endpoint Identifier 4th (4) [1] |
     * ------------------------------------------
     * |       Sequence Number 1st (4)          |
     * ------------------------------------------
     * |       Sequence Number 2nd (4)          |
     * ------------------------------------------
     * |       Sequence Number 3rd (4)          |
     * ------------------------------------------
     * |             Spare (4) [*]              |
     * ------------------------------------------
	 * 
 	 * NOTE 0:	[*] Spare bits.  The sender shall set them to "0" and the receiving entity shall ignore them.
	 * NOTE 1:	[1] This field will only exist and therefore shall only be evaluated when indicated by the T flag set to 1.
	 * 
     */
	
	//GTPv2 flags
	private int spareFlag;
	private boolean teidFlag;
	private boolean piggyBackingFlag;
	private byte messageType;
	private int teid;
	private byte spare;
	
	public GTPHeaderV2(){
		this.version = 2;
	}

	@Override
	public byte[] serialize() {
		byte[] data = createHeaderDataArray();
		
		ByteBuffer bb = ByteBuffer.wrap(data);
		byte flags = (byte) ((this.version << AbstractGTP.GTP_VERSION_SHIFT)
				+ (this.piggyBackingFlag ? 16 : 0) + (this.teidFlag ? 8 : 0)
				+ (this.spareFlag));
		
		bb.put(flags);
		bb.put(this.messageType);
		bb.putShort(this.totalLength);
		if(this.teidFlag){
			bb.putInt(this.teid);
		}
		
		for(int i=0; i<3;i++){
			bb.put(this.sequenceNumber[i]);	
		}
		bb.put(this.spare);
		
		return data;
	}

	@Override
	public IGTPHeader deserialize(ByteBuffer bb, byte scratch) {
		byte version = AbstractGTP.extractVersionFromScratch(scratch);
		
		if(version != this.version){
			throw new RuntimeException("Expected version was "+this.version+". Wrong deserialization of the packet on parent.");
		}
		
		byte flags = (byte) (scratch & GTP_FLAG_MASK);

		this.piggyBackingFlag = ((flags & 16) != 0);
		this.teidFlag = ((flags & 8) != 0);
		this.spareFlag = ((flags & 7));
		
		this.messageType = bb.get();
		this.totalLength = bb.getShort();
		
		if(this.teidFlag){
			this.teid = bb.getInt();
		}
		
		for(int i=0; i<3;i++){
			this.sequenceNumber[i] = bb.get();	
		}

		//No extension headers according to 3GPP TS 29.274 V13.2.0 (2015-06) Section 5.2
		//Spare last byte according to 3GPP TS 29.274 V13.2.0 (2015-06)
		this.spare = bb.get();
		return this;
	}

	@Override
	public int getSizeInBytes() {
	
		// Flags = 1
		// Message Type = 1
		// Length = 2
		// Sequence Number = 3
		// Spare
		int fixedHeaderSizeBytes = 1 + 1 + 2 + 3 + 1;
		
		if (this.teidFlag) {
			// teid = 4
			fixedHeaderSizeBytes += 4;
		}
		
		//No extension headers according to 3GPP TS 29.274 V13.2.0 (2015-06) Section 5.2
		
		return fixedHeaderSizeBytes;
	}
	
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();

//	     * ------------------------------------------
//	     * |        Version (3)         |   P (1)   |
//	     * ------------------------------------------
//	     * |  T  (1)  |          Spare (3) [*]      | 

		buffer.append("\n");
		buffer.append("|Ver\t|Pgy\t|T\t|Spr[*]\t\n");

		buffer.append(this.version + "\t" + this.piggyBackingFlag + "\t"
				+ this.spareFlag + "\t\n");

		return buffer.toString();
	}

	public int getSpareFlag() {
		return spareFlag;
	}

	public GTPHeaderV2 setSpareFlag(int spareFlag) {
		this.spareFlag = spareFlag;
		return this;
	}

	public boolean isTeidFlag() {
		return teidFlag;
	}

	public GTPHeaderV2 setTeidFlag(boolean teidFlag) {
		this.teidFlag = teidFlag;
		return this;
	}

	public boolean isPiggyBackingFlag() {
		return piggyBackingFlag;
	}

	public GTPHeaderV2 setPiggyBackingFlag(boolean piggyBackingFlag) {
		this.piggyBackingFlag = piggyBackingFlag;
		return this;
	}

	public byte getMessageType() {
		return messageType;
	}

	public GTPHeaderV2 setMessageType(byte messageType) {
		this.messageType = messageType;
		return this;
	}

	public short getTotalLength() {
		return totalLength;
	}

	public GTPHeaderV2 setTotalLength(short totalLength) {
		this.totalLength = totalLength;
		return this;
	}

	public int getTeid() {
		return teid;
	}

	public GTPHeaderV2 setTeid(int teid) {
		this.teid = teid;
		return this;
	}

	public byte getSpare() {
		return spare;
	}

	public GTPHeaderV2 setSpare(byte spare) {
		this.spare = spare;
		return this;
	}

	public byte[] getSequenceNumber() {
		return sequenceNumber;
	}

	public GTPHeaderV2 setSequenceNumber(byte[] sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
		return this;
	}

	@Override
	public byte[] getNextSequenceNumber() {
		//I did this due to short being signed and 
		byte[] intSeqNumberByteArray = Arrays.copyOf(SIZE1_ZERO_BYTE_ARRAY, SIZE1_ZERO_BYTE_ARRAY.length + this.sequenceNumber.length);
		System.arraycopy(this.sequenceNumber, 0, intSeqNumberByteArray, SIZE1_ZERO_BYTE_ARRAY.length, this.sequenceNumber.length);
		  
		int seqNumber = ((ByteBuffer)ByteBuffer.allocate(4).put(intSeqNumberByteArray).rewind()).getInt();
		//I could not find a reference to the max value in 3GPP TS 29.274 V13.2.0 (2015-06)
		//Using (2^24) - 1
		seqNumber++;
		if(seqNumber >= 16777216){
			seqNumber = 0;
		}
		
		byte[] result = new byte[3];
		
		result[0] = (byte) ((seqNumber >> 24) & 0xFF);
		result[1] = (byte) ((seqNumber >> 16) & 0xFF);
		result[2] = (byte) ((seqNumber >> 8) & 0xFF);
		
		return result;
	}

}
