package net.floodlightcontroller.core.web;

import java.util.HashMap;
import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class SystemResourcesResource extends ServerResource {
	
	@Get("json")
    public Map<String, Object> retrieve() {
        HashMap<String, Object> model = new HashMap<String, Object>();
        
        java.lang.management.OperatingSystemMXBean mxbean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunmxbean = (com.sun.management.OperatingSystemMXBean) mxbean;
        
        model.put("avgCpuLoad", new String(((double)sunmxbean.getSystemLoadAverage()/sunmxbean.getAvailableProcessors())*100 + "%"));
        model.put("freeSysMem", new Double(sunmxbean.getFreePhysicalMemorySize()));
        model.put("totalSysMem", new Double(sunmxbean.getTotalPhysicalMemorySize()));
        model.put("freeJvmMem", new Double(Runtime.getRuntime().freeMemory()));
        model.put("totalJvmMem", new Double(Runtime.getRuntime().totalMemory()));
        return model;
    }
}