package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.sql.Timestamp;

import net.floodlightcontroller.perfmon.CumulativeTimeBucket;
import net.floodlightcontroller.perfmon.OneComponentTime;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class PerfMonCumulativeTimeBucketJSONSerializer
                                extends JsonSerializer<CumulativeTimeBucket> {
    /**
     * Performs the serialization of a OneComponentTime object
     */
   @Override
   public void serialize(CumulativeTimeBucket cTB,
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       // Skip if the number of packets processed is zero
       if (cTB.getTotalPktCnt() != 0) {
           jGen.writeStartObject();
           jGen.writeNumberField("BktNo",     cTB.getBucketNo());
           Timestamp ts = new Timestamp(cTB.getStartTime_ms());
           String tsStr = ts.toString();
           while (tsStr.length() < 23) {
               tsStr = tsStr.concat("0");
           }
           jGen.writeStringField("StartTime", tsStr);
           jGen.writeNumberField("Duration",  cTB.getDuration_s());
           jGen.writeNumberField("TotPkts",   cTB.getTotalPktCnt());
           jGen.writeNumberField("Avg",       cTB.getAvgTotalProcTime_us());
           jGen.writeNumberField("Min",       cTB.getMinTotalProcTime_us());
           jGen.writeNumberField("Max",       cTB.getMaxTotalProcTime_us());
           jGen.writeNumberField("StdDev",    cTB.getSigmaTotalProcTime_us());
           int numComps = cTB.getNumComps();
           for (int idx=0; idx < numComps; idx++) {
               OneComponentTime oCT = cTB.getTComps().getOneComp()[idx];
               if (oCT.getPktCnt() != 0) {
                   serializer.defaultSerializeField(
                                           Integer.toString(idx), oCT, jGen);
               }
           }
           
           jGen.writeEndObject();
       }
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<CumulativeTimeBucket> handledType() {
       return CumulativeTimeBucket.class;
   }
}
