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
   public void serialize(CumulativeTimeBucket ctb,
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       // Skip if the number of packets processed is zero
       if (ctb.getTotalPktCnt() != 0) {
           jGen.writeStartObject();
           jGen.writeNumberField("BktNo",     ctb.getBucketNo());
           Timestamp ts = new Timestamp(ctb.getStartTimeNs());
           String tsStr = ts.toString();
           while (tsStr.length() < 23) {
               tsStr = tsStr.concat("0");
           }
           jGen.writeStringField("StartTime", tsStr);
           jGen.writeNumberField("Duration", ctb.getDurationS());
           jGen.writeNumberField("TotPkts", ctb.getTotalPktCnt());
           jGen.writeNumberField("Avg", ctb.getAverageProcTimeNs());
           jGen.writeNumberField("Min", ctb.getMinTotalProcTimeNs());
           jGen.writeNumberField("Max", ctb.getMaxTotalProcTimeNs());
           jGen.writeNumberField("StdDev", ctb.getTotalSigmaProcTimeNs());

           for (OneComponentTime oct : ctb.getComponentTimes()) {
               if (oct.getPktCnt() > 0) {
                   serializer.defaultSerializeField(oct.getCompName(), oct, jGen);
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
