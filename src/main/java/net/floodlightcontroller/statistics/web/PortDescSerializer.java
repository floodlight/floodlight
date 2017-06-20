package net.floodlightcontroller.statistics.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import net.floodlightcontroller.statistics.PortDesc;

public class PortDescSerializer extends JsonSerializer<PortDesc> {

	@Override
	public void serialize(PortDesc pd, JsonGenerator jGen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {
		
		jGen.writeStartObject();
		jGen.writeStringField("dpid", pd.getSwitchId().toString());
		jGen.writeStringField("port", pd.getSwitchPort().toString());
		jGen.writeStringField("name", pd.getName());
		jGen.writeStringField("state", pd.getState().toString());
		jGen.writeStringField("config", pd.getConfig().toString());
		jGen.writeStringField("is enabled?", String.valueOf(pd.isUp()));
		jGen.writeEndObject();
		
	}
}