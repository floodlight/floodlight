package net.floodlightcontroller.loadbalancer;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class LBMemberSerializer extends JsonSerializer<LBMember>{

    @Override
    public void serialize(LBMember member, JsonGenerator jGen,
                          SerializerProvider serializer) throws IOException,
                                                  JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("id", member.id);
        jGen.writeStringField("address", String.valueOf(member.address));
        jGen.writeStringField("port", Short.toString(member.port));
        jGen.writeStringField("poolId", member.poolId);
        jGen.writeStringField("vipId", member.vipId);

        jGen.writeEndObject();        
    }

}
