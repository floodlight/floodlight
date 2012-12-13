package org.openflow.protocol;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

/**
 * Represents an ofp_queue_get_config_request message
 * @author Andrew Ferguson (adf@cs.brown.edu)
 */
public class OFQueueGetConfigReply extends OFMessage {
    public static int MINIMUM_LENGTH = 16;

    protected short portNumber;
    protected List<OFPacketQueue> queues = new ArrayList<OFPacketQueue>();

    public OFQueueGetConfigReply() {
        super();
        this.type = OFType.QUEUE_GET_CONFIG_REPLY;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /**
     * @return the portNumber
     */
    public short getPortNumber() {
        return portNumber;
    }

    /**
     * @param portNumber the portNumber to set
     */
    public void setPortNumber(short portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * @return the port's queues
     */
    public List<OFPacketQueue> getQueues() {
        return queues;
    }

    /**
     * @param queues the queues to set
     */
    public void setQueues(List<OFPacketQueue> queues) {
        this.queues.clear();
        this.queues.addAll(queues);
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.portNumber = data.readShort();
        data.readInt();   // pad
        data.readShort(); // pad

        int availLength = (this.length - MINIMUM_LENGTH);
        this.queues.clear();

        while (availLength > 0) {
            OFPacketQueue queue = new OFPacketQueue();
            queue.readFrom(data);
            queues.add(queue);
            availLength -= queue.getLength();
        }
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.portNumber);
        data.writeInt(0);   // pad
        data.writeShort(0); // pad

        for (OFPacketQueue queue : queues) {
            queue.writeTo(data);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 349;
        int result = super.hashCode();
        result = prime * result + portNumber;
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
        if (!(obj instanceof OFQueueGetConfigReply)) {
            return false;
        }
        OFQueueGetConfigReply other = (OFQueueGetConfigReply) obj;
        if (portNumber != other.portNumber) {
            return false;
        }
        return true;
    }
}
