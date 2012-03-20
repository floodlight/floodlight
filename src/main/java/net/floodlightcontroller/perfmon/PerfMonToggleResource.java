package net.floodlightcontroller.perfmon;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class PerfMonToggleResource extends ServerResource {
    
    @Get("json")
    public String retrieve() {
        IPktInProcessingTimeService pktinProcTime = 
                (IPktInProcessingTimeService)getContext().getAttributes().
                    get(IPktInProcessingTimeService.class.getCanonicalName());
        
        String param = ((String)getRequestAttributes().get("perfmonstate")).toLowerCase();
        if (param.equals("reset")) {
            pktinProcTime.getCtb().reset();
        } else {
            if (param.equals("enable") || param.equals("true")) {
                pktinProcTime.setEnabled(true);
            } else if (param.equals("disable") || param.equals("false")) {
                pktinProcTime.setEnabled(false);
            }
        }
        setStatus(Status.SUCCESS_OK, "OK");
        return "{ \"enabled\" : " + pktinProcTime.isEnabled() + " }";
    }
}
