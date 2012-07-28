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
import java.util.HashMap;
import java.util.Map;

/**
 * @author Shudong Zhou (shudong.zhou@bigswitch.com)
 *
 */
public class BSN extends BasePacket {
	public static final int BSN_MAGIC = 0x20000604;
	public static final short BSN_VERSION_CURRENT = 0x0;
	public static final short BSN_TYPE_PROBE = 0x1;
	public static final short BSN_TYPE_BDDP  = 0x2;
	public static Map<Short, Class<? extends IPacket>> typeClassMap;
	
    static {
        typeClassMap = new HashMap<Short, Class<? extends IPacket>>();
        typeClassMap.put(BSN_TYPE_PROBE, BSNPROBE.class);
        typeClassMap.put(BSN_TYPE_BDDP, LLDP.class);
    }

	protected short type;
	protected short version;

	public BSN() {
    	version = BSN_VERSION_CURRENT;
	}
	
    public BSN(short type) {
    	this.type = type;
    	version = BSN_VERSION_CURRENT;
    }

    public short getType() {
		return type;
	}

	public BSN setType(short type) {
		this.type = type;
		return this;
	}
	
    public short getVersion() {
		return version;
	}

	public BSN setVersion(short version) {
		this.version = version;
		return this;
	}

    @Override
    public byte[] serialize() {
    	short length = 4 /* magic */ + 2 /* type */ + 2 /* version */;
    	
    	byte[] payloadData = null;
    	if (this.payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
            length += payloadData.length;
        }
    
        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.putInt(BSN_MAGIC);
        bb.putShort(this.type);
        bb.putShort(this.version);
        if (payloadData != null)
        	bb.put(payloadData);

        if (this.parent != null && this.parent instanceof Ethernet)
            ((Ethernet)this.parent).setEtherType(Ethernet.TYPE_BSN);

        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        
        int magic = bb.getInt();
        if (magic != BSN_MAGIC) {
        	throw new RuntimeException("Invalid BSN magic " + magic);
        }
        
        this.type = bb.getShort();
        this.version = bb.getShort();
        if (this.version != BSN_VERSION_CURRENT) {
        	throw new RuntimeException(
        			"Invalid BSN packet version " + this.version + ", should be "
        	        + BSN_VERSION_CURRENT);
        }
        
        IPacket payload;
        if (typeClassMap.containsKey(this.type)) {
            Class<? extends IPacket> clazz = typeClassMap.get(this.type);
            try {
                payload = clazz.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Error parsing payload for BSN packet" + e);
            }
        } else {
            payload = new Data();
        }
        
        this.payload = new Data();
        this.payload = payload.deserialize(data, bb.position(), bb.limit() - bb.position());
        this.payload.setParent(this);
        
        return this;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 883;
        int result = super.hashCode();
        result = prime * result + version;
        result = prime * result + type;
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
        if (!(obj instanceof BSN))
            return false;
        BSN other = (BSN) obj;
        return (type == other.type &&
        		version == other.version);
    }
    
    public String toString() {
    	StringBuffer sb = new StringBuffer("\n");
    	sb.append("BSN packet");
        if (typeClassMap.containsKey(this.type))
        	sb.append(" type: " + typeClassMap.get(this.type).getCanonicalName());
        else
        	sb.append(" type: " + this.type);
        
    	return sb.toString();
    }
}
