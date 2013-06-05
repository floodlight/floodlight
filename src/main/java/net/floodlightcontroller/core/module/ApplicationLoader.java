package net.floodlightcontroller.core.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * Load an application from a configuration directory
 * @author readams
 */
public class ApplicationLoader 
    implements IFloodlightModule, IApplicationService {
    
    /**
     * Representation for the application configuration
     * @author readams
     */
    public static class Application {
        private String name;
        private String[] modules;
        private Map<String,String> config;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String[] getModules() {
            return modules;
        }
        public void setModules(String[] modules) {
            this.modules = modules;
        }
        public Map<String, String> getConfig() {
            return config;
        }
        public void setConfig(Map<String, String> config) {
            this.config = config;
        }
    }
    
    
    protected static Logger logger = 
            LoggerFactory.getLogger(ApplicationLoader.class);
    protected static ObjectMapper mapper = new ObjectMapper();
    protected static ObjectReader reader = mapper.reader(Application.class);
    
    IModuleService moduleService;
    
    private static String APP_RESOURCE_PATH = "apps/";
    
    /**
     * Path containing application description files
     */
    protected String applicationPath;

    /**
     * Application to load
     */
    protected String application;

    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IApplicationService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {

        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
        new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        // We are the class that implements the service
        m.put(IApplicationService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        moduleService = context.getServiceImpl(IModuleService.class);
        
        Map<String,String> config = context.getConfigParams(this);
        if (config.containsKey("appsd"))
            applicationPath = config.get("appsd");
        if (config.containsKey("application"))
            application = config.get("application");
    }

    @Override
    public void startUp(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        if (application == null) {
            throw new FloodlightModuleException("No application to load");
        }

        // attempt to load from application path
        File appPath;
        if (applicationPath != null && 
            (appPath = new File(applicationPath)).exists() && 
            appPath.isDirectory()) {
            File[] files = appPath.listFiles();
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && f.getName().matches(".*\\.json$"));
                try {
                    if (loadApplication(new FileInputStream(f), f.getPath()))
                        return;
                } catch (FileNotFoundException e) {
                    throw new FloodlightModuleException(e);
                }
            }
        }

        // attempt to load from classpath.  Note here that the file needs
        // to be named after the application to be successful
        try {
            String r = APP_RESOURCE_PATH + application + ".json";
            InputStream is = getClass().getClassLoader().getResourceAsStream(r);
            loadApplication(is, "resource: " + r);
        } catch (Exception e) {
            throw new FloodlightModuleException(e);
        }
        
    }

    private boolean loadApplication(InputStream is, String path) 
            throws FloodlightModuleException {
        Application a;
        try {
             a = reader.readValue(is);
        } catch (Exception e) {
            throw new FloodlightModuleException("Could not read application " + 
                                                path, e);
        }
        if (application.equals(a.getName())) {
            Properties p = new Properties();
            if (a.getConfig() != null)
                p.putAll(a.getConfig());
            if (a.getModules() != null) {
                logger.info("Loading application {}", a.getName());
                moduleService.loadModulesFromList(Arrays.asList(a.getModules()),
                                                  p);
                return true;
            }
        }
        return false;
    }
}
