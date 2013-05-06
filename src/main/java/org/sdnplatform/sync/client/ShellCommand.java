package org.sdnplatform.sync.client;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * A user command for the command line client
 * @author readams
 */
public abstract class ShellCommand {

    protected static final ObjectMapper mapper = new ObjectMapper();
    protected static final MappingJsonFactory mjf = 
            new MappingJsonFactory(mapper);
    
    static {
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                         true);
    }
    
    /**
     * Execute the command on the given tokens
     * @param tokens the argument tokens.  The first token will be the command
     * @param line the whole command line
     * @return whether to exit the shell after the command
     */
    public abstract boolean execute(String[] tokens, 
                                    String line) throws Exception;
    
    /**
     * Return syntax description
     * @return the syntax string
     */
    public abstract String syntaxString();
    
    /**
     * Parse a JSON object
     * @param jp the JSON parse
     * @return the JSON node
     * @throws IOException
     */
    protected JsonNode validateJson(JsonParser jp) throws IOException {
        JsonNode parsed = null;
        
        try {
            parsed = jp.readValueAsTree();
        } catch (JsonProcessingException e) {
            System.err.println("Could not parse JSON: " + e.getMessage());
            return null;
        }  
        return parsed;
    }
    
    /**
     * Serialize a JSON object as bytes
     * @param value the object to serialize
     * @return the serialized bytes
     * @throws Exception
     */
    protected byte[] serializeJson(JsonNode value) throws Exception {
        return mapper.writeValueAsBytes(value);
    }
}
