package net.floodlightcontroller.dhcpserver.web;

import java.io.IOException;

import org.projectfloodlight.openflow.types.IPv4Address;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.dhcpserver.DHCPInstance;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DHCPInstanceSerializer extends JsonSerializer<DHCPInstance> {

    @Override
    public void serialize(DHCPInstance instance, JsonGenerator jGen,
                          SerializerProvider serializer)
                                  throws IOException, JsonProcessingException {
    	jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);
    	
        jGen.writeStartObject();
        jGen.writeStringField("name", instance.getName());
        jGen.writeStringField("domain_name", instance.getDomainName());
        jGen.writeStringField("broadcast_ip", instance.getBroadcastIp().toString());
        
        jGen.writeArrayFieldStart("dns_ips");
        for (IPv4Address ip : instance.getDnsIps()) {
            jGen.writeString(ip.toString());
        }
        jGen.writeEndArray();
        
        jGen.writeArrayFieldStart("ntp_ips");
        for (IPv4Address ip : instance.getNtpIps()) {
            jGen.writeString(ip.toString());
        }
        jGen.writeEndArray();
        
        jGen.writeArrayFieldStart("ntp_ips");
        for (NodePortTuple ip : instance.getMemberPorts()) {
        	jGen.writeStartObject();
            jGen.writeString(ip.toString());
        }
        jGen.writeEndArray();

        jGen.writeNumberField("lease_hold_time_sec", instance.getHoldTimeSec());
        jGen.writeNumberField("lease_time_sec", instance.getLeaseTimeSec());
        jGen.writeNumberField("lease_rebind_time_sec", instance.getRebindTimeSec());
        jGen.writeNumberField("lease_renewal_time_sec", instance.getRenewalTimeSec());

        jGen.writeBooleanField("ip_forwarding", instance.getIpForwarding());
        jGen.writeStringField("router_ip", instance.getRouterIp().toString());
        jGen.writeStringField("server_ip", instance.getServerIp().toString());
        jGen.writeStringField("server_mac", instance.getServerMac().toString());
        jGen.writeStringField("server_ip", instance.getSubnetMask().toString());

        
        jGen.writeEndObject();
    }
}
