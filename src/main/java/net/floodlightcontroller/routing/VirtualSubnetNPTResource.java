package net.floodlightcontroller.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/1/18
 */
public class VirtualSubnetNPTResource extends ServerResource {
    @Put
    @Post
    public Object createVirtualSubnet(String jsonData) throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes().
                        get(IRoutingService.class.getCanonicalName());

        if (routingService.getCurrentSubnetBuildMode() != SubnetBuildMode.NodePortTuple) {
            return Collections.singletonMap("INFO: ", "Subnet currently not define as group of node-port-tuples. " +
                    "List created subnet and double check");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode nameNode = mapper.readTree(jsonData).get("subnet-name");
            JsonNode gatewayIPNode = mapper.readTree(jsonData).get("gateway-ip");
            JsonNode switchNode = mapper.readTree(jsonData).get("switch");
            JsonNode portNode = mapper.readTree(jsonData).get("port");

            if (nameNode == null || gatewayIPNode == null || switchNode == null || portNode == null) {
                return Collections.singletonMap("INFO: ", "Some fields missing");
            }

            if (!routingService.getVirtualSubnet(nameNode.asText()).isPresent()) {
                // Create a new virtual subnet
                routingService.createVirtualSubnet(nameNode.asText(), IPv4Address.of(gatewayIPNode.asText()),
                        new NodePortTuple(DatapathId.of(switchNode.asText()), OFPort.of(portNode.asInt())));
                return Collections.singletonMap("INFO: ", "Virtual subnet '" + nameNode.asText() + "' created");
            }
            else {
                // Updating existing virtual subnet
//                routingService.updateVirtualSubnet();
                return Collections.singletonMap("INFO: ", "Virtual subnet '" + nameNode.asText() + "' updated");
            }
        }
        catch (IOException e){
            throw new IOException(e);
        }

    }

}
