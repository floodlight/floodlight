package net.floodlightcontroller.loadbalancer;

import java.io.IOException;
import java.util.Collection;

import org.projectfloodlight.openflow.types.IpProtocol;
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

import net.floodlightcontroller.packet.IPv4;

public class L7RulesResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(L7RulesResource.class);
	
	@Get("json")
    public Collection <LBVip> retrieve() {
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());
        
        String vipId = (String) getRequestAttributes().get("rule");
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
    public int removeVip() {
        
        String vipId = (String) getRequestAttributes().get("vip");
        
        ILoadBalancerService lbs =
                (ILoadBalancerService)getContext().getAttributes().
                    get(ILoadBalancerService.class.getCanonicalName());

        return lbs.removeVip(vipId);
    }

    protected LBVip jsonToVip(String json) throws IOException {
        
        if (json==null) return null;
        
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        LBVip vip = new LBVip();
        
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
                vip.id = jp.getText();
                continue;
            } 
            if (n.equals("tenant_id")) {
                vip.tenantId = jp.getText();
                continue;
            } 
            if (n.equals("name")) {
                vip.name = jp.getText();
                continue;
            }
            if (n.equals("network_id")) {
                vip.netId = jp.getText();
                continue;
            }
            if (n.equals("protocol")) {
                String tmp = jp.getText();
                if (tmp.equalsIgnoreCase("TCP")) {
                    vip.protocol = (byte) IpProtocol.TCP.getIpProtocolNumber();
                } else if (tmp.equalsIgnoreCase("UDP")) {
                    vip.protocol = (byte) IpProtocol.UDP.getIpProtocolNumber();
                } else if (tmp.equalsIgnoreCase("ICMP")) {
                    vip.protocol = (byte) IpProtocol.ICMP.getIpProtocolNumber();
                } 
                continue;
            }
            if (n.equals("address")) {
                vip.address = IPv4.toIPv4Address(jp.getText());
                continue;
            }
            if (n.equals("port")) {
                vip.port = Short.parseShort(jp.getText());
                continue;
            }
            if (n.equals("pool_id")) {
                vip.pools.add(jp.getText());
                continue;
            }
            
            log.warn("Unrecognized field {} in " +
                    "parsing Vips", 
                    jp.getText());
        }
        jp.close();
        
        return vip;
    }
}
