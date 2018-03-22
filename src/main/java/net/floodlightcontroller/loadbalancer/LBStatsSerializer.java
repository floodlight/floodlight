package net.floodlightcontroller.loadbalancer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class LBStatsSerializer extends JsonSerializer<LBStats>{

	@Override
	public void serialize(LBStats poolStats, JsonGenerator jGen,
			SerializerProvider serializer) throws IOException,
	JsonProcessingException {
		jGen.writeStartObject();

		jGen.writeStringField("Bytes In", String.valueOf(poolStats.bytesIn));
		jGen.writeStringField("Bytes Out", String.valueOf(poolStats.bytesOut));
		jGen.writeStringField("Active Flows", String.valueOf(poolStats.activeFlows));
		//jGen.writeStringField("Total Flows", String.valueOf(poolStats.totalFlows));

		jGen.writeEndObject();

	}
}