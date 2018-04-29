package net.floodlightcontroller.dhcpserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.dhcpserver.DHCPInstance;
import net.floodlightcontroller.dhcpserver.IDHCPService;
import org.projectfloodlight.openflow.types.*;
import org.restlet.data.Status;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.Optional;

public class InstanceResource extends ServerResource {

    private static final String INSTANCE_NOT_FOUND_MESSAGE = "Instance not found.";

    @Get
    public Object getInstance() {
        IDHCPService dhcpService = (IDHCPService) getContext()
                .getAttributes().get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get("instance-name");

        Optional<DHCPInstance> instance = dhcpService.getInstance(whichInstance);

        if (instance.isPresent()) {
            return instance.get();
        } else {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, INSTANCE_NOT_FOUND_MESSAGE);
            return null;
        }
    }

    @Put
    @Post
    public Object updateInstance(String json) throws IOException {
        IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
                .get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get("instance-name");
        Optional<DHCPInstance> dhcpInstance = dhcpService.getInstance(whichInstance);

        if (!dhcpInstance.isPresent()) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, INSTANCE_NOT_FOUND_MESSAGE);
            return null;
        }

        if (json == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

        JsonNode jsonNode = new ObjectMapper().readTree(json);

        JsonNode gatewayNode = jsonNode.get("router-ip");
        if (gatewayNode != null) {
            IPv4Address routerIP = IPv4Address.of(gatewayNode.asText());
            dhcpInstance.get().updateDefaultGateway(routerIP);
        }

        JsonNode switchportsNode = jsonNode.get("switchports");
        if (switchportsNode != null) {
            dhcpInstance.get().clearNptMember();

            for (JsonNode swpt : switchportsNode) {
                JsonNode dpidNode = swpt.get("dpid");
                JsonNode portNode = swpt.get("port");
                if (dpidNode != null && portNode != null) {
                    NodePortTuple npt = new NodePortTuple(DatapathId.of(dpidNode.asText()), OFPort.of(portNode.asInt()));
                    dhcpInstance.get().addNptMember(npt);
                }
            }
        }

        JsonNode switchesNode = jsonNode.get("switches");
        if (switchesNode != null) {
            dhcpInstance.get().clearSwitchMember();

            for (JsonNode sw : switchesNode) {
                JsonNode dpidNode = sw.get("dpid");
                if (dpidNode != null) {
                    dhcpInstance.get().addSwitchMember(DatapathId.of(dpidNode.asText()));
                }
            }
        }

        JsonNode vlansNode = jsonNode.get("vlans");
        if (vlansNode != null) {
            dhcpInstance.get().clearVlanMember();

            for (JsonNode vlan : vlansNode) {
                VlanVid vid = VlanVid.ofVlan(vlan.get("vlan").asInt());
                dhcpInstance.get().addVlanMember(vid);
            }
        }

        JsonNode staticAddressesNode = jsonNode.get("staticaddresses");
        if (staticAddressesNode != null) {
            dhcpInstance.get().clearStaticAddress();

            for (JsonNode staticAddress : staticAddressesNode) {
                JsonNode macNode = staticAddress.get("mac");
                JsonNode ipNode = staticAddress.get("ip");
                if (macNode != null && ipNode != null) {
                    dhcpInstance.get().addStaticAddress(MacAddress.of(macNode.asText()), IPv4Address.of(ipNode.asText()));
                }
            }
        }

        JsonNode clientMembersNode = jsonNode.get("clientmembers");
        if (clientMembersNode != null) {
            dhcpInstance.get().clearClientMemer();

            for (JsonNode clientmember : clientMembersNode) {
                MacAddress cm = MacAddress.of(clientmember.asText());
                dhcpInstance.get().addClientMember(cm);
            }
        }

        JsonNode dnsServersNode = jsonNode.get("dnsservers");
        if (dnsServersNode != null) {
            dhcpInstance.get().clearDnsServer();

            for (JsonNode dnsServer : dnsServersNode) {
                IPv4Address ds = IPv4Address.of(dnsServer.asText());
                dhcpInstance.get().addDnsServer(ds);
            }
        }

        JsonNode ntpServersNode = jsonNode.get("ntpservers");
        if (ntpServersNode != null) {
            dhcpInstance.get().clearNtpServer();

            for (JsonNode ntpServer : ntpServersNode) {
                IPv4Address ns = IPv4Address.of(ntpServer.asText());
                dhcpInstance.get().addNtpServer(ns);
            }
        }

        setDescription("Instance updated.");
        return dhcpInstance.get();
    }

    @Delete
    public Object deleteInstance() {
        IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
                .get(IDHCPService.class.getCanonicalName());

        String whichInstance = (String) getRequestAttributes().get("instance-name");

        if (dhcpService.deleteInstance(whichInstance)) {
            return ImmutableMap.of("deleted", whichInstance);
        } else {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, INSTANCE_NOT_FOUND_MESSAGE);
            return null;
        }
    }

}
