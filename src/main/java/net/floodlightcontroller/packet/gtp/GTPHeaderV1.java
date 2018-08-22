package net.floodlightcontroller.packet.gtp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.PacketParsingException;

public class GTPHeaderV1 extends AbstractGTPHeader {
	
    private static final byte ECHO_RESPONSE_TYPE = (byte) 0x02;
	/**
     * Got from 3GPP TS 29.060 V13.1.0 (2015-06) Section 6
     * GTPv1
     * ------------------------------------------
     * |V* (3)|PT (1)|[*] (1)|E (1)|S (1)|PN (1)|
     * ------------------------------------------
     * |           Message Type (8)             |
     * ------------------------------------------
     * |            Length 1st (8)              |
     * ------------------------------------------
     * |            Length 2nd (8)              |
     * ------------------------------------------
     * |   Tunnel Endpoint Identifier 1st (8)   |
     * ------------------------------------------
     * |   Tunnel Endpoint Identifier 2nd (8)   |
     * ------------------------------------------
     * |   Tunnel Endpoint Identifier 3rd (8)   |
     * ------------------------------------------
     * |   Tunnel Endpoint Identifier 4th (8)   |
     * ------------------------------------------
     * |       Sequence Number 1st (8)   [1][4] |
     * ------------------------------------------
     * |       Sequence Number 2nd (8)   [1][4] |
     * ------------------------------------------
     * |            N-PDU Number (8)     [2][4] |
     * ------------------------------------------
     * |  Next Extension Header Type (8) [3][4] |
     * ------------------------------------------
     * 
     * V* = Version
     * NOTE 0:	[*] This bit is a spare bit. It shall be sent as "0". The receiver shall not evaluate this bit.
	 * NOTE 1:	[1] This field shall only be evaluated when indicated by the S flag set to 1.
	 * NOTE 2:	[2] This field shall only be evaluated when indicated by the PN flag set to 1.
	 * NOTE 3:	[3] This field shall only be evaluated when indicated by the E flag set to 1.
	 * NOTE 4:	[4] This field shall be present if and only if any one or more of the S, PN and E flags are set.
	 **/
	
//	public static final byte GTP_PROTOCOL_TYPE_MASK = 16;
    
    private static final byte[] SIZE2_ZERO_BYTE_ARRAY = { 0x00, 0x00 };

	
	//GTPv1 flags
	private boolean protocolType;
	private boolean reserved;
	private boolean extHeaderFlag;
	private boolean sequenceNumberFlag;
	private boolean nPDUNumberFlag;
	private byte messageType;
	private int teid;
	private byte nextExtHeader;
	private byte nPDUNumber;
	private List<GTPV1ExtHeader> extHeaders;
	private byte recoveryRestartCounter;
	private byte recoveryType;
	
	public GTPHeaderV1(){
		extHeaders = new ArrayList<GTPV1ExtHeader>();
		this.version = 1;
	}

	@Override
	public byte[] serialize() {
		byte[] data = createHeaderDataArray();

		ByteBuffer bb = ByteBuffer.wrap(data);
		
		int version = (this.version << AbstractGTP.GTP_VERSION_SHIFT);

		byte flags = (byte) (version
				+ (this.protocolType ? 16 : 0) + (this.reserved ? 8 : 0)
				+ (this.extHeaderFlag ? 4 : 0)
				+ (this.sequenceNumberFlag ? 2 : 0) + (this.nPDUNumberFlag ? 1
				: 0));
		bb.put(flags);
		bb.put(this.messageType);
		bb.putShort(this.totalLength);
		bb.putInt(this.teid);

		if (this.extHeaderFlag || this.sequenceNumberFlag
				|| this.nPDUNumberFlag) {
			// Extra fields are present
			// They should be read, but interpreted only if
			// Specific flags are set
			bb.put(this.sequenceNumber);
			bb.put(this.nPDUNumber);
			bb.put(this.nextExtHeader);

			for (GTPV1ExtHeader extHeader : extHeaders) {
				bb.put(extHeader.serialize());
			}
		}
		
		
		//According to section 7.7.11 in 3GPP TS 29.060 V13.1.0 (2015-06)
		if(this.messageType == ECHO_RESPONSE_TYPE ){
			//Mandatory fields for Echo Response messages according to Section 7.2.2
			//from 3GPP TS 29.060 V13.1.0 (2015-06)			
			bb.put(this.recoveryType);
			bb.put(this.recoveryRestartCounter);
		}

		return data;
	}

	@Override
	public IGTPHeader deserialize(ByteBuffer bb, byte scratch) throws PacketParsingException {
		byte version = AbstractGTP.extractVersionFromScratch(scratch);
		
		if(version != this.version){
			throw new RuntimeException("Expected version was "+this.version+". Wrong deserialization of the packet on parent.");
		}

		byte flags = (byte) (scratch & GTP_FLAG_MASK);

		this.protocolType = ((flags & 16) != 0);
		this.reserved = ((flags & 8) != 0);
		this.extHeaderFlag = ((flags & 4) != 0);
		this.sequenceNumberFlag = ((flags & 2) != 0);
		this.nPDUNumberFlag = ((flags & 1) != 0);

		this.messageType = bb.get();
		
		//Length of all extra data in the packet
		//According to 3GPP TS 29.060 V13.1.0 (2015-06) Section 6
		//The Sequence Number, the N-PDU Number or any Extension headers shall be considered to be part of the payload, i.e. included in the length count.
		this.totalLength = bb.getShort();

		this.teid = bb.getInt();

		if (this.extHeaderFlag || this.sequenceNumberFlag
				|| this.nPDUNumberFlag) {
			// Extra fields are present
			// They should be read, but interpreted only if
			// Specific flags are set
			byte[] seqNumber = new byte[2];
			seqNumber[0] = bb.get();
			seqNumber[1] = bb.get();
			byte nPDUNum = bb.get();
			byte nextHeader = bb.get();

			if (this.sequenceNumberFlag) {
				this.sequenceNumber = seqNumber;
			}

			if (this.nPDUNumberFlag) {
				this.nPDUNumber = nPDUNum;
			}

			if (this.extHeaderFlag) {
				this.nextExtHeader = nextHeader;
				extHeaders = new ArrayList<GTPV1ExtHeader>();
			}

			while (nextHeader != 0) {
				// This means that there are extra headers to be read

				GTPV1ExtHeader extHeader = new GTPV1ExtHeader();
				extHeader.setVersion(this.version);
				extHeader.deserialize(bb, (byte) 0);
				extHeaders.add(extHeader);
				nextHeader = extHeader.getNextExtHeaderType();
			}
		}
		
		//According to section 7.7.11 in 3GPP TS 29.060 V13.1.0 (2015-06)
		if(this.messageType == ECHO_RESPONSE_TYPE ){
			//Mandatory fields for Echo Response messages according to Section 7.2.2
			//from 3GPP TS 29.060 V13.1.0 (2015-06)
			this.recoveryType = bb.get();
			this.recoveryRestartCounter = bb.get();
		}
		
		return this;
	}

	@Override
	public int getSizeInBytes() {
		
		// Flags = 1
		// Message Type = 1
		// Length = 2
		// teid = 4
		int fixedHeaderSizeBytes = 1 + 1 + 2 + 4;
		
		if (this.extHeaderFlag || this.sequenceNumberFlag
				|| this.nPDUNumberFlag) {
			
			// Sequence Number = 2
			// N-PDU number = 1
			// Next Extension Header = 1
			fixedHeaderSizeBytes += 2 + 1 + 1;
		}
		
		//According to section 7.7.11 in 3GPP TS 29.060 V13.1.0 (2015-06)
		if(this.messageType == ECHO_RESPONSE_TYPE ){
			//Mandatory fields for Echo Response messages according to Section 7.2.2
			//from 3GPP TS 29.060 V13.1.0 (2015-06)
			
			fixedHeaderSizeBytes += 2;
		}
		
		int numberOfExtraBytes = 0;
		for (GTPV1ExtHeader extHeader : extHeaders) {
			numberOfExtraBytes += extHeader.getN();
		}

		return fixedHeaderSizeBytes + numberOfExtraBytes;
	}
	
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();

		// * ----------------------------------------------------------
		// * | Version (3) | PT (1) | [*] (1) | E (1) | S (1) | PN (1) |
		// * ----------------------------------------------------------
		// * 

		buffer.append("\n");
		buffer.append("|Ver\t|PT\t|(*)\t|E\t|S\t|PN\t|\n");

		buffer.append(this.version + "\t" + this.protocolType + "\t"
				+ this.reserved + "\t" + this.extHeaderFlag + "\t"
				+ this.sequenceNumberFlag + "\t" + this.nPDUNumberFlag+"\n");

		return buffer.toString();
	}
	
	
	class GTPV1ExtHeader {

		private byte n;
		private byte nextExtHeaderType;
		private IPacket payload;
		private byte version;

		public byte getN() {
			return n;
		}

		public void setVersion(byte version) {
			this.version = version;
		}

		public byte getNextExtHeaderType() {
			return nextExtHeaderType;
		}

		public byte[] serialize() {
			byte[] data = new byte[(this.n*4)];
			data[0] = this.n;
			
			byte[] headerData = ((Data)this.payload).getData();
			
			for (int i = 1; i < this.n*4; i++) {
				data[i] = headerData[i-1];
			}

			return data;
		}
		
		public IPacket getExtraHeader(){
			return this.payload;
		}

		public GTPV1ExtHeader deserialize(byte[] data, int offset, int length)
				throws PacketParsingException {
			
	        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
	        
	        return deserialize(bb, (byte) 0);
		}

		public GTPV1ExtHeader deserialize(ByteBuffer bb, byte scratch)
				throws PacketParsingException {
			//The number of octets according to 3GPP TS 29.060 V13.1.0 (2015-06) Section 6
	        //The total number of octets, including the this.n, is this.n * 4
			this.n = bb.get();
			byte[] headerData = new byte[(this.n*4)-1];
			
			//Read all extra information according to this.n
			for (int i = 1; i < this.n*4; i++) {
				headerData[i-1] = bb.get();
			}
			
			this.nextExtHeaderType = headerData[headerData.length-1];

			this.payload = new Data(headerData);
			return this;
		}

		public byte getVersion() {
			return this.version;
		}

		public int getSizeInBytes() {
			return this.n;
		}

	}


	public boolean isProtocolType() {
		return protocolType;
	}

	public GTPHeaderV1 setProtocolType(boolean protocolType) {
		this.protocolType = protocolType;
		return this;
	}

	public boolean isReserved() {
		return reserved;
	}

	public GTPHeaderV1 setReserved(boolean reserved) {
		this.reserved = reserved;
		return this;
	}

	public boolean isExtHeaderFlag() {
		return extHeaderFlag;
	}

	public GTPHeaderV1 setExtHeaderFlag(boolean extHeaderFlag) {
		this.extHeaderFlag = extHeaderFlag;
		return this;
	}

	public boolean isSequenceNumberFlag() {
		return sequenceNumberFlag;
	}

	public GTPHeaderV1 setSequenceNumberFlag(boolean sequenceNumberFlag) {
		this.sequenceNumberFlag = sequenceNumberFlag;
		return this;
	}

	public boolean isnPDUNumberFlag() {
		return nPDUNumberFlag;
	}

	public GTPHeaderV1 setnPDUNumberFlag(boolean nPDUNumberFlag) {
		this.nPDUNumberFlag = nPDUNumberFlag;
		return this;
	}
	
	public byte getMessageType() {
		return messageType;
	}

	public GTPHeaderV1 setMessageType(byte messageType) {
		this.messageType = messageType;
		return this;
	}

	public short getTotalLength() {
		return totalLength;
	}

	public GTPHeaderV1 setTotalLength(short totalLength) {
		this.totalLength = totalLength;
		return this;
	}
	
	public int getTeid() {
		return teid;
	}

	public GTPHeaderV1 setTeid(int teid) {
		this.teid = teid;
		return this;
	}

	public byte getNextExtHeader() {
		return nextExtHeader;
	}

	public GTPHeaderV1 setNextExtHeader(byte nextExtHeader) {
		this.nextExtHeader = nextExtHeader;
		return this;
	}

	public byte getnPDUNumber() {
		return nPDUNumber;
	}

	public GTPHeaderV1 setnPDUNumber(byte nPDUNumber) {
		this.nPDUNumber = nPDUNumber;
		return this;
	}
	
	public byte[] getSequenceNumber() {
		return sequenceNumber;
	}

	public GTPHeaderV1 setSequenceNumber(byte[] sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
		return this;
	}

	public List<GTPV1ExtHeader> getExtHeaders() {
		return extHeaders;
	}

	public GTPHeaderV1 setExtHeaders(List<GTPV1ExtHeader> extHeaders) {
		this.extHeaders = extHeaders;
		return this;
	}

	public byte getRecoveryRestartCounter() {
		return recoveryRestartCounter;
	}

	public GTPHeaderV1 setRecoveryRestartCounter(byte recoveryRestartCounter) {
		this.recoveryRestartCounter = recoveryRestartCounter;
		return this;
	}

	public byte getRecoveryType() {
		return recoveryType;
	}

	public GTPHeaderV1 setRecoveryType(byte recoveryType) {
		this.recoveryType = recoveryType;
		return this;
	}

	@Override
	public byte[] getNextSequenceNumber() {
		//I did this due to short being signed and 
		byte[] intSeqNumberByteArray = Arrays.copyOf(SIZE2_ZERO_BYTE_ARRAY, SIZE2_ZERO_BYTE_ARRAY.length + this.sequenceNumber.length);
		System.arraycopy(this.sequenceNumber, 0, intSeqNumberByteArray, SIZE2_ZERO_BYTE_ARRAY.length, this.sequenceNumber.length);
		
		int seqNumber = ((ByteBuffer)ByteBuffer.allocate(4).put(intSeqNumberByteArray).rewind()).getInt();
		//The Sequence Number value shall be wrapped to zero after 65535 
		//according to  3GPP TS 29.060 V13.1.0 (2015-06) Section 9.3.1.1
		seqNumber++;
		if(seqNumber > 65535){
			seqNumber = 0;
		}
		
		return ByteBuffer.allocate(2).putShort((short) seqNumber).array();
	}

}
