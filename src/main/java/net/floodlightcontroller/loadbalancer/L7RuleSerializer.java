package net.floodlightcontroller.loadbalancer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class L7RuleSerializer extends JsonSerializer<L7Rule>{

	@Override
	public void serialize(L7Rule l7_rule, JsonGenerator jGen, SerializerProvider serializer)
			throws IOException, JsonProcessingException {
		jGen.writeStartObject();

		jGen.writeStringField("type", String.valueOf(l7_rule.type));
		jGen.writeStringField("value", l7_rule.value);
		jGen.writeStringField("id", l7_rule.id);
		jGen.writeStringField("policyId", l7_rule.policyId);

		jGen.writeEndObject();
	}

}
