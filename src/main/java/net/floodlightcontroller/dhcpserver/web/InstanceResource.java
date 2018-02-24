package net.floodlightcontroller.dhcpserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.dhcpserver.DHCPInstance;
import net.floodlightcontroller.dhcpserver.IDHCPService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
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
    // This would also overwrite/update an existing dhcp instance
    public Object updateInstance(String json) throws IOException {
        IDHCPService dhcpService = (IDHCPService) getContext().getAttributes()
                .get(IDHCPService.class.getCanonicalName());
        String whichInstance = (String) getRequestAttributes().get("instance-name");
        Optional<DHCPInstance> dhcpInstance = dhcpService.getInstance(whichInstance);

        if (json == null) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

        JsonNode jsonNode = new ObjectMapper().readTree(json);
        JsonNode switchportsNode = jsonNode.get("switchport");
        if (switchportsNode != null) {
            for (JsonNode swpt : switchportsNode) {
                JsonNode dpidNode = swpt.get("dpid");
                JsonNode portNode = swpt.get("port");
                dhcpInstance.get().addNptMember(new NodePortTuple(DatapathId.of(dpidNode.asText()), OFPort.of
                        (portNode.asInt())));
            }
        }

        return null;
    }

    private boolean checkRequiredFields(JsonNode nameNode, JsonNode startIPNode, JsonNode endIPNode, JsonNode
            serverIDNode, JsonNode serverMacNode,
                                        JsonNode routerIPNode, JsonNode broadcastNode, JsonNode leaseTimeNode,
                                        JsonNode rebindTimeNode,
                                        JsonNode renewTimeNode, JsonNode ipforwardingNode, JsonNode domainNameNode) {
        return nameNode != null && startIPNode != null && endIPNode != null && serverIDNode != null && serverMacNode
                != null
                && routerIPNode != null && broadcastNode != null && leaseTimeNode != null && rebindTimeNode != null
                && renewTimeNode != null && ipforwardingNode != null && domainNameNode != null;

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
