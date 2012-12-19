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
    Thread controller = null;

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
        boot.start();
    }

    public void init(String[] args) throws CmdLineException,FloodlightModuleException {
        // Setup logger
        System.setProperty("org.restlet.engine.loggerFacadeClass", 
                "org.restlet.ext.slf4j.Slf4jLoggerFacade");

        parser.parseArgument(args);
    }

    public void start() throws FloodlightModuleException {
        this.stop();

        // Load modules
        final FloodlightModuleLoader fml = new FloodlightModuleLoader();
        final IFloodlightModuleContext moduleContext = fml.loadModulesFromConfig(settings.getModuleFile());

        // Run REST server
        restApi = moduleContext.getServiceImpl(IRestApiService.class);
        restApi.run();

        // Run the main floodlight module
        controller = new Thread(){
            public void run() {
                moduleContext.getServiceImpl(IFloodlightProviderService.class).run();
            }};
        controller.start();
    }

    public void stop() throws FloodlightModuleException {
        if(restApi != null){
            try{
                restApi.stop();
            }catch(Exception e){
                throw new FloodlightModuleException("RestApi restlet stop error");
            }
            restApi = null;
        }
        if(controller != null){
            controller.interrupt();
            try {
                controller.join();
            } catch (InterruptedException e) {
                throw new FloodlightModuleException("controller join failed");
            }
            controller = null;
        }
    }

    public void destroy() {
    }
}
