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

package org.openflow.protocol;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

public class OFQueueProp {
    private int NONE_MINIMUM_LENGTH = 8;
    private int RATE_MINIMUM_LENGTH = 16;

    public enum OFQueuePropType {
        OFPQT_NONE       (0),
        OFPQT_MIN_RATE   (1),
        OFPQT_MAX_RATE   (2);

        protected int value;

        private OFQueuePropType(int value) {
            this.value = value;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }

        public static OFQueuePropType fromShort(short x) {
            switch (x) {
                case 0:
                    return OFPQT_NONE;
                case 1:
                    return OFPQT_MIN_RATE;
                case 2:
                    return OFPQT_MAX_RATE;
            }
            return null;
        }
    }

    protected OFQueuePropType type;
    protected short length;
    protected short rate = -1; // not valid if type == OFPQT_NONE

    public OFQueueProp() {
        this.type = OFQueuePropType.OFPQT_NONE;
        this.length = U16.t(NONE_MINIMUM_LENGTH);
    }

    /**
     * @return the type
     */
    public OFQueuePropType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(OFQueuePropType type) {
        this.type = type;

        switch (type) {
            case OFPQT_NONE:
                this.length = U16.t(NONE_MINIMUM_LENGTH);
                break;
            case OFPQT_MIN_RATE:
                this.length = U16.t(RATE_MINIMUM_LENGTH);
                break;
            case OFPQT_MAX_RATE:
                this.length = U16.t(RATE_MINIMUM_LENGTH);
                break;
        }
    }

    /**
     * @return the rate
     */
    public short getRate() {
        return rate;
    }

    /**
     * @param rate the rate to set
     */
    public void setRate(short rate) {
        this.rate = rate;
    }

    /**
     * @return the length
     */
    public short getLength() {
        return length;
    }

    public void readFrom(ChannelBuffer data) {
        this.type = OFQueuePropType.fromShort(data.readShort());
        this.length = data.readShort();
        data.readInt(); // pad

        if (this.type == OFQueuePropType.OFPQT_MIN_RATE ||
            this.type == OFQueuePropType.OFPQT_MAX_RATE) {
            assert(this.length == RATE_MINIMUM_LENGTH);

            this.rate = data.readShort();
            data.readInt(); // pad
            data.readShort(); // pad
        } else {
            assert(this.length == NONE_MINIMUM_LENGTH);
        }
    }

    public void writeTo(ChannelBuffer data) {
        data.writeShort(this.type.getValue());
        data.writeShort(this.length);
        data.writeInt(0); // pad

        if (this.type == OFQueuePropType.OFPQT_MIN_RATE ||
            this.type == OFQueuePropType.OFPQT_MAX_RATE) {
            data.writeShort(this.rate);
            data.writeInt(0); // pad
            data.writeShort(0); // pad
        }
    }

    @Override
    public int hashCode() {
        final int prime = 353;
        int result = super.hashCode();
        result = prime * result + type.getValue();
        result = prime * result + rate;
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
        if (!(obj instanceof OFQueueProp)) {
            return false;
        }
        OFQueueProp other = (OFQueueProp) obj;
        if (type != other.type) {
            return false;
        }
        if (type == OFQueuePropType.OFPQT_MIN_RATE ||
            type == OFQueuePropType.OFPQT_MAX_RATE) {
            if (rate != other.rate) {
                return false;
            }
        }
        return true;
    }
}
