package net.floodlightcontroller.routing.web.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.VirtualGatewayInstance;
import net.floodlightcontroller.routing.VirtualGatewayInterface;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.io.IOException;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/31/17
 */
public class VirtualGatewaySerializer extends JsonSerializer<VirtualGatewayInstance> {
    @Override
    public void serialize(VirtualGatewayInstance gateway, JsonGenerator jsonGen, SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {
        jsonGen.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);

        jsonGen.writeStartObject();
        jsonGen.writeStringField("gateway-name", gateway.getName());
        jsonGen.writeStringField("gateway-mac", gateway.getGatewayMac().toString());

        if (gateway.getInterfaces() != null) {
            jsonGen.writeArrayFieldStart("virtual-interfaces");
            for (VirtualGatewayInterface inft : gateway.getInterfaces()) {
                jsonGen.writeString(inft.getInterfaceName());
                jsonGen.writeString(inft.getIp().toString());
                jsonGen.writeString(inft.getMask().toString());
            }
            jsonGen.writeEndArray();
        }

        if (gateway.getSwitchMembers() != null) {
            jsonGen.writeArrayFieldStart("switch-id");
            for (DatapathId dpid : gateway.getSwitchMembers()) {
                jsonGen.writeString(dpid.toString());
            }
            jsonGen.writeEndArray();
        }

        if (gateway.getNptMembers() != null) {
            jsonGen.writeArrayFieldStart("node-port-tuples");
            for (NodePortTuple npt : gateway.getNptMembers()) {
                jsonGen.writeString(npt.toString());
            }
            jsonGen.writeEndArray();
        }

        if (gateway.getSubsetMembers() != null) {
            jsonGen.writeArrayFieldStart("subnets");
            for (IPv4AddressWithMask subnet : gateway.getSubsetMembers()) {
                jsonGen.writeString(subnet.toString());
            }
            jsonGen.writeEndArray();
        }

        jsonGen.writeEndObject();

    }
}
