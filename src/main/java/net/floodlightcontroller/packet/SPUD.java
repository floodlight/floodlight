package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Jacob Chappell (jacob.chappell@uky.edu)
 */
public class SPUD extends BasePacket {
    public static final byte[] MAGIC_CONSTANT =
        { (byte) 0xd8, 0x00, 0x00, (byte) 0xd8 };
    public static final int HEADER_LENGTH = 13;
    public static final byte COMMAND_DATA = 0x0;
    public static final byte COMMAND_OPEN = 0x1;
    public static final byte COMMAND_CLOSE = 0x2;
    public static final byte COMMAND_ACK = 0x3;

    protected long tubeID;
    protected byte command;
    protected boolean adec;
    protected boolean pdec;
    protected byte reserved;

    public long getTubeID() {
        return tubeID;
    }

    public SPUD setTubeID(long tubeID) {
        this.tubeID = tubeID;
        return this;
    }

    public byte getCommand() {
        return command;
    }

    public SPUD setCommand(byte command) {
        this.command = command;
        return this;
    }

    public boolean getADEC() {
        return adec;
    }

    public SPUD setADEC(boolean adec) {
        this.adec = adec;
        return this;
    }

    public boolean getPDEC() {
        return pdec;
    }

    public SPUD setPDEC(boolean pdec) {
        this.pdec = pdec;
        return this;
    }

    public byte getReserved() {
        return reserved;
    }

    public SPUD setReserved(byte reserved) {
        this.reserved = reserved;
        return this;
    }

    @Override
    public byte[] serialize() {
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
        }
        int length = HEADER_LENGTH + ((payloadData == null) ? 0 : payloadData.length);
        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(MAGIC_CONSTANT);
        bb.putLong(tubeID);
        byte adecBit = (byte) ((adec) ? 1 : 0);
        byte pdecBit = (byte) ((pdec) ? 1 : 0);
        byte lastByte = (byte) (((command & 0x3) << 6) | ((adecBit & 0x1) << 5)
                | ((pdecBit & 0x1) << 4) | (reserved & 0xf));
        bb.put(lastByte);
        if (payloadData != null) {
            bb.put(payloadData);
        }
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length)
            throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        byte[] magicConstant = new byte[MAGIC_CONSTANT.length];
        bb.get(magicConstant, 0, MAGIC_CONSTANT.length);
        if (!Arrays.equals(magicConstant, MAGIC_CONSTANT)) {
            throw new PacketParsingException("Magic constant is incorrect.");
        }
        tubeID = bb.getLong();
        byte lastByte = bb.get();
        command = (byte) ((lastByte & 0xc0) >>> 6);
        adec = ((lastByte & 0x20) != 0);
        pdec = ((lastByte & 0x10) != 0);
        reserved = (byte) (lastByte & 0xF);
        // TODO: make sure reserved bits are 0 for this version.
        this.payload = new Data();
        this.payload = payload.deserialize(data, bb.position(), bb.limit()-bb.position());
        this.payload.setParent(this);
        return this;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (adec ? 1231 : 1237);
        result = prime * result + command;
        result = prime * result + (pdec ? 1231 : 1237);
        result = prime * result + reserved;
        result = prime * result + (int) (tubeID ^ (tubeID >>> 32));
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
        if (!(obj instanceof SPUD))
            return false;
        SPUD other = (SPUD) obj;
        if (adec != other.adec)
            return false;
        if (command != other.command)
            return false;
        if (pdec != other.pdec)
            return false;
        if (reserved != other.reserved)
            return false;
        if (tubeID != other.tubeID)
            return false;
        return true;
    }
}
