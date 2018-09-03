package net.floodlightcontroller.statistics.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.statistics.FlowRuleStats;

public class FlowRuleStatsSerializer extends JsonSerializer<FlowRuleStats>{

	@Override
	public void serialize(FlowRuleStats frs, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {

		jGen.writeStartObject();
		jGen.writeStringField("Dpid", String.valueOf(frs.getDpid()));
		jGen.writeStringField("Bytes", String.valueOf(frs.getByteCount().getValue()));
		jGen.writeStringField("Packets", String.valueOf(frs.getPacketCount().getValue()));
		jGen.writeStringField("Priority", String.valueOf(frs.getPriority()));
		jGen.writeStringField("Hard Timeout", String.valueOf(frs.getIdleTimeout()));
		jGen.writeStringField("Idle Timeout", String.valueOf(frs.getHardTimeout()));
		jGen.writeStringField("Duration in seconds", String.valueOf(frs.getDurationSec()));
		jGen.writeEndObject();
	}
}