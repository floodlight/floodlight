package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.devicemanager.internal.EventHistoryAttachmentPoint;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.openflow.util.HexString;

/**
 * 
 * @author subrata
 *
 */

public class EventHistoryAttachmentPointJSONSerializer 
                        extends JsonSerializer<EventHistoryAttachmentPoint> {
    /**
     * Performs the serialization of a EventHistory.BaseInfo object
     */
   @Override
   public void serialize(EventHistoryAttachmentPoint attachPt, 
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       jGen.writeStartObject();
       jGen.writeStringField("Switch", HexString.toHexString(attachPt.dpid));
       jGen.writeStringField("Host",   HexString.toHexString(attachPt.mac));
       jGen.writeNumberField("Port",   attachPt.port);
       jGen.writeNumberField("VLAN",   attachPt.vlan);
       jGen.writeStringField("Reason", attachPt.reason);
       jGen.writeEndObject();
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<EventHistoryAttachmentPoint> handledType() {
       return EventHistoryAttachmentPoint.class;
   }
}
