package org.openflow.protocol;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

/**
 * Represents an ofp_queue_get_config_request message
 * @author Andrew Ferguson (adf@cs.brown.edu)
 */
public class OFQueueGetConfigRequest extends OFMessage {
    public static int MINIMUM_LENGTH = 12;

    protected short portNumber;

    public OFQueueGetConfigRequest() {
        super();
        this.type = OFType.QUEUE_GET_CONFIG_REQUEST;
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

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.portNumber = data.readShort();
        data.readShort(); // pad
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.portNumber);
        data.writeShort(0); // pad
    }

    @Override
    public int hashCode() {
        final int prime = 347;
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
        if (!(obj instanceof OFQueueGetConfigRequest)) {
            return false;
        }
        OFQueueGetConfigRequest other = (OFQueueGetConfigRequest) obj;
        if (portNumber != other.portNumber) {
            return false;
        }
        return true;
    }
}
