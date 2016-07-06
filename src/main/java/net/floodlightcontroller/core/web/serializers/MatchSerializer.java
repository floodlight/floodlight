package net.floodlightcontroller.core.web.serializers;

import java.io.IOException;
import java.util.Iterator;

import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serialize any Match in JSON.
 * 
 * Use automatically by Jackson via JsonSerialize(using=MatchSerializer.class),
 * or use the static function within this class within another serializer.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */
public class MatchSerializer extends JsonSerializer<Match> {
	protected static Logger logger = LoggerFactory.getLogger(OFActionListSerializer.class);

	@Override
	public void serialize(Match match, JsonGenerator jGen, SerializerProvider serializer) throws IOException,
	JsonProcessingException {
		serializeMatch(jGen, match);
	}
	
	@SuppressWarnings("unchecked") 
	public static String matchValueToString(Match m, @SuppressWarnings("rawtypes") MatchField mf) {
		return m.isPartiallyMasked(mf) ? m.getMasked(mf).toString() : m.get(mf).toString();
	}

	public static void serializeMatch(JsonGenerator jGen, Match match) throws IOException, JsonProcessingException {
		// list flow matches
		jGen.writeObjectFieldStart("match");
		Iterator<MatchField<?>> mi = match.getMatchFields().iterator(); // get iter to any match field type
		Match m = match;
		while (mi.hasNext()) {
			MatchField<?> mf = mi.next();
			jGen.writeStringField(MatchUtils.getMatchFieldName(mf.id), matchValueToString(m, mf));
		}

		jGen.writeEndObject(); // end match
	}
}