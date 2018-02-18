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
import org.projectfloodlight.openflow.types.VlanVid;

public class DHCPInstanceSerializer extends JsonSerializer<DHCPInstance> {

    @Override
    public void serialize(DHCPInstance instance, JsonGenerator jGen,
                          SerializerProvider serializer)
                                  throws IOException, JsonProcessingException {
    	jGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);
    	
        jGen.writeStartObject();
        jGen.writeStringField("instance_name", instance.getName());
        jGen.writeStringField("server_mac", instance.getServerMac().toString());
        jGen.writeStringField("server_ip", instance.getServerID().toString());
        jGen.writeStringField("subnet_mask", instance.getSubnetMask().toString());
        jGen.writeStringField("router_ip", instance.getRouterIP().toString());
        jGen.writeBooleanField("ip_forwarding", instance.getIpforwarding());
        jGen.writeNumberField("lease_time_sec", instance.getLeaseTimeSec());
        jGen.writeNumberField("lease_rebind_time_sec", instance.getRebindTimeSec());
        jGen.writeNumberField("lease_renewal_time_sec", instance.getRenewalTimeSec());
        jGen.writeStringField("domain_name", instance.getDomainName());
        jGen.writeStringField("broadcast_ip", instance.getBroadcastIP().toString());

        if (instance.getStaticAddresseses() != null) {
            jGen.writeArrayFieldStart("static_addresses");
            for (IPv4Address ip : instance.getStaticAddresseses().values()) {
                jGen.writeString(ip.toString());
            }
            jGen.writeEndArray();
        }

        if (instance.getDNSServers() != null) {
            jGen.writeArrayFieldStart("dns_ips");
            for (IPv4Address ip : instance.getDNSServers()) {
                jGen.writeString(ip.toString());
            }
            jGen.writeEndArray();
        }

        if (instance.getNtpServers() != null) {
            jGen.writeArrayFieldStart("ntp_ips");
            for (IPv4Address ip : instance.getNtpServers()) {
                jGen.writeString(ip.toString());
            }
            jGen.writeEndArray();
        }

        if (instance.getVlanMembers() != null) {
            jGen.writeArrayFieldStart("vlans");
            for (VlanVid vid : instance.getVlanMembers()) {
                jGen.writeString(vid.toString());
            }
            jGen.writeEndArray();
        }

        if (instance.getNptMembers() != null) {
            jGen.writeArrayFieldStart("ntp_ips");
            for (NodePortTuple ip : instance.getNptMembers()) {
                jGen.writeStartObject();
                jGen.writeString(ip.toString());
            }
            jGen.writeEndArray();
        }

        jGen.writeEndObject();
    }
}
