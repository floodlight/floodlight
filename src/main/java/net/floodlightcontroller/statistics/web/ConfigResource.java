package net.floodlightcontroller.statistics.web;

import java.util.Collections;

import net.floodlightcontroller.statistics.IStatisticsService;

import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

public class ConfigResource extends ServerResource {

	@Post
	@Put
	public Object config() {
		IStatisticsService statisticsService = (IStatisticsService) getContext().getAttributes().get(IStatisticsService.class.getCanonicalName());

		if (getReference().getPath().contains(SwitchStatisticsWebRoutable.ENABLE_STR)) {
			statisticsService.collectStatistics(true);
			return Collections.singletonMap("statistics-collection", "enabled");
		}

		if (getReference().getPath().contains(SwitchStatisticsWebRoutable.DISABLE_STR)) {
			statisticsService.collectStatistics(false);
			return Collections.singletonMap("statistics-collection", "disabled");
		}


		if (getReference().getPath().contains(SwitchStatisticsWebRoutable.PORT_STR)) {
			String period = (String) getRequestAttributes().get("period");
			try{
				int val = Integer.valueOf(period);
				return statisticsService.setPortStatsPeriod(val);
			}catch(Exception e) {
				return "{\"status\" : \"Failed! " + e.getMessage() + "\"}";
				
			}
			
		}

		if (getReference().getPath().contains(SwitchStatisticsWebRoutable.FLOW_STR)) {
			String period = (String) getRequestAttributes().get("period");
			try{
				int val = Integer.valueOf(period);
				return statisticsService.setFlowStatsPeriod(val);
			}catch(Exception e) {
				return "{\"status\" : \"Failed! " + e.getMessage() + "\"}";
				
			}
		}

		return Collections.singletonMap("ERROR", "Unimplemented configuration option");
	}
}