package net.floodlightcontroller.core.types;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * Simple classes for defining a hop in a path.
 * 
 * Class PathNode good for tracking hops in
 * an outer container that guarantees ordering
 * or for standalone use. It can also be useful
 * when lookup of specific nodes needs to be
 * faster than O(n)... lookup time dependent on 
 * containing class.
 * 
 * Inner class LinkedPathNode useful for chaining
 * LinkedPathNodes together. Note traversal will
 * always be O(n), since it's effectively a linked
 * list.
 * 
 * @author rizard
 *
 */
public class PathNode {
    private DatapathId node;
    private OFPort in;
    private OFPort out;
    private int hopId;

    private PathNode() { }

    private PathNode(DatapathId node, OFPort in, OFPort out, int hopId) {
        this.node = node;
        this.in = in;
        this.out = out;
        this.hopId = hopId;
    }

    public static PathNode of(DatapathId node, OFPort in, OFPort out, int hopId) {
        return new PathNode(node, in, out, hopId);
    }

    public DatapathId getNode() {
        return node;
    }

    public OFPort getInPort() {
        return in;
    }

    public OFPort getOutPort() {
        return out;
    }

    public int getHopId() {
        return hopId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + hopId;
        result = prime * result + ((in == null) ? 0 : in.hashCode());
        result = prime * result + ((node == null) ? 0 : node.hashCode());
        result = prime * result + ((out == null) ? 0 : out.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PathNode other = (PathNode) obj;
        if (hopId != other.hopId)
            return false;
        if (in == null) {
            if (other.in != null)
                return false;
        } else if (!in.equals(other.in))
            return false;
        if (node == null) {
            if (other.node != null)
                return false;
        } else if (!node.equals(other.node))
            return false;
        if (out == null) {
            if (other.out != null)
                return false;
        } else if (!out.equals(other.out))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "PathNode [node=" + node + ", in=" + in + ", out=" + out + ", hopId=" + hopId + "]";
    }

    public static class LinkedPathNode extends PathNode {
        private LinkedPathNode prev;
        private LinkedPathNode next;

        private LinkedPathNode() { 
            super(); 
        }

        private LinkedPathNode(DatapathId node, OFPort in, OFPort out, 
                int hopId, LinkedPathNode prev, LinkedPathNode next) {
            super(node, in, out, hopId);
            this.prev = prev;
            this.next = next;
        }

        public static LinkedPathNode of(DatapathId node, OFPort in, OFPort out, 
                int hopId, LinkedPathNode prev, LinkedPathNode next) {
            return new LinkedPathNode(node, in, out, hopId, prev, next);
        }

        public static LinkedPathNode of(PathNode n, LinkedPathNode prev, LinkedPathNode next) {
            return new LinkedPathNode(n.node, n.in, n.out, n.hopId, prev, next);
        }

        public LinkedPathNode getPrevious() {
            return prev;
        }

        public LinkedPathNode getNext() {
            return next;
        }

        public boolean isStart() {
            if (prev == null && next != null) {
                return true;
            }
            return false;
        }

        public boolean isEnd() {
            if (prev != null && next == null) {
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((next == null) ? 0 : next.hashCode());
            result = prime * result + ((prev == null) ? 0 : prev.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (getClass() != obj.getClass())
                return false;
            LinkedPathNode other = (LinkedPathNode) obj;
            if (next == null) {
                if (other.next != null)
                    return false;
            } else if (!next.equals(other.next))
                return false;
            if (prev == null) {
                if (other.prev != null)
                    return false;
            } else if (!prev.equals(other.prev))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "LinkedPathNode [prev=" + prev + ", " 
                    + super.toString() + ", next=" + next + "]";
        }
    }
}
