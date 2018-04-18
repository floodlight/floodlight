package net.floodlightcontroller.dhcpserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.dhcpserver.IDHCPService;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Geddings Barrineau, geddings.barrineau@bigswitch.com on 2/24/18.
 */
public class ConfigResource extends ServerResource {

    @Get
    public Object getConfig() {
        IDHCPService dhcpService = (IDHCPService) getContext()
                .getAttributes().get(IDHCPService.class.getCanonicalName());

        List<Map> maps = new ArrayList<>();
        maps.add(ImmutableMap.of("enabled", dhcpService.isDHCPEnabled()));
        maps.add(ImmutableMap.of("dynamicLease", dhcpService.isDHCPDynamicEnabled()));

        return maps;
    }

    @Put
    @Post
    public Object configure(String json) throws IOException {
        IDHCPService dhcpService = (IDHCPService) getContext()
                .getAttributes().get(IDHCPService.class.getCanonicalName());

        if (json == null) {
            setStatus(org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

        JsonNode jsonNode = new ObjectMapper().readTree(json);
        JsonNode enableNode = jsonNode.get("enable");
        JsonNode leaseGCPeriodNode = jsonNode.get("lease-gc-period");
        JsonNode dynamicLeaseNode = jsonNode.get("dynamic-lease");

        if (enableNode == null || leaseGCPeriodNode == null || dynamicLeaseNode == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "One or more required fields missing.");
            return null;
        }

        if (enableNode.asBoolean()) {
            dhcpService.enableDHCP();
            dhcpService.setCheckExpiredLeasePeriod(leaseGCPeriodNode.asLong());
        }
        else dhcpService.disableDHCP();

        if (dynamicLeaseNode.asBoolean()) {
            dhcpService.enableDHCPDynamic();
        }
        else {
            dhcpService.disableDHCDynamic();
        }

        return getConfig();

    }
}
