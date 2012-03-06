package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.linkdiscovery.LinkTuple;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.openflow.util.HexString;

public class LinkTupleSerializer extends JsonSerializer<LinkTuple> {

    /**
     * Serializes the @LinkTuple object.
     * @param linkTuple The LinkTuple to serialize
     * @param jGen The JSON Generator to use
     * @param serializer The Jackson serializer provider
     */
    @Override
    public void serialize(LinkTuple linkTuple, JsonGenerator jGen, SerializerProvider serializer) throws IOException, JsonProcessingException {
        jGen.writeStartObject();
        jGen.writeNumberField("dst-port", linkTuple.getDst().getPort());
        jGen.writeStringField("dst-switch", HexString.toHexString(linkTuple.getDst().getSw().getId()));
        jGen.writeNumberField("src-port", linkTuple.getSrc().getPort());
        jGen.writeStringField("src-switch", HexString.toHexString(linkTuple.getSrc().getSw().getId()));
        jGen.writeEndObject();
    }
    
    /**
     * Tells SimpleModule that we are the serializer for @LinkTuple
     */
    @Override
    public Class<LinkTuple> handledType() {
        return LinkTuple.class;
    }
}
