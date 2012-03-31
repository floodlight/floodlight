package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.devicemanager.DeviceNetworkAddress;
import net.floodlightcontroller.packet.IPv4;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class DeviceNetworkAddressJSONSerializer extends JsonSerializer<DeviceNetworkAddress> {

    @Override
    public void serialize(DeviceNetworkAddress dna, JsonGenerator jgen, SerializerProvider sp)
                                  throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("ip", IPv4.fromIPv4Address(dna.getNetworkAddress()));
        jgen.writeStringField("last-seen", dna.getLastSeen().toString());
        jgen.writeEndObject();
    }
    
    @Override
    public Class<DeviceNetworkAddress> handledType() {
        return DeviceNetworkAddress.class;
    }
}
