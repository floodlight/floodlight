package net.floodlightcontroller.perfmon;

import java.io.IOException;
import java.sql.Timestamp;


import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class CumulativeTimeBucketJSONSerializer
                                extends JsonSerializer<CumulativeTimeBucket> {
    /**
     * Performs the serialization of a OneComponentTime object
     */
   @Override
   public void serialize(CumulativeTimeBucket ctb,
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       jGen.writeStartObject();
       Timestamp ts = new Timestamp(ctb.getStartTimeNs()/1000000);
       jGen.writeStringField("start-time", ts.toString());
       jGen.writeStringField("current-time", 
         new Timestamp(System.currentTimeMillis()).toString());
       jGen.writeNumberField("total-packets", ctb.getTotalPktCnt());
       jGen.writeNumberField("average", ctb.getAverageProcTimeNs());
       jGen.writeNumberField("min", ctb.getMinTotalProcTimeNs());
       jGen.writeNumberField("max", ctb.getMaxTotalProcTimeNs());
       jGen.writeNumberField("std-dev", ctb.getTotalSigmaProcTimeNs());
       jGen.writeArrayFieldStart("modules");
       for (OneComponentTime oct : ctb.getModules()) {
           serializer.defaultSerializeValue(oct, jGen);
       }
       jGen.writeEndArray();
       jGen.writeEndObject();
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<CumulativeTimeBucket> handledType() {
       return CumulativeTimeBucket.class;
   }
}
