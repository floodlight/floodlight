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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
*
* @author David Erickson (daviderickson@cs.stanford.edu)
*/
public abstract class BasePacket implements IPacket {
    public static final Logger log = LoggerFactory.getLogger(BasePacket.class);
    protected IPacket parent;
    protected IPacket payload;

    /**
     * @return the parent
     */
    @Override
    public IPacket getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    @Override
    public IPacket setParent(IPacket parent) {
        this.parent = parent;
        return this;
    }

    /**
     * @return the payload
     */
    @Override
    public IPacket getPayload() {
        return payload;
    }

    /**
     * @param payload the payload to set
     */
    @Override
    public IPacket setPayload(IPacket payload) {
        this.payload = payload;
        return this;
    }
    
    @Override
    public void resetChecksum() {
        if (this.parent != null)
            this.parent.resetChecksum();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 6733;
        int result = 1;
        result = prime * result + ((payload == null) ? 0 : payload.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof BasePacket))
            return false;
        BasePacket other = (BasePacket) obj;
        if (payload == null) {
            if (other.payload != null)
                return false;
        } else if (!payload.equals(other.payload))
            return false;
        return true;
    }
    
    @Override
    public Object clone() {
        IPacket pkt;
        try {
            pkt = this.getClass().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone packet");
        }
        // TODO: we are using serialize()/deserialize() to perform the 
        // cloning. Not the most efficient way but simple. We can revisit
        // if we hit performance problems.
        byte[] data = this.serialize();
        try {
            pkt.deserialize(this.serialize(), 0, data.length);
        } catch (PacketParsingException e) {
            // This shouldn't happen here, since we already deserialized it once
            return new Data(data);
        }
        pkt.setParent(this.parent);
        return pkt;
    }
}
