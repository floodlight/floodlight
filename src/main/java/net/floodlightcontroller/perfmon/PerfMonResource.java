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
public class PerfMonResource extends ServerResource {
    protected static Logger logger = LoggerFactory.getLogger(PerfMonResource.class);
    byte[] PerfMonResourceSerialized;
    
    /**
     * The input packet parameters to be received via POST
     * @author subrata
     */    
    
    /**
     * The output model that contains the result of the performance
     * monitoring data
     * @author subrata
     */
    public static class ProcTimePerfMonOutput {
        String  perfMonType;
        Boolean procTimeMonitoringState;
        CircularTimeBucketSet procTimeBucketSet;
        String  Status;
        String  Explanation;
        
        public String getPerfMonType() {
            return perfMonType;
        }
        public void setPerfMonType(String perfMonType) {
            this.perfMonType = perfMonType;
        }             
        public Boolean getProcTimeMonitoringState() {
            return procTimeMonitoringState;
        }
        public void setProcTimeMonitoringState(Boolean procTimeMonitoringState) {
            this.procTimeMonitoringState = procTimeMonitoringState;
        }
        public CircularTimeBucketSet getProcTimeBucketSet() {
            return procTimeBucketSet;
        }
        public void setProcTimeBucketSet(CircularTimeBucketSet procTimeBucketSet) {
            this.procTimeBucketSet = procTimeBucketSet;
        }
           
    }
    
    @Get("json")
    public ProcTimePerfMonOutput handleApiQuery() {
                
        // Get the type of performance data queried
        // Supported types: proc-time
        String perfMonType = (String)getRequestAttributes().get("type"); 
        
        logger.info("Perf Mon. API call: type = " + perfMonType);
        
        IPktInProcessingTimeService pktinProcTime = 
            (IPktInProcessingTimeService)getContext().getAttributes().
                get(IPktInProcessingTimeService.class.getCanonicalName());
        
        // Allocate output object
        ProcTimePerfMonOutput output = new ProcTimePerfMonOutput();
        
        output.setPerfMonType(perfMonType);
        output.setProcTimeMonitoringState(pktinProcTime.isEnabled());
        output.setProcTimeBucketSet(pktinProcTime.getCtbs());
        
        setStatus(Status.SUCCESS_OK);
        output.Status      = "SUCCESS";
        output.Explanation = "OK";        

        return output;
    }
}