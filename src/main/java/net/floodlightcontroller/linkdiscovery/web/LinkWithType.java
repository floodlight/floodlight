package net.floodlightcontroller.linkdiscovery.web;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.openflow.util.HexString;

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.routing.Link;

/**
 * This class is both the datastructure and the serializer
 * for a link with the corresponding type of link.
 * @author alexreimers
 */
@JsonSerialize(using=LinkWithType.class)
public class LinkWithType extends JsonSerializer<LinkWithType> {
    public long srcSwDpid;
    public short srcPort;
    public int srcPortState;
    public long dstSwDpid;
    public short dstPort;
    public int dstPortState;
    public LinkType type;

    // Do NOT delete this, it's required for the serializer
    public LinkWithType() {}
    
    public LinkWithType(Link link,
                        int srcPortState,
                        int dstPortState,
                        LinkType type) {
        this.srcSwDpid = link.getSrc();
        this.srcPort = link.getSrcPort();
        this.srcPortState = srcPortState;
        this.dstSwDpid = link.getDst();
        this.dstPort = link.getDstPort();
        this.dstPortState = dstPortState;
        this.type = type;
    }

	@Override
	public void serialize(LinkWithType lwt, JsonGenerator jgen, SerializerProvider arg2) 
			throws IOException, JsonProcessingException {
		// You ****MUST*** use lwt for the fields as it's actually a different object.
		jgen.writeStartObject();
		jgen.writeStringField("src-switch", HexString.toHexString(lwt.srcSwDpid));
		jgen.writeNumberField("src-port", lwt.srcPort);
		jgen.writeNumberField("src-port-state", lwt.srcPortState);
		jgen.writeStringField("dst-switch", HexString.toHexString(lwt.dstSwDpid));
		jgen.writeNumberField("dst-port", lwt.dstPort);
		jgen.writeNumberField("dst-port-state", lwt.dstPortState);
		jgen.writeStringField("type", lwt.type.toString());
		jgen.writeEndObject();
	}
	
	@Override
	public Class<LinkWithType> handledType() {
		return LinkWithType.class;
	}
}