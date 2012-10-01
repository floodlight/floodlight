package net.floodlightcontroller.core;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import net.floodlightcontroller.core.internal.CmdLineSettings;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.FloodlightModuleLoader;
import net.floodlightcontroller.core.module.IFloodlightModuleContext;
import net.floodlightcontroller.restserver.IRestApiService;

/**
 * Host for the Floodlight main method
 * @author alexreimers
 */
public class Main {
	static CmdLineSettings settings = new CmdLineSettings();
	static CmdLineParser parser = new CmdLineParser(settings);
	
	IRestApiService restApi = null;
	IFloodlightProviderService controller = null;
	
    /**
     * Main method to load configuration and modules
     * @param args
     * @throws FloodlightModuleException 
     */
    public static void main(String[] args) throws FloodlightModuleException {
    	Main boot = new Main();
    	try{
	    	boot.init(args);
    	}catch(CmdLineException e){
            parser.printUsage(System.out);
            System.exit(1);
    	}
    }
    
    public void init(String[] args) throws CmdLineException,FloodlightModuleException {
        // Setup logger
        System.setProperty("org.restlet.engine.loggerFacadeClass", 
                "org.restlet.ext.slf4j.Slf4jLoggerFacade");
        
        parser.parseArgument(args);
        
        // Load modules
        FloodlightModuleLoader fml = new FloodlightModuleLoader();
        IFloodlightModuleContext moduleContext = fml.loadModulesFromConfig(settings.getModuleFile());
        // Run REST server
        restApi = moduleContext.getServiceImpl(IRestApiService.class);
        // Run the main floodlight module
        controller = moduleContext.getServiceImpl(IFloodlightProviderService.class);
    }
    
    public void start() {
        restApi.run();
        // This call blocks, it has to be the last line in the main
        controller.run();    	
    }
    
    public void stop() {
    	// no-op because IRestApiService don't have stop()
    	// op-op because IFloodlightProviderService don't have stop()
    }
    
    public void destroy() {
    	
    }
}
