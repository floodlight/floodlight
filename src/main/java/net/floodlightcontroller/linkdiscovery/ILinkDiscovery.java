package net.floodlightcontroller.linkdiscovery;

public interface ILinkDiscovery {

    public static enum UpdateOperation {LINK_UPDATED, LINK_REMOVED, SWITCH_UPDATED, SWITCH_REMOVED, PORT_UP, PORT_DOWN};

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

        // For port up or port down.
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
            String operationString = null;
            if (operation == UpdateOperation.LINK_REMOVED) {
                operationString = "Link Removed";
                return "LDUpdate [operation=" + operationString +
                        ", src=" + src + ", srcPort=" + srcPort
                        + ", dst=" + dst + ", dstPort=" + dstPort
                        + ", type=" + type + "]";
            } else if (operation == UpdateOperation.LINK_UPDATED) {
                operationString = "Link Updated";
                return "LDUpdate [operation=" + operationString +
                        ", src=" + src + ", srcPort=" + srcPort
                        + ", dst=" + dst + ", dstPort=" + dstPort
                        + ", type=" + type + "]";
            } else if (operation == UpdateOperation.PORT_DOWN) {
                operationString = "Port Down";
                return "LDUpdate [operation=" + operationString +
                        ", src=" + src + ", srcPort=" + srcPort + "]";
            } else if (operation == UpdateOperation.PORT_UP) {
                operationString = "Port Up";
                return "LDUpdate [operation=" + operationString +
                        ", src=" + src + ", srcPort=" + srcPort + "]";
            } else if (operation == UpdateOperation.SWITCH_REMOVED) {
                operationString = "Switch Removed";
                return "LDUpdate [operation=" + operationString +
                        ", src=" + src + "]";
            } else if (operation == UpdateOperation.SWITCH_UPDATED) {
                operationString = "Switch Updated";
                return "LDUpdate [operation=" + operationString +
                        ", src=" + src + "]";
            } else {
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
