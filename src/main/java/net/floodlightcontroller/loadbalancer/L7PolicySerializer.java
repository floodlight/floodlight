package net.floodlightcontroller.loadbalancer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class L7PolicySerializer extends JsonSerializer<L7Policy> {

	@Override
	public void serialize(L7Policy l7_policy, JsonGenerator jGen, SerializerProvider serializer)
			throws IOException, JsonProcessingException {
		jGen.writeStartObject();
        
        jGen.writeStringField("action", String.valueOf(l7_policy.action));
        jGen.writeStringField("id", l7_policy.id);
        jGen.writeStringField("vipId", l7_policy.vipId);
        jGen.writeStringField("poolId", l7_policy.poolId);

        jGen.writeEndObject();
		
	}

}
