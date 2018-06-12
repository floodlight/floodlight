package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.routing.IRoutingService;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 4/28/18
 */
public class ConfigResource extends ServerResource {

    @Get
    public Object getL3Config() {
        IRoutingService routingService = (IRoutingService) getContext()
                .getAttributes().get(IRoutingService.class.getCanonicalName());

        List<Map> maps = new ArrayList<>();
        maps.add(ImmutableMap.of("enabled", routingService.isL3RoutingEnabled()));

        return maps;
    }


    @Put
    @Post
    public Object configureL3(String json) throws Exception {
        IRoutingService routingService = (IRoutingService) getContext()
                .getAttributes().get(IRoutingService.class.getCanonicalName());

        if (json == null) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "Field missing to enable/disable L3 routing.");
            return null;
        }

        JsonNode jsonNode = new ObjectMapper().readTree(json);
        JsonNode enableNode = jsonNode.get("enable");

        if (enableNode == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

        if (enableNode.asBoolean()) {
            routingService.enableL3Routing();
        }
        else {
            routingService.disableL3Routing();
        }

        return getL3Config();

    }


}
