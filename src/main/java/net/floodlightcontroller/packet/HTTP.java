package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;

import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.TransportPort;

/**
 * @author Diogo Coutinho (diogo.m.coutinho@tecnico.ulisboa.pt)
 *
 */
public class HTTP extends BasePacket {
	
	protected byte method;
	protected byte uri;
	protected byte version;
	protected short host; // IP DO SERVER
    // SP = (short) 20 
	// CRLF = 0d 0a 

	@Override
	public byte[] serialize() {
		 int length;
//	        if (dataOffset == 0)
//	            dataOffset = 5;  // default header length
//	        length = dataOffset << 2;
//	        byte[] payloadData = null;
//	        if (payload != null) {
//	            payload.setParent(this);
//	           
//	            payloadData = payload.serialize();
//	            length += payloadData.length;
//	        }
//
//	        byte[] data = new byte[length];
//	        ByteBuffer bb = ByteBuffer.wrap(data);
//
//	        bb.putShort((short)this.sourcePort.getPort()); //TCP ports are defined to be 16 bits
//	        bb.putShort((short)this.destinationPort.getPort());
//	        bb.putInt(this.sequence);
//	        bb.putInt(this.acknowledge);
//	        bb.putShort((short) (this.flags | (dataOffset << 12)));
//	        bb.putShort(this.windowSize);
//	        bb.putShort(this.checksum);
//	        bb.putShort(this.urgentPointer);
//	        if (dataOffset > 5) {
//	            int padding;
//	            bb.put(options);
//	            padding = (dataOffset << 2) - 20 - options.length;
//	            for (int i = 0; i < padding; i++)
//	                bb.put((byte) 0);
//	        }
//	        if (payloadData != null)
//	            bb.put(payloadData);
//
//	        if (this.parent != null && this.parent instanceof IPv4)
//	            ((IPv4)this.parent).setProtocol(IpProtocol.TCP);
//	        
//	        return data;
		 return null;
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length) throws PacketParsingException {
		 ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
		 
		 
		 
//	        this.sourcePort = TransportPort.of((int) (bb.getShort() & 0xffff)); // short will be signed, pos or neg
//	        this.destinationPort = TransportPort.of((int) (bb.getShort() & 0xffff)); // convert range 0 to 65534, not -32768 to 32767
//	        this.sequence = bb.getInt();
//	        this.acknowledge = bb.getInt();
//	        this.flags = bb.getShort();
//	        this.dataOffset = (byte) ((this.flags >> 12) & 0xf);
//	        if (this.dataOffset < 5) {
//	            throw new PacketParsingException("Invalid tcp header length < 20");
//	        }
//	        this.flags = (short) (this.flags & 0x1ff);
//	        this.windowSize = bb.getShort();
//	        this.checksum = bb.getShort();
//	        this.urgentPointer = bb.getShort();
//	        if (this.dataOffset > 5) {
//	            int optLength = (dataOffset << 2) - 20;
//	            if (bb.limit() < bb.position()+optLength) {
//	                optLength = bb.limit() - bb.position();
//	            }
//	            try {
//	                this.options = new byte[optLength];
//	                bb.get(this.options, 0, optLength);
//	            } catch (IndexOutOfBoundsException e) {
//	                this.options = null;
//	            }
//	        }
	                        
	        this.payload = new Data();
	        int remLength = bb.limit()-bb.position();
	        this.payload = payload.deserialize(data, bb.position(), remLength);

	        log.info("HTTP payload : {}", this.payload);
	        
	        this.payload.setParent(this);
	        return this;
	}
}