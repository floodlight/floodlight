package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.VirtualGateway;
import net.floodlightcontroller.routing.VirtualGatewayInterface;
import org.restlet.resource.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class VirtualInterfaceResource extends ServerResource {

    @Get
    public Object getVirtualInterfaces() {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes()
                        .get(IRoutingService.class.getCanonicalName());

        String gatewayName = (String) getRequestAttributes().get("gateway-name");
        String interfaceName = (String) getRequestAttributes().get("interface-name");

        Optional<VirtualGateway> gateway = routingService.getVirtualGateway(gatewayName);
        if (!gateway.isPresent()) {
            return Collections.singletonMap("INFO: ", "Virtual gateway '" + gatewayName + "' not found");
        }
        if (routingService.getAllGatewayInterfaces(gateway.get()).get().isEmpty()) {
            return Collections.singletonMap("INFO: ", "No virtual interface exists on '" + gatewayName + "' yet");
        }

        if (interfaceName.equals("all")) {
            return routingService.getAllGatewayInterfaces(gateway.get()).get();
        }
        else {
            Optional<VirtualGatewayInterface> vInterface = routingService.getGatewayInterface(interfaceName, gateway.get());
            return vInterface.isPresent() ?
                    routingService.getGatewayInterface(interfaceName, gateway.get()) :
                    Collections.singletonMap("INFO: ", "Virtual interface '" + interfaceName + "' not found");
        }

    }

    @Delete
    public Object removeVirtualInterface() throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes()
                        .get(IRoutingService.class.getCanonicalName());

        String gatewayName = (String) getRequestAttributes().get("gateway-name");
        String interfaceName = (String) getRequestAttributes().get("interface-name");

        Optional<VirtualGateway> gateway = routingService.getVirtualGateway(gatewayName);
        if (!gateway.isPresent()) {
            return Collections.singletonMap("INFO: ", "Virtual gateway '" + gatewayName + "' not found");
        }
        if (routingService.getAllGatewayInterfaces(gateway.get()).get().isEmpty()) {
            return Collections.singletonMap("INFO: ", "No virtual interface exists on '" + gatewayName + "' yet");
        }

        if (interfaceName.equals("all")) {
            routingService.removeAllVirtualInterfaces(gateway.get());
            return Collections.singletonMap("INFO: ", "All virtual interface from '" + gatewayName + "' removed");
        }
        else {
            if (routingService.removeVirtualInterface(interfaceName, gateway.get())) {
                return Collections.singletonMap("INFO: ", "Virtual interface '" + interfaceName +
                        "' from gateway '" + gatewayName + "' removed");
            }
            else {
                return Collections.singletonMap("INFO: ", "Virtual interface '" + interfaceName +
                        "' from gateway '" + gatewayName + "' not found");
            }
        }

    }

    @Put
    @Post
    public Object createVirtualInterface(String jsonData) throws IOException {
        IRoutingService routingService =
                (IRoutingService) getContext().getAttributes()
                        .get(IRoutingService.class.getCanonicalName());

        String gatewayName = (String) getRequestAttributes().get("gateway-name");

        Optional<VirtualGateway> gateway = routingService.getVirtualGateway(gatewayName);
        if (!gateway.isPresent()) {
            return Collections.singletonMap("INFO: ", "Virtual gateway '" + gatewayName + "' not found");
        }

        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode interfaceNameNode = mapper.readTree(jsonData).get("interface-name");
            JsonNode interfaceIPNode = mapper.readTree(jsonData).get("interface-ip");
            JsonNode interfaceMaskNode = mapper.readTree(jsonData).get("interface-mask");
            JsonNode interfaceMacNode = mapper.readTree(jsonData).get("interface-mac");

            if (interfaceNameNode == null || interfaceIPNode == null || interfaceMaskNode == null || interfaceMacNode == null) {
                return Collections.singletonMap("INFO: ", "some fields missing");
            }

            //TODO: Probably should check if interface-ip is a valid IP with mask here

            VirtualGatewayInterface vInterface = new ObjectMapper()
                    .reader(VirtualGatewayInterface.class)
                    .readValue(jsonData);

            if (!routingService.getVirtualGateway(gateway.get().getName()).get()
                    .getInterface(interfaceNameNode.asText()).isPresent()) {
                // Create new virtual interface
                routingService.createVirtualInterface(gateway.get(), vInterface);
                return Collections.singletonMap("INFO: ", "Virtual interface '" + interfaceNameNode.asText() + "' created on '" + gatewayName + "'" );
            }
            else {
                // Update existing virtual interface
                routingService.updateVirtualInterface(gateway.get(), vInterface);
                return Collections.singletonMap("INFO: ", "Virtual interface '" + interfaceNameNode.asText() + "' updated");
            }

        }
        catch (IOException e) {
            throw new IOException(e);
        }

    }


}
