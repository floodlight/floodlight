package org.openflow.protocol;

import org.openflow.util.HexString;

public class OFMatchWithSwDpid {
    protected OFMatch ofMatch;
    protected long  switchDataPathId;

    public OFMatchWithSwDpid() {
    	this.ofMatch = new OFMatch();
    	this.switchDataPathId = 0;
    }
    
    public OFMatchWithSwDpid(OFMatch ofm, long swDpid) {
    	this.ofMatch = ofm.clone();
    	this.switchDataPathId = swDpid;
    }
    public OFMatch getOfMatch() {
		return ofMatch;
	}

	public void setOfMatch(OFMatch ofMatch) {
		this.ofMatch = ofMatch.clone();
	}

	public long getSwitchDataPathId() {
        return this.switchDataPathId;
    }

    public OFMatchWithSwDpid setSwitchDataPathId(long dpid) {
        this.switchDataPathId = dpid;
        return this;
    }
    
    @Override
    public String toString() {
        return "OFMatchWithSwDpid [" + HexString.toHexString(switchDataPathId) + ofMatch + "]";
    }
}
