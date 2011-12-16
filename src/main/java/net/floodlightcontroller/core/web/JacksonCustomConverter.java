/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.core.web;

import java.util.List;

import net.floodlightcontroller.core.web.serializers.OFFeaturesReplyJSONSerializer;
import net.floodlightcontroller.core.web.serializers.OFMatchJSONSerializer;
import net.floodlightcontroller.core.web.serializers.OFPhysicalPortJSONSerializer;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.JsonGenerator.Feature;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.module.SimpleModule;
import org.restlet.data.MediaType;
import org.restlet.engine.Engine;
import org.restlet.engine.converter.ConverterHelper;
import org.restlet.ext.jackson.JacksonConverter;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrap the standard JacksonConverter and use a custom object mapper
 * that contains custom serializers
 * @author readams
 *
 */
public class JacksonCustomConverter extends JacksonConverter {
    protected static Logger log = LoggerFactory.getLogger(JacksonCustomConverter.class);

    protected static ObjectMapper jsonObjectMapper;
    protected static SimpleModule jsonModule;
    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(Feature.AUTO_CLOSE_TARGET, false);
        jsonObjectMapper = new ObjectMapper(jsonFactory);
        jsonModule = new SimpleModule("JsonModule", new Version(1, 0, 0, null));
        jsonModule.addSerializer(new OFMatchJSONSerializer());
        jsonModule.addSerializer(new OFFeaturesReplyJSONSerializer());
        jsonModule.addSerializer(new OFPhysicalPortJSONSerializer());
        jsonObjectMapper.registerModule(jsonModule);
    }
    
    @Override
    protected <T> JacksonRepresentation<T> create(MediaType mediaType, T source) {
        JacksonRepresentation<T> jr = new JacksonRepresentation<T>(mediaType, source);
        jr.setObjectMapper(jsonObjectMapper);
        return jr;
    }

    @Override
    protected <T> JacksonRepresentation<T> create(Representation source, Class<T> objectClass) {
        JacksonRepresentation<T> jr = new JacksonRepresentation<T>(source, objectClass);
        jr.setObjectMapper(jsonObjectMapper);
        return jr;
    }
    
    /**
     * Replace the jackson converter with this one
     * @param converterClass
     * @param newConverter
     */
    public static void replaceConverter() {
        ConverterHelper oldConverter = null;

        List<ConverterHelper> converters = Engine.getInstance().getRegisteredConverters();
        for (ConverterHelper converter : converters) {
            if (converter.getClass().equals(JacksonConverter.class)) {
                converters.remove(converter);
                oldConverter = converter;
                break;
            }
        }

        converters.add(new JacksonCustomConverter());

        if (oldConverter == null) {
            log.debug("Added {} to Restlet Engine", JacksonCustomConverter.class);
        } else {
            log.debug("Replaced {} with {} in Restlet Engine", 
                         oldConverter.getClass(), JacksonCustomConverter.class);
        }
    }
}
