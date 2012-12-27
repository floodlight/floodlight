package net.floodlightcontroller.loadbalancer;

import java.io.IOException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class LBVipSerializer extends JsonSerializer<LBVip>{

    @Override
    public void serialize(LBVip vip, JsonGenerator jGen,
                          SerializerProvider serializer) throws IOException,
                                                  JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("name", vip.name);
        jGen.writeStringField("id", vip.id);
        jGen.writeStringField("address", String.valueOf(vip.address));
        jGen.writeStringField("protocol", Byte.toString(vip.protocol));
        jGen.writeStringField("port", Short.toString(vip.port));

        jGen.writeEndObject();
    }

    
}
