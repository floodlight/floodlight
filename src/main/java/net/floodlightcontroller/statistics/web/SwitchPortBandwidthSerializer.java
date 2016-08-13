package net.floodlightcontroller.statistics.web;

import java.io.IOException;
import java.util.Date;

import net.floodlightcontroller.statistics.SwitchPortBandwidth;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class SwitchPortBandwidthSerializer extends JsonSerializer<SwitchPortBandwidth> {

	@Override
	public void serialize(SwitchPortBandwidth spb, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
		jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);

		jGen.writeStartObject();
		jGen.writeStringField("dpid", spb.getSwitchId().toString());
		jGen.writeStringField("port", spb.getSwitchPort().toString());
		jGen.writeStringField("updated", new Date(spb.getUpdateTime()).toString());
		jGen.writeStringField("link-speed-bits-per-second", spb.getLinkSpeedBitsPerSec().getBigInteger().toString());
		jGen.writeStringField("bits-per-second-rx", spb.getBitsPerSecondRx().getBigInteger().toString());
		jGen.writeStringField("bits-per-second-tx", spb.getBitsPerSecondTx().getBigInteger().toString());
		jGen.writeEndObject();
	}

}