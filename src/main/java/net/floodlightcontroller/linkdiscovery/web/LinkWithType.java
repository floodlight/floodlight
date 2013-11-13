/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.linkdiscovery.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openflow.util.HexString;

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkDirection;
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
    public long dstSwDpid;
    public short dstPort;
    public LinkType type;
    public LinkDirection direction;

    // Do NOT delete this, it's required for the serializer
    public LinkWithType() {}

    public LinkWithType(Link link,
            LinkType type,
            LinkDirection direction) {
        this.srcSwDpid = link.getSrc();
        this.srcPort = link.getSrcPort();
        this.dstSwDpid = link.getDst();
        this.dstPort = link.getDstPort();
        this.type = type;
        this.direction = direction;
    }

    @Override
    public void serialize(LinkWithType lwt, JsonGenerator jgen, SerializerProvider arg2)
            throws IOException, JsonProcessingException {
        // You ****MUST*** use lwt for the fields as it's actually a different object.
        jgen.writeStartObject();
        jgen.writeStringField("src-switch", HexString.toHexString(lwt.srcSwDpid));
        jgen.writeNumberField("src-port", lwt.srcPort);
        jgen.writeStringField("dst-switch", HexString.toHexString(lwt.dstSwDpid));
        jgen.writeNumberField("dst-port", lwt.dstPort);
        jgen.writeStringField("type", lwt.type.toString());
        jgen.writeStringField("direction", lwt.direction.toString());
        jgen.writeEndObject();
    }

    @Override
    public Class<LinkWithType> handledType() {
        return LinkWithType.class;
    }
}