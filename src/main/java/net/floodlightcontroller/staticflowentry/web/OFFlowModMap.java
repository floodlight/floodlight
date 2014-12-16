package net.floodlightcontroller.staticflowentry.web;

import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowMod;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=OFFlowModMapSerializer.class) 
public class OFFlowModMap {

	/*
	 * Contains the following double-mapping:
	 * Map<Switch-DPID-Str, Map<Flow-Name-Str, OFFlowMod>>
	 */
	private Map<String, Map<String, OFFlowMod>> theMap;
	
	public OFFlowModMap (Map<String, Map<String, OFFlowMod>> theMap) {
		this.theMap = theMap;
	}
	
	public Map<String, Map<String, OFFlowMod>> getMap() {
		return theMap;
	}
}
