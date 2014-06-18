/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol;

import java.util.ArrayList;
import java.util.List;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.serializers.OFFeaturesReplyJSONSerializer;
import org.openflow.protocol.serializers.StringDpidToLongJSONDeserializer;
import org.openflow.util.U16;


/**
 * Represents a features reply message
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
@JsonSerialize(using=OFFeaturesReplyJSONSerializer.class)
public class OFFeaturesReply extends OFMessage {
    public static int MINIMUM_LENGTH = 32;

    /**
     * Corresponds to bits on the capabilities field
     */
    public enum OFCapabilities {
        OFPC_FLOW_STATS     (1 << 0),
        OFPC_TABLE_STATS    (1 << 1),
        OFPC_PORT_STATS     (1 << 2),
        OFPC_STP            (1 << 3),
        OFPC_RESERVED       (1 << 4),
        OFPC_IP_REASM       (1 << 5),
        OFPC_QUEUE_STATS    (1 << 6),
        OFPC_ARP_MATCH_IP   (1 << 7);

        protected int value;

        private OFCapabilities(int value) {
            this.value = value;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }

    protected long datapathId;
    protected int buffers;
    protected byte tables;
    protected int capabilities;
    protected int actions;
    protected List<OFPhysicalPort> ports;

    public OFFeaturesReply() {
        super();
        this.type = OFType.FEATURES_REPLY;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /**
     * @return the datapathId
     */
    public long getDatapathId() {
        return datapathId;
    }

    /**
     * @param datapathId the datapathId to set
     */
    @JsonDeserialize(using=StringDpidToLongJSONDeserializer.class)
    public void setDatapathId(long datapathId) {
        this.datapathId = datapathId;
    }

    /**
     * @return the buffers
     */
    public int getBuffers() {
        return buffers;
    }

    /**
     * @param buffers the buffers to set
     */
    public void setBuffers(int buffers) {
        this.buffers = buffers;
    }

    /**
     * @return the tables
     */
    public byte getTables() {
        return tables;
    }

    /**
     * @param tables the tables to set
     */
    public void setTables(byte tables) {
        this.tables = tables;
    }

    /**
     * @return the capabilities
     */
    public int getCapabilities() {
        return capabilities;
    }

    /**
     * @param capabilities the capabilities to set
     */
    public void setCapabilities(int capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * @return the actions
     */
    public int getActions() {
        return actions;
    }

    /**
     * @param actions the actions to set
     */
    public void setActions(int actions) {
        this.actions = actions;
    }

    /**
     * @return the ports
     */
    public List<OFPhysicalPort> getPorts() {
        return ports;
    }

    /**
     * @param ports the ports to set
     */
    public void setPorts(List<OFPhysicalPort> ports) {
        this.ports = ports;
        if (ports == null) {
            this.setLengthU(MINIMUM_LENGTH);
        } else {
            this.setLengthU(MINIMUM_LENGTH + ports.size()
                    * OFPhysicalPort.MINIMUM_LENGTH);
        }
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.datapathId = data.readLong();
        this.buffers = data.readInt();
        this.tables = data.readByte();
        data.readerIndex(data.readerIndex() + 3); // pad
        this.capabilities = data.readInt();
        this.actions = data.readInt();
        if (this.ports == null) {
            this.ports = new ArrayList<OFPhysicalPort>();
        } else {
            this.ports.clear();
        }
        int portCount = (super.getLengthU() - 32)
                / OFPhysicalPort.MINIMUM_LENGTH;
        OFPhysicalPort port;
        for (int i = 0; i < portCount; ++i) {
            port = new OFPhysicalPort();
            port.readFrom(data);
            this.ports.add(port);
        }
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeLong(this.datapathId);
        data.writeInt(this.buffers);
        data.writeByte(this.tables);
        data.writeShort((short) 0); // pad
        data.writeByte((byte) 0); // pad
        data.writeInt(this.capabilities);
        data.writeInt(this.actions);
        if (this.ports != null)
            for (OFPhysicalPort port : this.ports) {
                port.writeTo(data);
            }
    }

    @Override
    public int hashCode() {
        final int prime = 139;
        int result = super.hashCode();
        result = prime * result + actions;
        result = prime * result + buffers;
        result = prime * result + capabilities;
        result = prime * result + (int) (datapathId ^ (datapathId >>> 32));
        result = prime * result + ((ports == null) ? 0 : ports.hashCode());
        result = prime * result + tables;
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
        if (!(obj instanceof OFFeaturesReply)) {
            return false;
        }
        OFFeaturesReply other = (OFFeaturesReply) obj;
        if (actions != other.actions) {
            return false;
        }
        if (buffers != other.buffers) {
            return false;
        }
        if (capabilities != other.capabilities) {
            return false;
        }
        if (datapathId != other.datapathId) {
            return false;
        }
        if (ports == null) {
            if (other.ports != null) {
                return false;
            }
        } else if (!ports.equals(other.ports)) {
            return false;
        }
        if (tables != other.tables) {
            return false;
        }
        return true;
    }
}
