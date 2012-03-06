package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.linkdiscovery.internal.EventHistoryTopologyCluster;

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

public class EventHistoryTopologyClusterJSONSerializer
                        extends JsonSerializer<EventHistoryTopologyCluster> {
    /**
     * Performs the serialization of a EventHistory Topology-Switch object
     */
   @Override
   public void serialize(EventHistoryTopologyCluster topoCluster,
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       jGen.writeStartObject();
       jGen.writeStringField("Switch",
               HexString.toHexString(topoCluster.dpid));
       jGen.writeStringField("OldClusterId", 
               HexString.toHexString(topoCluster.clusterIdOld));
       jGen.writeStringField("NewClusterId", 
               HexString.toHexString(topoCluster.clusterIdNew));
       jGen.writeStringField("Reason", topoCluster.reason);
       jGen.writeEndObject();
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<EventHistoryTopologyCluster> handledType() {
       return EventHistoryTopologyCluster.class;
   }
}