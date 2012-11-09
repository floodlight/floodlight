package net.floodlightcontroller.loadbalancer;

import java.io.IOException;
import java.util.Collection;
import net.floodlightcontroller.virtualnetwork.NetworkResource;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VipsResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(NetworkResource.class);
    
    @Get("json")
    public Collection <LBVip> retrieve() {
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String vipId = (String) getRequestAttributes().get("vip");
        if (vipId!=null)
            return lbs.listVip(vipId);
        else        
            return lbs.listVips();               
    }
    
    @Put
    @Post
    public LBVip createVip(String postData) {        

        LBVip vip=null;
        try {
            vip=jsonToVip(postData);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String vipId = (String) getRequestAttributes().get("vip");
        if (vipId != null)
            return lbs.updateVip(vip);
        else        
            return lbs.createVip(vip);
    }
    
    @Delete
    public int removeVip(String postData) {
        
        String vipId = (String) getRequestAttributes().get("vip");
               
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());

        return lbs.removeVip(vipId);
    }

    protected LBVip jsonToVip(String json) throws IOException {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        LBVip vip = new LBVip();
        
        try {
            jp = f.createJsonParser(json);
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
            else if (n.equals("vip")) {
                while (jp.nextToken() != JsonToken.END_OBJECT) {
                    String field = jp.getCurrentName();
                    
                    if (field.equals("tenant_id")) {
                        vip.id = jp.getText();
                        continue;
                    } 
                    if (field.equals("name")) {
                        vip.name = jp.getText();
                        continue;
                    }
                    if (field.equals("network_id")) {
                        vip.netId = jp.getText();
                        continue;
                    }
                    if (field.equals("protocol")) {
                        vip.protocol = Byte.parseByte(jp.getText());
                        continue;
                    }
                    if (field.equals("port")) {
                        vip.port = Short.parseShort(jp.getText());
                        continue;
                    }
                    if (field.equals("pool_id")) {
                        vip.pools.add(jp.getText());                        
                        continue;
                    }                    
                    
                    log.warn("Unrecognized field {} in " +
                            "parsing Vips", 
                            jp.getText());
                }
            }
        }
        
        jp.close();
        return vip;
    }
    
}
