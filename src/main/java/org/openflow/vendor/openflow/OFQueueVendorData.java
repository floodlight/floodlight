/**
*    Copyright 2012, Andrew Ferguson, Brown University
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

package org.openflow.vendor.openflow;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFPacketQueue;

/**
 * Class that represents the vendor data in a queue modify or delete request
 *
 * @author Andrew Ferguson (adf@cs.brown.edu)
 */
public class OFQueueVendorData extends OFOpenFlowVendorData {
    public static int MINIMUM_LENGTH = 8;

    protected short portNumber;
    protected List<OFPacketQueue> queues = new ArrayList<OFPacketQueue>();

    public OFQueueVendorData(int dataType) {
        super(dataType);
    }

    /**
     * @return the portNumber
     */
    public short getPortNumber() {
        return portNumber;
    }

    /**
     * @param port the port on which the queue is
     */
    public void setPortNumber(short portNumber) {
        this.portNumber = portNumber;
    }


    /**
     * @return the queues
     */
    public List<OFPacketQueue> getQueues() {
        return queues;
    }

    /**
     * @param queues the queues to modify or delete
     */
    public void setQueues(List<OFPacketQueue> queues) {
        this.queues = queues;
    }

    /**
     * @return the total length of the queue modify or delete msg
     */
    @Override
    public int getLength() {
        int queuesLength = 0;

        for (OFPacketQueue queue : queues) {
            queuesLength += queue.getLength();
        }

        return super.getLength() + MINIMUM_LENGTH + queuesLength;
    }

    /**
     * Read the queue message data from the ChannelBuffer
     * @param data the channel buffer from which we're deserializing
     * @param length the length to the end of the enclosing message
     */
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        portNumber = data.readShort();
        data.readInt();   // pad
        data.readShort(); // pad

        int availLength = (length - MINIMUM_LENGTH);
        this.queues.clear();

        while (availLength > 0) {
            OFPacketQueue queue = new OFPacketQueue();
            queue.readFrom(data);
            queues.add(queue);
            availLength -= queue.getLength();
        }
    }

    /**
     * Write the queue message data to the ChannelBuffer
     * @param data the channel buffer to which we're serializing
     */
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.portNumber);
        data.writeInt(0);   // pad
        data.writeShort(0); // pad

        for (OFPacketQueue queue : queues) {
            queue.writeTo(data);
        }
    }
}
