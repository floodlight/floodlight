package net.floodlightcontroller.core.web;

import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFStatsReply;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = StatsReplySerializer.class)
public class StatsReplyToJson {
	private Map<String, List<OFStatsReply>> toserialize;
	
	StatsReplyToJson(Map<String, List<OFStatsReply>> serializeMe) {
		toserialize = serializeMe;
	}
}
