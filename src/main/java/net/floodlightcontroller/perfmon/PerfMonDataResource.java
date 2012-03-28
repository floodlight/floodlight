package net.floodlightcontroller.perfmon;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Return the performance monitoring data for the get rest api call
 * @author subrata
 */
public class PerfMonDataResource extends ServerResource {
    protected static Logger logger = LoggerFactory.getLogger(PerfMonDataResource.class);  
    
    @Get("json")
    public CumulativeTimeBucket handleApiQuery() {        
        IPktInProcessingTimeService pktinProcTime = 
            (IPktInProcessingTimeService)getContext().getAttributes().
                get(IPktInProcessingTimeService.class.getCanonicalName());
        
        setStatus(Status.SUCCESS_OK, "OK");
        // Allocate output object
        if (pktinProcTime.isEnabled()) {
            CumulativeTimeBucket ctb = pktinProcTime.getCtb();
            ctb.computeAverages();
            return ctb;
        }
        
        return null;
    }
}