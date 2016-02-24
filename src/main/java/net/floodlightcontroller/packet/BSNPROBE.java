/**
*    Copyright 2012, Big Switch Networks, Inc. 
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

/**
 * 
 */
package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.projectfloodlight.openflow.util.HexString;

/**
 * @author Shudong Zhou (shudong.zhou@bigswitch.com)
 *
 */
public class BSNPROBE extends BasePacket {	
	protected long controllerId;
	protected int sequenceId;
	protected byte[] srcMac;
	protected byte[] dstMac;
	protected long srcSwDpid;
	protected int srcPortNo;

    public BSNPROBE() {
        srcMac = new byte[6];
        dstMac = new byte[6];
    }


	public long getControllerId() {
		return this.controllerId;
	}

	public BSNPROBE setControllerId(long controllerId) {
		this.controllerId = controllerId;
		return this;
	}

	public int getSequenceId() {
		return sequenceId;
	}

	public BSNPROBE setSequenceId(int sequenceId) {
		this.sequenceId = sequenceId;
		return this;
	}
	
    public byte[] getSrcMac() {
        return this.srcMac;
    }

    public BSNPROBE setSrcMac(byte[] srcMac) {
        this.srcMac = srcMac;
        return this;
    }
    
	public byte[] getDstMac() {
		return dstMac;
	}

	public BSNPROBE setDstMac(byte[] dstMac) {
		this.dstMac = dstMac;
		return this;
	}

	public long getSrcSwDpid() {
		return srcSwDpid;
	}

	public BSNPROBE setSrcSwDpid(long srcSwDpid) {
		this.srcSwDpid = srcSwDpid;
		return this;
	}

	public int getSrcPortNo() {
		return srcPortNo;
	}

	public BSNPROBE setSrcPortNo(int srcPortNo) {
		this.srcPortNo = srcPortNo;
		return this;
	}

    @Override
    public byte[] serialize() {
    	short length = 8 /* controllerId */ + 4 /* seqId */
    			+ 12 /* srcMac dstMac */ + 8 /* srcSwDpid */ + 4 /* srcPortNo */;
    	
    	byte[] payloadData = null;
    	if (this.payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
            length += payloadData.length;
        }
    
        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.putLong(this.controllerId);
        bb.putInt(this.sequenceId);
        bb.put(this.srcMac);
        bb.put(this.dstMac);
        bb.putLong(this.srcSwDpid);
        bb.putInt(this.srcPortNo);
        if (payloadData != null)
        	bb.put(payloadData);

        if (this.parent != null && this.parent instanceof BSN)
            ((BSN)this.parent).setType(BSN.BSN_TYPE_PROBE);

        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length)
            throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        
        controllerId = bb.getLong();
        sequenceId = bb.getInt();
        bb.get(this.srcMac, 0, 6);
        bb.get(this.dstMac, 0, 6);
        this.srcSwDpid = bb.getLong();
        this.srcPortNo = bb.getInt();
        
        if (bb.hasRemaining()) {
        	this.payload = new Data();
	        this.payload = payload.deserialize(data, bb.position(), bb.limit() - bb.position());
	        this.payload.setParent(this);
        }
        
        return this;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                 + (int) (controllerId ^ (controllerId >>> 32));
        result = prime * result + Arrays.hashCode(dstMac);
        result = prime * result + sequenceId;
        result = prime * result + Arrays.hashCode(srcMac);
        result = prime * result + srcPortNo;
        result = prime * result + (int) (srcSwDpid ^ (srcSwDpid >>> 32));
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        BSNPROBE other = (BSNPROBE) obj;
        if (controllerId != other.controllerId) return false;
        if (!Arrays.equals(dstMac, other.dstMac)) return false;
        if (sequenceId != other.sequenceId) return false;
        if (!Arrays.equals(srcMac, other.srcMac)) return false;
        if (srcPortNo != other.srcPortNo) return false;
        if (srcSwDpid != other.srcSwDpid) return false;
        return true;
    }


    public String toString() {
    	StringBuffer sb = new StringBuffer("\n");
    	sb.append("BSN Probe packet");
    	sb.append("\nSource Mac: ");
    	sb.append(HexString.toHexString(srcMac));
    	sb.append("\nDestination Mac: ");
    	sb.append(HexString.toHexString(dstMac));
    	sb.append("\nSource Switch: ");
    	sb.append(HexString.toHexString(srcSwDpid));
    	sb.append(" port: " + srcPortNo);
    	sb.append("\nSequence No.:" + sequenceId);
    	
    	return sb.toString();
    }
}
