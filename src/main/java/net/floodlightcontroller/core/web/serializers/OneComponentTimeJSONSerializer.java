package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;

import net.floodlightcontroller.perfmon.OneComponentTime;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

public class OneComponentTimeJSONSerializer
                                    extends JsonSerializer<OneComponentTime> {
    /**
     * Performs the serialization of a OneComponentTime object
     */
   @Override
   public void serialize(OneComponentTime oct, 
                   JsonGenerator jGen,
                   SerializerProvider serializer) 
                   throws IOException, JsonProcessingException {
       jGen.writeStartObject();
       jGen.writeStringField("module-name", oct.getCompName());
       jGen.writeNumberField("num-packets", oct.getPktCnt());
       jGen.writeNumberField("total", oct.getSumProcTimeNs());
       jGen.writeNumberField("average", oct.getAvgProcTimeNs());
       jGen.writeNumberField("max", oct.getMaxProcTimeNs());
       jGen.writeNumberField("min", oct.getMinProcTimeNs());
       jGen.writeNumberField("std-dev", oct.getSigmaProcTimeNs());
       jGen.writeNumberField("average-squared",  oct.getSumSquaredProcTimeNs());
       jGen.writeEndObject();
   }

   /**
    * Tells SimpleModule that we are the serializer for OFMatch
    */
   @Override
   public Class<OneComponentTime> handledType() {
       return OneComponentTime.class;
   }
}

