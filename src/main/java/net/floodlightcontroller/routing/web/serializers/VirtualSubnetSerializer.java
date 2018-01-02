package net.floodlightcontroller.routing.web.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.floodlightcontroller.routing.VirtualSubnet;

import java.io.IOException;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/31/17
 */
public class VirtualSubnetSerializer extends JsonSerializer<VirtualSubnet> {
    @Override
    public void serialize(VirtualSubnet virtualSubnet, JsonGenerator jsonGen, SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {
        jsonGen.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);

        jsonGen.writeStartObject();
        jsonGen.writeStringField("subnet-name", virtualSubnet.getName());
        jsonGen.writeStringField("gateway-ip", virtualSubnet.getGatewayIP().toString());
        jsonGen.writeObjectField("subnetDPIDs", virtualSubnet.getSubnetDPIDs().toString());
        jsonGen.writeObjectField("subnetNPTs", virtualSubnet.getSubnetNPTs().toString());
        jsonGen.writeEndObject();
    }
}
