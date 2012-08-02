package net.floodlightcontroller.core.web;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class SystemResourcesResource extends ServerResource {
	
	@Get("json")
    public Map<String, Object> retrieve() {
        HashMap<String, Object> model = new HashMap<String, Object>();
        
        OperatingSystemMXBean mxbean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunmxbean = (com.sun.management.OperatingSystemMXBean) mxbean;
        Runtime runtime = Runtime.getRuntime();
        model.put("avgCpuLoad", new String(((double)sunmxbean.getSystemLoadAverage()/sunmxbean.getAvailableProcessors())*100 + "%"));
        model.put("freeSysMem", new Double(sunmxbean.getFreePhysicalMemorySize()));
        model.put("totalSysMem", new Double(sunmxbean.getTotalPhysicalMemorySize()));
        model.put("freeJvmMem", new Double(runtime.freeMemory()));
        model.put("totalJvmMem", new Double(runtime.totalMemory()));
        return model;
    }
}