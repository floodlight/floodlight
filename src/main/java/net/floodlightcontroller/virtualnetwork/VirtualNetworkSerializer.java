package net.floodlightcontroller.virtualnetwork;

import java.io.IOException;
import java.util.Iterator;

import net.floodlightcontroller.util.MACAddress;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

/**
 * Serialize a VirtualNetwork object
 * @author KC Wang
 */
public class VirtualNetworkSerializer extends JsonSerializer<VirtualNetwork> {

    @Override
    public void serialize(VirtualNetwork vNet, JsonGenerator jGen,
            SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("name", vNet.name);
        jGen.writeStringField("guid", vNet.guid);
        jGen.writeStringField("gateway", vNet.gateway);

        jGen.writeArrayFieldStart("mac");
        Iterator<MACAddress> hit = vNet.hosts.iterator();
        while (hit.hasNext())
            jGen.writeString(hit.next().toString());
        jGen.writeEndArray();
        
        jGen.writeEndObject();
    }

}
