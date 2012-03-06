package net.floodlightcontroller.topology;

public class OrderedNodePair {
    private long src;
    private long dst;

    public OrderedNodePair(long s, long d) {
        src = s;
        dst = d;
    }

    public long getSrc() {
        return src;
    }

    public long getDst() {
        return dst;
    }

    @Override
    public int hashCode() {
        final int prime = 2417;
        int result = 1;
        result = prime * result + (int) (dst ^ (dst >>> 32));
        result = prime * result + (int) (src ^ (src >>> 32));
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
        OrderedNodePair other = (OrderedNodePair) obj;
        if (dst != other.dst)
            return false;
        if (src != other.src)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "OrderedNodePair [src=" + src + ", dst=" + dst + "]";
    }
}
