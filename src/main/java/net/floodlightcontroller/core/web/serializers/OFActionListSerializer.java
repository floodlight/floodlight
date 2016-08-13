package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.List;

import net.floodlightcontroller.util.ActionUtils;

import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize any List of OFAction in JSON.
 * 
 * Use automatically by Jackson via JsonSerialize(using=OFActionListSerializer.class),
 * or use the static function within this class within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class OFActionListSerializer extends JsonSerializer<List<OFAction>> {
    protected static Logger logger = LoggerFactory.getLogger(OFActionListSerializer.class);
	
	@Override
	public void serialize(List<OFAction> actions, JsonGenerator jGen, SerializerProvider serializer) throws IOException,
			JsonProcessingException {
		jGen.writeStartObject();
		serializeActions(jGen, actions);
		jGen.writeEndObject();
	}
    
	/**
     * Write a JSON string given a list of OFAction. Supports OF1.0 - OF1.3.
     * This is the only place actions are serialized, for any OF version. Because
     * some OF version share actions, it makes sense to have them in one place.
     * @param jsonGenerator
     * @param actions
     * @throws IOException
     * @throws JsonProcessingException
     */
    public static void serializeActions(JsonGenerator jsonGenerator, List<OFAction> actions) throws IOException, JsonProcessingException {
        if (actions.isEmpty()) {
            jsonGenerator.writeStringField("none", "drop");
        } else {
        	jsonGenerator.writeStringField("actions", ActionUtils.actionsToString(actions)); //TODO update to write each action in JSON
        }
    }
}