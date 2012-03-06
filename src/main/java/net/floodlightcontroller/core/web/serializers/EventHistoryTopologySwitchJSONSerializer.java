package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologySwitch;
import net.floodlightcontroller.packet.IPv4;

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

public class EventHistoryTopologySwitchJSONSerializer
                        extends JsonSerializer<EventHistoryTopologySwitch> {
    /**
     * Performs the serialization of a EventHistory Topology-Switch object
     */
   @Override
   public void serialize(EventHistoryTopologySwitch topoSw,
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       jGen.writeStartObject();
       jGen.writeStringField("Switch", HexString.toHexString(topoSw.dpid));
       jGen.writeStringField("Port",
               "0x"+Integer.toHexString((int)(topoSw.l4Port)).substring(4));
       jGen.writeStringField("IpAddr", 
               IPv4.fromIPv4Address(IPv4.toIPv4Address(topoSw.ipv4Addr)));
       jGen.writeStringField("Reason", topoSw.reason);
       jGen.writeEndObject();
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<EventHistoryTopologySwitch> handledType() {
       return EventHistoryTopologySwitch.class;
   }
}