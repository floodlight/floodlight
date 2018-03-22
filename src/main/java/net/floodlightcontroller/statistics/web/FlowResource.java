package net.floodlightcontroller.statistics.web;

import java.util.Collections;
import java.util.HashSet;

import org.projectfloodlight.openflow.types.DatapathId;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.statistics.FlowRuleStats;
import net.floodlightcontroller.statistics.IStatisticsService;


public class FlowResource extends ServerResource {
	private static final Logger log = LoggerFactory.getLogger(FlowResource.class);
	
	@Get("json")
	public Object retrieve() {
		IStatisticsService statisticsService = (IStatisticsService) getContext().getAttributes().get(IStatisticsService.class.getCanonicalName());
		
        String d = (String) getRequestAttributes().get(SwitchStatisticsWebRoutable.DPID_STR);
        
        DatapathId dpid = DatapathId.NONE;
        
        HashSet<FlowRuleStats> frss;
        if (!d.trim().equalsIgnoreCase("all")) {
            try {
                dpid = DatapathId.of(d);
            } catch (Exception e) {
                log.error("Could not parse DPID {}", d);
                return Collections.singletonMap("ERROR", "Could not parse DPID " + d);
            }
            
    		frss = new HashSet<FlowRuleStats>(statisticsService.getFlowStats(dpid));
    		return frss;
        }
        frss = new HashSet<FlowRuleStats>(statisticsService.getFlowStats().values());
        return frss;
	}
}