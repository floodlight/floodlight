package net.floodlightcontroller.routing.web.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.floodlightcontroller.routing.VirtualGatewayInterface;

import java.io.IOException;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/31/17
 */
public class VirtualInterfaceSerializer extends JsonSerializer<VirtualGatewayInterface> {
    @Override
    public void serialize(VirtualGatewayInterface virtualGatewayInterface, JsonGenerator jsonGen, SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {
        jsonGen.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);

        jsonGen.writeStartObject();
        jsonGen.writeStringField("interface-name", virtualGatewayInterface.getInterfaceName());
        jsonGen.writeStringField("interface-ip", virtualGatewayInterface.getIp().toString());
        jsonGen.writeStringField("interface-mask", virtualGatewayInterface.getMask().toString());
        jsonGen.writeEndObject();
    }
}
