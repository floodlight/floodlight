package net.floodlightcontroller.dhcpserver.web;

import java.io.IOException;

import net.floodlightcontroller.dhcpserver.DHCPInstance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class DHCPInstanceDeserializer extends JsonDeserializer<DHCPInstance> {

	@Override
	public DHCPInstance deserialize(JsonParser jParser, DeserializationContext dserCntx)
			throws IOException, JsonProcessingException {
		
		return null;
	}
}