package net.floodlightcontroller.flowcache;

public class PendingSwRespKey {
    long swDpid;
    int  transId;

    public PendingSwRespKey(long swDpid, int transId) {
        this.swDpid  = swDpid;
        this.transId = transId;
    }

    @Override
    public int hashCode() {
        final int prime = 97;
        Long dpid   = swDpid;
        Integer tid = transId;
        return (tid.hashCode()*prime + dpid.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof PendingSwRespKey)) {
            return false;
        }
        PendingSwRespKey other = (PendingSwRespKey) obj;
        if ((swDpid != other.swDpid) || (transId != other.transId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return Long.toHexString(swDpid)+","+Integer.toString(transId);
    }
}
