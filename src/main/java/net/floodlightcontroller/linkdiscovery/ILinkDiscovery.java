package net.floodlightcontroller.linkdiscovery;

public interface ILinkDiscovery {

    public static enum UpdateOperation {ADD_OR_UPDATE, REMOVE, SWITCH_UPDATED};

    public class LDUpdate {
        protected long src;
        protected short srcPort;
        protected int srcPortState;
        protected long dst;
        protected short dstPort;
        protected int dstPortState;
        protected SwitchType srcType;
        protected LinkType type;
        protected UpdateOperation operation;

        public LDUpdate(long src, short srcPort, int srcPortState,
                      long dst, short dstPort, int dstPortState,
                      ILinkDiscovery.LinkType type,
                      UpdateOperation operation) {
            this.src = src;
            this.srcPort = srcPort;
            this.srcPortState = srcPortState;
            this.dst = dst;
            this.dstPort = dstPort;
            this.dstPortState = dstPortState;
            this.type = type;
            this.operation = operation;
        }

        public LDUpdate(LinkTuple lt, int srcPortState,
                      int dstPortState, ILinkDiscovery.LinkType type, UpdateOperation operation) {
            this(lt.getSrc().getSw().getId(), lt.getSrc().getPort(),
                 srcPortState, lt.getDst().getSw().getId(), lt.getDst().getPort(),
                 dstPortState, type, operation);
        }

        // For updtedSwitch(sw)
        public LDUpdate(long switchId, SwitchType stype) {
            this.operation = UpdateOperation.SWITCH_UPDATED;
            this.src = switchId;
            this.srcType = stype;
        }

        public long getSrc() {
            return src;
        }

        public short getSrcPort() {
            return srcPort;
        }

        public int getSrcPortState() {
            return srcPortState;
        }

        public long getDst() {
            return dst;
        }

        public short getDstPort() {
            return dstPort;
        }

        public int getDstPortState() {
            return dstPortState;
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

        @Override
        public String toString() {
            return "LDUpdate [src=" + src + ", srcPort=" + srcPort
                   + ", srcPortState=" + srcPortState + ", dst=" + dst
                   + ", dstPort=" + dstPort + ", dstPortState=" + dstPortState
                   + ", srcType=" + srcType + ", type=" + type + ", operation="
                   + operation + "]";
        }
    }

    public enum SwitchType {
        BASIC_SWITCH, CORE_SWITCH
    };

    public enum LinkType {
        INVALID_LINK, DIRECT_LINK, MULTIHOP_LINK, TUNNEL
    };
}
