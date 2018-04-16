package net.floodlightcontroller.routing.web.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.floodlightcontroller.routing.VirtualGateway;
import net.floodlightcontroller.routing.VirtualGatewayInterface;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/31/17
 */
public class VirtualGatewaySerializer extends JsonSerializer<VirtualGateway> {
    @Override
    public void serialize(VirtualGateway gateway, JsonGenerator jsonGen, SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {
        jsonGen.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);

        jsonGen.writeStartObject();
        jsonGen.writeStringField("gateway-name", gateway.getName());
        jsonGen.writeStringField("gateway-mac", gateway.getGatewayMac().toString());
//        jsonGen.writeObjectField("interfaces", gateway.getInterfaces());

        if (gateway.getInterfaces() != null) {
            jsonGen.writeArrayFieldStart("virtual-interfaces");
            for (VirtualGatewayInterface inft : gateway.getInterfaces()) {
                jsonGen.writeString(inft.getInterfaceName());
                jsonGen.writeString(inft.getMac().toString());
                jsonGen.writeString(inft.getIp().toString());
                jsonGen.writeString(inft.getMask().toString());
            }
            jsonGen.writeEndArray();
        }

        jsonGen.writeObjectField("subnets", gateway.getSubnets());
        jsonGen.writeEndObject();

    }
}
