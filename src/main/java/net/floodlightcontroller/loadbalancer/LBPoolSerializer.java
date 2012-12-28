package net.floodlightcontroller.loadbalancer;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class LBPoolSerializer extends JsonSerializer<LBPool>{

    @Override
    public void serialize(LBPool pool, JsonGenerator jGen,
                          SerializerProvider serializer) throws IOException,
                                                  JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("name", pool.name);
        jGen.writeStringField("id", pool.id);
        jGen.writeStringField("vipId", pool.vipId);

        for (int i=0; i<pool.members.size(); i++)
            jGen.writeStringField("pool", pool.members.get(i));

        jGen.writeEndObject();   
    }

}
