package net.floodlightcontroller.core;

import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.FloodlightModuleLoader;

/**
 * Host for the Floodlight main method
 * @author alexreimers
 */
public class Main {

    /**
     * Main method to load configuration and modules
     * @param args
     * @throws FloodlightModuleException 
     */
    public static void main(String[] args) throws FloodlightModuleException {
        System.setProperty("org.restlet.engine.loggerFacadeClass", 
                "org.restlet.ext.slf4j.Slf4jLoggerFacade");
        
        FloodlightModuleLoader fml = new FloodlightModuleLoader();
        fml.loadModulesFromConfig();
    }

}
