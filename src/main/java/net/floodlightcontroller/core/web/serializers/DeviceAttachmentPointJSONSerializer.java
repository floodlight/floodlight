package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class DeviceAttachmentPointJSONSerializer extends JsonSerializer<DeviceAttachmentPoint> {
    @Override
    public void serialize(DeviceAttachmentPoint dap, JsonGenerator jgen, SerializerProvider sp) 
            throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("switch", dap.getSwitchPort().getSw().getStringId());
        jgen.writeNumberField("port", dap.getSwitchPort().getPort());
        jgen.writeStringField("last-seen", dap.getLastSeen().toString());
        jgen.writeEndObject();
    }
    
    @Override
    public Class<DeviceAttachmentPoint> handledType() {
        return DeviceAttachmentPoint.class;
    }
}
