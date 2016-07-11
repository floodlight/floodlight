package net.floodlightcontroller.staticentry.web;

import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=SFPEntryMapSerializer.class) 
public class SFPEntryMap {

	/*
	 * Contains the following double-mapping:
	 * Map<Switch-DPID-Str, Map<Entry-Name-Str, OFMessage>>
	 */
	private Map<String, Map<String, OFMessage>> theMap;
	
	public SFPEntryMap (Map<String, Map<String, OFMessage>> theMap) {
		this.theMap = theMap;
	}
	
	public Map<String, Map<String, OFMessage>> getMap() {
		return theMap;
	}
}
