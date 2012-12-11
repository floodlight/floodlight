package org.openflow.protocol;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

/**
 * Represents ofp_packet_queue
 * @author Andrew Ferguson (adf@cs.brown.edu)
 */
public class OFPacketQueue {
    public static int MINIMUM_LENGTH = 8;

    protected int queueId;
    protected short length;
    protected List<OFQueueProp> properties = new ArrayList<OFQueueProp>();

    public OFPacketQueue() {
    	this.queueId = -1;
    	this.length = -1;
    }
    
    public OFPacketQueue(int queueId) {
    	this.queueId = queueId;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /**
     * @return the queueId
     */
    public long getQueueId() {
        return queueId;
    }

    /**
     * @param queueId the queueId to set
     */
    public void setQueueId(int queueId) {
        this.queueId = queueId;
    }
    
    /**
     * @return the queue's properties
     */
    public List<OFQueueProp> getProperties() {
		return properties;
	}

    /**
     * @param properties the properties to set
     */
	public void setProperties(List<OFQueueProp> properties) {
		this.properties = properties;
		
		this.length = U16.t(MINIMUM_LENGTH);
		for (OFQueueProp prop : properties) {
			this.length += prop.getLength();
		}
	}

	/**
     * @return the length
     */
    public short getLength() {
        return length;
    }
    
    public void readFrom(ChannelBuffer data) {
        this.queueId = data.readInt();
        this.length = data.readShort();
        data.readShort(); // pad
        
        int availLength = (this.length - MINIMUM_LENGTH);
        this.properties.clear();
        
        while (availLength > 0) {
        	OFQueueProp prop = new OFQueueProp();
        	prop.readFrom(data);
        	properties.add(prop);
        	availLength -= prop.getLength();
        }
    }
    
    public void writeTo(ChannelBuffer data) {
    	data.writeInt(queueId);
    	data.writeShort(length);
    	data.writeShort(0); // pad
    	
    	for (OFQueueProp prop : properties) {
    		prop.writeTo(data);
    	}
    }
    
    @Override
    public int hashCode() {
        final int prime = 359;
        int result = super.hashCode();
        result = prime * result + queueId;
        result = prime * result + length;
        result = prime * result + properties.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof OFPacketQueue)) {
            return false;
        }
        OFPacketQueue other = (OFPacketQueue) obj;
        if (queueId != other.queueId) {
            return false;
        }
        if (! properties.equals(other.properties)) {
        	return false;
        }
        return true;
    }
}
