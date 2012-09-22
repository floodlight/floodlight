package net.floodlightcontroller.topology.web;

import java.util.List;

import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(RouteResource.class);

    @Get("json")
    public List<NodePortTuple> retrieve() {
        IRoutingService routing = 
                (IRoutingService)getContext().getAttributes().
                    get(IRoutingService.class.getCanonicalName());
        
        String srcDpid = (String) getRequestAttributes().get("src-dpid");
        String srcPort = (String) getRequestAttributes().get("src-port");
        String dstDpid = (String) getRequestAttributes().get("dst-dpid");
        String dstPort = (String) getRequestAttributes().get("dst-port");

        log.debug( srcDpid + "--" + srcPort + "--" + dstDpid + "--" + dstPort);

        long longSrcDpid = HexString.toLong(srcDpid);
        short shortSrcPort = Short.parseShort(srcPort);
        long longDstDpid = HexString.toLong(dstDpid);
        short shortDstPort = Short.parseShort(dstPort);
        
        Route result = routing.getRoute(longSrcDpid, shortSrcPort, longDstDpid, shortDstPort);
        
        if (result!=null) {
            return routing.getRoute(longSrcDpid, shortSrcPort, longDstDpid, shortDstPort).getPath();
        }
        else {
            log.debug("ERROR! no route found");
            return null;
        }
    }
}
