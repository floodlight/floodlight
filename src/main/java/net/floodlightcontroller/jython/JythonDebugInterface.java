package net.floodlightcontroller.jython;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class JythonDebugInterface implements IFloodlightModule {
    protected static Logger log = LoggerFactory.getLogger(JythonDebugInterface.class);
    JythonServer debug_server;
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't export services
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // We don't export services
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        // We don't have any dependencies
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
             throws FloodlightModuleException {
        // no-op
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        Map<String, Object> locals = new HashMap<String, Object>();
        
        // add all existing module references to the debug server
        for (Class<? extends IFloodlightService> s : context.getAllServices()) {
            // Put only the last part of the name
            String[] bits = s.getCanonicalName().split("\\.");
            String name = bits[bits.length-1];
            locals.put(name, context.getServiceImpl(s));
        }
        
        JythonServer debug_server = new JythonServer(6655, locals);
        debug_server.start();
    }
}
