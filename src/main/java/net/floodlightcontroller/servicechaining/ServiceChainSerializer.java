package net.floodlightcontroller.servicechaining;

import java.io.IOException;
import java.util.Collection;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class ServiceChainSerializer extends JsonSerializer<ServiceChain> {

    @Override
    public void serialize(ServiceChain sc, JsonGenerator jgen,
                          SerializerProvider sp) throws IOException,
            JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("name", sc.getName());
        jgen.writeStringField("description", sc.getDescription());
        if (sc.getSourceBvs() != null) {
            jgen.writeStringField("source-BVS", sc.getSourceBvs());
        } else {
            jgen.writeStringField("source-BVS", "*");
        }
        if (sc.getDestinationBvs() != null) {
            jgen.writeStringField("destination-BVS", sc.getDestinationBvs());
        } else {
            jgen.writeStringField("destination-BVS", "*");
        }
        jgen.writeArrayFieldStart("service-nodes");
        Collection<ServiceNode> serviceNodes = sc.getServiceNodes();
        if (serviceNodes != null && !serviceNodes.isEmpty()) {
            for (ServiceNode sn : serviceNodes) {
                jgen.writeObject(sn);
            }
        }
        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    @Override
    public Class<ServiceChain> handledType() {
        return ServiceChain.class;
    }
}
