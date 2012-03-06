package net.floodlightcontroller.topology;

public class NodePair {
    private long min;
    private long max;

    public NodePair(long a, long b) {
        if (a < b) {
            min = a; 
            max = b;
        } else {
            min = b;
            max = a;
        }
    }

    public long getNode() {
        return min;
    }

    public long getOtherNode() {
        return max;
    }

    public String toString() {
        return "[" + new Long(min) + ", " + new Long(max) + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (max ^ (max >>> 32));
        result = prime * result + (int) (min ^ (min >>> 32));
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
        NodePair other = (NodePair) obj;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        return true;
    }
}
