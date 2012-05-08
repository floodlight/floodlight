package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.topology.NodePortTuple;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.openflow.util.HexString;

public class NodePortTupleJSONSerializer extends JsonSerializer<NodePortTuple> {

    @Override
    public void serialize(NodePortTuple ntp, JsonGenerator jgen,
                          SerializerProvider sp) throws IOException,
                                                  JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("switch", HexString.toHexString(ntp.getNodeId()));
        jgen.writeNumberField("port", ntp.getPortId());
        jgen.writeEndObject();
    }

    @Override
    public Class<NodePortTuple> handledType() {
        return NodePortTuple.class;
    }
}
