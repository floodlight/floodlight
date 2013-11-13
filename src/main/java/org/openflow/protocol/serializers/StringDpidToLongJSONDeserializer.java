package org.openflow.protocol.serializers;

import java.io.IOException;

import org.openflow.util.HexString;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class StringDpidToLongJSONDeserializer extends
    JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser jsonParser,
                                       DeserializationContext cntx)
                                       throws IOException,
                                       JsonProcessingException {
        return Long.valueOf(HexString.toLong(jsonParser.getText()));
    }

}
