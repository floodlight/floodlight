package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.perfmon.OneComponentTime;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class PerfMonOneComponentTimeJSONSerializer
                                    extends JsonSerializer<OneComponentTime> {
    /**
     * Performs the serialization of a OneComponentTime object
     */
   @Override
   public void serialize(OneComponentTime oCT, 
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       // Skip if the number of packets processed is zero
       if (oCT.getPktCnt() != 0) {
           jGen.writeStartObject(); // Called from higher layer
           jGen.writeStringField("CompName", oCT.getCompName());
           jGen.writeNumberField("Pkts",     oCT.getPktCnt());
           jGen.writeNumberField("Avg",      oCT.getAvgProcTime_us());
           jGen.writeNumberField("Max",      oCT.getMaxProcTime_us());
           jGen.writeNumberField("Min",      oCT.getMinProcTime_us());
           jGen.writeNumberField("StdDev",   oCT.getSigmaProcTime_us());
           //jGen.writeNumberField("Sum",    oCT.getSumProcTime_us());
           //jGen.writeNumberField("SumSq",  oCT.getSumSquaredProcTime_us2());
           jGen.writeEndObject();
       }
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<OneComponentTime> handledType() {
       return OneComponentTime.class;
   }
}

