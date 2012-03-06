package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologyLink;

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

public class EventHistoryTopologyLinkJSONSerializer
                        extends JsonSerializer<EventHistoryTopologyLink> {
    /**
     * Performs the serialization of a EventHistory Topology-Switch object
     */
   @Override
   public void serialize(EventHistoryTopologyLink topoLnk,
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       jGen.writeStartObject();
       jGen.writeStringField("Source-Switch",
               HexString.toHexString(topoLnk.srcSwDpid));
       jGen.writeNumberField("SrcPort", topoLnk.srcSwport);
       jGen.writeStringField("Dest-Switch",
               HexString.toHexString(topoLnk.dstSwDpid));
       jGen.writeNumberField("DstPort", topoLnk.dstSwport);
       jGen.writeNumberField("SrcPortState", topoLnk.srcPortState);
       jGen.writeNumberField("DstPortState", topoLnk.dstPortState);
       jGen.writeStringField("Reason", topoLnk.reason);
       jGen.writeEndObject();
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<EventHistoryTopologyLink> handledType() {
       return EventHistoryTopologyLink.class;
   }
}