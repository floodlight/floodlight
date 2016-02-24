package net.floodlightcontroller.staticflowentry.web;

import java.io.IOException;
import java.util.Map;

import net.floodlightcontroller.core.web.serializers.OFFlowModSerializer;

import org.projectfloodlight.openflow.protocol.OFFlowMod;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * This is a helper-serializer class for use by the Static Flow Pusher.
 * The SFP outputs a DPID-keyed map with values of a flow-name-keyed map,
 * which then contains the OFFlowMods that need to be serialized.
 * 
 * OFFlowModSerializer is written separately, since I have a feeling it
 * might come in handy to other modules needing to write an OFFlowMod
 * in JSON.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 *
 */
public class OFFlowModMapSerializer extends JsonSerializer<OFFlowModMap> {

	@Override
	public void serialize(OFFlowModMap fmm, JsonGenerator jGen, SerializerProvider serializer)
			throws IOException, JsonProcessingException {
		
        jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true); // IMHO this just looks nicer and is easier to read if everything is quoted

		if (fmm == null) {
			jGen.writeStartObject();
			jGen.writeString("No flows have been added to the Static Flow Pusher.");
			jGen.writeEndObject();
			return;
		}

		Map<String, Map<String, OFFlowMod>> theMap = fmm.getMap();

		jGen.writeStartObject();
		if (theMap.keySet() != null) {
			for (String dpid : theMap.keySet()) {
				if (theMap.get(dpid) != null) {
					jGen.writeArrayFieldStart(dpid);
					for (String name : theMap.get(dpid).keySet()) {
						jGen.writeStartObject();
						jGen.writeFieldName(name);
						OFFlowModSerializer.serializeFlowMod(jGen, theMap.get(dpid).get(name));
						jGen.writeEndObject();
					}    
					jGen.writeEndArray();
				}
			}
		}
		jGen.writeEndObject();
	}
}
