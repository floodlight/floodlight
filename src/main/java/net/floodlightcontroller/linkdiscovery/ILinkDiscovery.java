/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.linkdiscovery;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.ser.ToStringSerializer;
import org.openflow.util.HexString;

public interface ILinkDiscovery {

    @JsonSerialize(using=ToStringSerializer.class)
    public enum UpdateOperation {
        LINK_UPDATED("Link Updated"),
        LINK_REMOVED("Link Removed"),
        SWITCH_UPDATED("Switch Updated"),
        SWITCH_REMOVED("Switch Removed"),
        PORT_UP("Port Up"),
        PORT_DOWN("Port Down"),
        TUNNEL_PORT_ADDED("Tunnel Port Added"),
        TUNNEL_PORT_REMOVED("Tunnel Port Removed");

        private String value;
        UpdateOperation(String v) {
            value = v;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public class LDUpdate {
        protected long src;
        protected short srcPort;
        protected long dst;
        protected short dstPort;
        protected SwitchType srcType;
        protected LinkType type;
        protected UpdateOperation operation;

        public LDUpdate(long src, short srcPort,
                      long dst, short dstPort,
                      ILinkDiscovery.LinkType type,
                      UpdateOperation operation) {
            this.src = src;
            this.srcPort = srcPort;
            this.dst = dst;
            this.dstPort = dstPort;
            this.type = type;
            this.operation = operation;
        }

        public LDUpdate(LDUpdate old) {
            this.src = old.src;
            this.srcPort = old.srcPort;
            this.dst = old.dst;
            this.dstPort = old.dstPort;
            this.srcType = old.srcType;
            this.type = old.type;
            this.operation = old.operation;
        }

        // For updtedSwitch(sw)
        public LDUpdate(long switchId, SwitchType stype, UpdateOperation oper ){
            this.operation = oper;
            this.src = switchId;
            this.srcType = stype;
        }

        // For port up or port down; and tunnel port added and removed.
        public LDUpdate(long sw, short port, UpdateOperation operation) {
            this.src = sw;
            this.srcPort = port;
            this.operation = operation;
        }

        public long getSrc() {
            return src;
        }

        public short getSrcPort() {
            return srcPort;
        }

        public long getDst() {
            return dst;
        }

        public short getDstPort() {
            return dstPort;
        }

        public SwitchType getSrcType() {
            return srcType;
        }

        public LinkType getType() {
            return type;
        }

        public UpdateOperation getOperation() {
            return operation;
        }

        public void setOperation(UpdateOperation operation) {
            this.operation = operation;
        }
        
        @Override
        public String toString() {
            switch (operation) {
            case LINK_REMOVED:
            case LINK_UPDATED:
                return "LDUpdate [operation=" + operation +
                        ", src=" + HexString.toHexString(src)
                        + ", srcPort=" + srcPort
                        + ", dst=" + HexString.toHexString(dst) 
                        + ", dstPort=" + dstPort
                        + ", type=" + type + "]";
            case PORT_DOWN:
            case PORT_UP:
                return "LDUpdate [operation=" + operation +
                        ", src=" + HexString.toHexString(src)
                        + ", srcPort=" + srcPort + "]";
            case SWITCH_REMOVED:
            case SWITCH_UPDATED:
                return "LDUpdate [operation=" + operation +
                        ", src=" + HexString.toHexString(src) + "]";
            default:
                return "LDUpdate: Unknown update.";
            }
        }
    }

    public enum SwitchType {
        BASIC_SWITCH, CORE_SWITCH
    };

    public enum LinkType {
        INVALID_LINK {
        	@Override
        	public String toString() {
        		return "invalid";
        	}
        }, 
        DIRECT_LINK{
        	@Override
        	public String toString() {
        		return "internal";
        	}
        }, 
        MULTIHOP_LINK {
        	@Override
        	public String toString() {
        		return "external";
        	}
        }, 
        TUNNEL {
        	@Override
        	public String toString() {
        		return "tunnel";
        	}
        }
    };
}
