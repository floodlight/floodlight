package net.floodlightcontroller.loadbalancer;

import java.io.IOException;
import java.util.Collection;

import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

public class L7PoliciesResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(L7PoliciesResource.class);
	
	@Get("json")
    public Collection <L7Policy> retrieve() {
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String policyId = (String) getRequestAttributes().get("policy");
        if (policyId!=null)
            return lbs.listL7Policy(policyId);
        else
            return lbs.listL7Policies();
    }
    
    @Put
    @Post
    public L7Policy createL7Policy(String postData) {

    	L7Policy l7_policy=null;
        try {
        	l7_policy=jsonToL7Policy(postData);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String policyId = (String) getRequestAttributes().get("policy");
        if (policyId != null)
            return lbs.updateL7Policy(l7_policy);
        else
            return lbs.createL7Policy(l7_policy);
    }
    
    @Delete
    public int removeL7Policy() {
        
        String policyId = (String) getRequestAttributes().get("policy");
        
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());

        return lbs.removeL7Policy(policyId);
    }

    protected L7Policy jsonToL7Policy(String json) throws IOException {
        
        if (json==null) return null;
        
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        L7Policy l7_policy = new L7Policy();
        
        try {
            jp = f.createParser(json);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
 
            if (n.equals("id")) {
            	l7_policy.id = jp.getText();
                continue;
            } 
            if (n.equals("vip_id")) {
            	l7_policy.vipId = jp.getText();
                continue;
            } 
            if (n.equals("pool_id")) {
            	l7_policy.poolId = jp.getText();
                continue;
            }
            if (n.equals("value")) {
            	l7_policy.action = Short.parseShort(jp.getText());
                continue;
            } 
            
            log.warn("Unrecognized field {} in " +
                    "parsing L7Policies", 
                    jp.getText());
        }
        jp.close();
        
        return l7_policy;
    }
}
