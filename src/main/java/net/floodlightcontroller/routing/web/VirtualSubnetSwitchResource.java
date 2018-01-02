package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.SubnetBuildMode;
import net.floodlightcontroller.routing.VirtualSubnet;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;


/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/1/18
 */
public class VirtualSubnetSwitchResource extends ServerResource {
    @Put
    @Post
    public Object createVirtualSubnet(String jsonData) throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode nameNode = mapper.readTree(jsonData).get("subnet-name");
            JsonNode gatewayIPNode = mapper.readTree(jsonData).get("gateway-ip");
            JsonNode switchNode = mapper.readTree(jsonData).get("switch");

            if (nameNode == null || gatewayIPNode == null || switchNode == null) {
                return Collections.singletonMap("INFO: ", "Some fields missing");
            }

            if (!routingService.getVirtualSubnet(nameNode.asText()).isPresent()) {
                // This is a new subnet, will try to create it
                if (routingService.getCurrentSubnetBuildMode() != SubnetBuildMode.SWITCH &&
                        !routingService.getAllVirtualSubnets().get().isEmpty()) {
                    return Collections.singletonMap("INFO: ", "Subnet currently not define as group of switches. " +
                            "List created subnet and double check");
                }
                // Check if desired DPID existed already
                if (!routingService.checkDPIDExist(DatapathId.of(switchNode.asText()))) {
                    routingService.createVirtualSubnet(nameNode.asText(), IPv4Address.of(gatewayIPNode.asText()),
                            DatapathId.of(switchNode.asText()));
                    return Collections.singletonMap("INFO: ", "Virtual subnet '" + nameNode.asText() + "' created");
                }
                else {
                    return Collections.singletonMap("INFO: ", "DPID '" + switchNode.asText() +
                            "' already existed, change to another DPID and try again");
                }
            }
            else {
                // This is an existing subnet, will try to update it or add new DPID into it //TODO: better comments
                if (routingService.getCurrentSubnetBuildMode() != SubnetBuildMode.SWITCH &&
                        !routingService.getAllVirtualSubnets().get().isEmpty()) {
                    return Collections.singletonMap("INFO: ", "Subnet currently not define as group of switches. " +
                            "List created subnet and double check");
                }
                routingService.updateVirtualSubnet(nameNode.asText(), IPv4Address.of(gatewayIPNode.asText()),
                        DatapathId.of(switchNode.asText()));
                return Collections.singletonMap("INFO: ", "Virtual subnet '" + nameNode.asText() + "' updated");
            }
        }
        catch (IOException e) {
            throw new IOException(e);
        }

    }

}
