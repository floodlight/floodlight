package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.devicemanager.DeviceNetworkAddress;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class DeviceJSONSerializer extends JsonSerializer<Device> {

    @Override
    public void serialize(Device device, JsonGenerator jgen, SerializerProvider sp)
            throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("last-seen", device.getLastSeen().toString());
        if (device.getVlanId() != null)
            jgen.writeNumberField("vlan", device.getVlanId());
        else
            jgen.writeNullField("vlan");
        jgen.writeArrayFieldStart("network-addresses");
        for (DeviceNetworkAddress dna : device.getNetworkAddresses()) {
            sp.defaultSerializeValue(dna, jgen);
        }
        jgen.writeEndArray();
        jgen.writeArrayFieldStart("attachment-points");
        for (DeviceAttachmentPoint dap : device.getAttachmentPoints()) {
            sp.defaultSerializeValue(dap, jgen);
        }
        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    @Override
    public Class<Device> handledType() {
        return Device.class;
    }
}
