package net.floodlightcontroller.util;

import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;

public class OFMatchWithSwDpid {
	private Match match;
	private DatapathId dpid;
	
	public OFMatchWithSwDpid(Match match, DatapathId dpid) {
		this.match = match;
		this.dpid = dpid;
	}
	public OFMatchWithSwDpid() {
		this.match = null;
		this.dpid = DatapathId.NONE;
	}
	
	public Match getMatch() {
		return match;
	}
	
	public void setMatch(Match match) {
		this.match = match;
	}
	
	public DatapathId getDpid() {
		return dpid;
	}
	
	public void setDpid(DatapathId dpid) {
		this.dpid = dpid;
	}
	
}
