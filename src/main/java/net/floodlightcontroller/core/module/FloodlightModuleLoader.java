/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.core.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.module.FloodlightModulePriority.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Finds all Floodlight modules in the class path and loads/starts them.
 * @author alexreimers
 *
 */
public class FloodlightModuleLoader {
    protected static final Logger logger =
            LoggerFactory.getLogger(FloodlightModuleLoader.class);

    private Map<Class<? extends IFloodlightService>,
                  Collection<IFloodlightModule>> serviceMap;
    private Map<IFloodlightModule,
                  Collection<Class<? extends
                                   IFloodlightService>>> moduleServiceMap;
    private Map<String, IFloodlightModule> moduleNameMap;
    private List<IFloodlightModule> loadedModuleList;

    private final FloodlightModuleContext floodlightModuleContext;

    protected boolean startupModules;

    private static URI configFile;

    public static final String COMPILED_CONF_FILE =
            "floodlightdefault.properties";
    public static final String FLOODLIGHT_MODULES_KEY =
            "floodlight.modules";

    public FloodlightModuleLoader() {
        loadedModuleList = Collections.emptyList();
        floodlightModuleContext = new FloodlightModuleContext(this);
        startupModules = true;
    }

    /**
     * Gets the map of modules and their names.
     * @return An UNMODIFIABLE map of the modules and their names.
     */
    public synchronized Map<String, IFloodlightModule> getModuleNameMap() {
        if(moduleNameMap == null)
            return ImmutableMap.of();
        else
            return Collections.unmodifiableMap(moduleNameMap);
    }

    /**
     * Gets the list of modules in the order that they will be/were initialized
     * @return An UNMODIFIABLE list of loaded modules, or null if
     * not initialized.
     */
    public List<IFloodlightModule> getModuleList() {
        if (loadedModuleList == null)
            return Collections.emptyList();
        else
            return Collections.unmodifiableList(loadedModuleList);
    }

    /**
     * Return the location of the config file that was used to initialize
     * floodlight. If no config file was specified (i.e. floodlight was
     * configured from the default resource), then the return value is null.
     * @return location of the config file or null if no config file was
     * specified
     */
    public static URI getConfigFileURI() {
        return configFile;
    }

    /**
     * Finds all IFloodlightModule(s) in the classpath. It creates 3 Maps.
     * serviceMap -> Maps a service to a module
     * moduleServiceMap -> Maps a module to all the services it provides
     * moduleNameMap -> Maps the string name to the module
     * @throws FloodlightModuleException If two modules are specified in the
     * configuration that provide the same service.
     */
    protected synchronized void findAllModules(Collection<String> mList)
            throws FloodlightModuleException {
        if (serviceMap != null)
            return;

        serviceMap = new HashMap<>();
        moduleServiceMap = new HashMap<>();
        moduleNameMap = new HashMap<>();

        // Get all the current modules in the classpath
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ServiceLoader<IFloodlightModule> moduleLoader =
                ServiceLoader.load(IFloodlightModule.class, cl);
        // Iterate for each module, iterate through and add it's services
        Iterator<IFloodlightModule> moduleIter = moduleLoader.iterator();
        while (moduleIter.hasNext()) {
            IFloodlightModule m = null;
            try {
                m = moduleIter.next();
            } catch (ServiceConfigurationError sce) {
                logger.error("Could not find module: {}", sce.getMessage());
                continue;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Found module " + m.getClass().getName());
            }

            // Set up moduleNameMap
            moduleNameMap.put(m.getClass().getCanonicalName(), m);

            // Set up serviceMap
            Collection<Class<? extends IFloodlightService>> servs =
                    m.getModuleServices();
            if (servs != null) {
                moduleServiceMap.put(m, servs);
                for (Class<? extends IFloodlightService> s : servs) {
                    Collection<IFloodlightModule> mods =
                            serviceMap.get(s);
                    if (mods == null) {
                        mods = new ArrayList<IFloodlightModule>();
                        serviceMap.put(s, mods);
                    }
                    mods.add(m);
                    // Make sure they haven't specified duplicate modules in
                    // the config
                    int dupInConf = 0;
                    for (IFloodlightModule cMod : mods) {
                        if (mList.contains(cMod.getClass().getCanonicalName()))
                            dupInConf += 1;
                    }

                    if (dupInConf > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (IFloodlightModule mod : mods) {
                            sb.append(mod.getClass().getCanonicalName());
                            sb.append(", ");
                        }
                        String duplicateMods = sb.toString();
                        String mess = "ERROR! The configuration file " +
                                "specifies more than one module that " +
                                "provides the service " +
                                s.getCanonicalName() +
                                ". Please specify only ONE of the " +
                                "following modules in the config file: " +
                                duplicateMods;
                        throw new FloodlightModuleException(mess);
                    }
                }
            }
        }
    }

    /**
     * Loads the modules from a specified configuration file.
     * @param fName The configuration file path
     * @return An IFloodlightModuleContext with all the modules to be started
     * @throws FloodlightModuleException
     */
    @LogMessageDocs({
        @LogMessageDoc(level="INFO",
                message="Loading modules from {file name}",
                explanation="The controller is initializing its module " +
                        "configuration from the specified properties " +
                        "file or directory"),
        @LogMessageDoc(level="INFO",
                message="Loading default modules",
                explanation="The controller is initializing its module " +
                        "configuration to the default configuration"),
        @LogMessageDoc(level="ERROR",
                message="Could not load module configuration file",
                explanation="The controller failed to read the " +
                        "module configuration file",
                recommendation="Verify that the module configuration is " +
                        "present. " + LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Could not load default modules",
                explanation="The controller failed to read the default " +
                        "module configuration",
                recommendation=LogMessageDoc.CHECK_CONTROLLER)
    })
    public IFloodlightModuleContext loadModulesFromConfig(String fName)
            throws FloodlightModuleException {
        Properties prop = new Properties();
        Collection<String> configMods = new ArrayList<>();

        if (fName == null) {
            logger.info("Loading default modules");
            InputStream is = this.getClass().getClassLoader().
                                    getResourceAsStream(COMPILED_CONF_FILE);
            mergeProperties(is, null, configMods, prop);
        } else {
            File confFile = new File(fName);
            if (! confFile.exists())
                throw new FloodlightModuleConfigFileNotFoundException(fName);
            logger.info("Loading modules from {}", confFile.getPath());
            if (confFile.isFile()) {
                mergeProperties(null, confFile,
                                configMods, prop);
            } else {
                File[] files = confFile.listFiles();
                Arrays.sort(files);
                for (File f : files) {
                    logger.debug("Loading conf.d file {}", f.getPath());

                    if (f.isFile() &&
                        f.getName().matches(".*\\.properties$")) {
                        mergeProperties(null, f, configMods, prop);
                    }
                }
            }
        }
        return loadModulesFromList(configMods, prop);
    }

    private void mergeProperties(InputStream is,
                                 File confFile,
                                 Collection<String> configMods,
                                 Properties prop)
                                         throws FloodlightModuleException {
        try {
            Properties fprop = new Properties();
            if (is != null) {
                fprop.load(is);
            } else {
                try (FileInputStream fis = new FileInputStream(confFile)) {
                    fprop.load(fis);
                }
            }
            String moduleList = fprop.getProperty(FLOODLIGHT_MODULES_KEY);
            if (moduleList != null) {
                moduleList = moduleList.replaceAll("\\s", "");
                configMods.addAll(Arrays.asList(moduleList.split(",")));
            }
            fprop.remove(FLOODLIGHT_MODULES_KEY);

            prop.putAll(fprop);
        } catch (IOException e) {
            throw new FloodlightModuleException(e);
        }
    }

    /**
     * Loads modules (and their dependencies) specified in the list
     * @param configMods The fully-qualified module names
     * @return The ModuleContext containing all the loaded modules
     * @throws FloodlightModuleException
     */
    public synchronized IFloodlightModuleContext
            loadModulesFromList(Collection<String> configMods,
                                Properties prop)
                                        throws FloodlightModuleException {
        logger.debug("Starting module loader");

        findAllModules(configMods);

        ArrayList<IFloodlightModule> moduleList = new ArrayList<>();
        Map<Class<? extends IFloodlightService>, IFloodlightModule> moduleMap =
                new HashMap<>();
        HashSet<String> modsVisited = new HashSet<>();

        ArrayDeque<String> modsToLoad = new ArrayDeque<>(configMods);
        while (!modsToLoad.isEmpty()) {
            String moduleName = modsToLoad.removeFirst();
            traverseDeps(moduleName, modsToLoad,
                         moduleList, moduleMap, modsVisited);
        }

        parseConfigParameters(prop);

        loadedModuleList = moduleList;

        initModules(moduleList);
        if(startupModules)
            startupModules(moduleList);

        return floodlightModuleContext;
    }

    private void traverseDeps(String moduleName,
                              Collection<String> modsToLoad,
                              ArrayList<IFloodlightModule> moduleList,
                              Map<Class<? extends IFloodlightService>,
                                  IFloodlightModule> moduleMap,
                              Set<String> modsVisited)
                                      throws FloodlightModuleException {
        if (modsVisited.contains(moduleName)) return;
        modsVisited.add(moduleName);
        IFloodlightModule module = moduleNameMap.get(moduleName);
        if (module == null) {
            throw new FloodlightModuleException("Module " +
                    moduleName + " not found");
        }

        // Add its dependencies to the stack
        Collection<Class<? extends IFloodlightService>> deps =
                module.getModuleDependencies();
        if (deps != null) {
            for (Class<? extends IFloodlightService> c : deps) {
                IFloodlightModule m = moduleMap.get(c);
                if (m == null) {
                    Collection<IFloodlightModule> mods = serviceMap.get(c);
                    // Make sure only one module is loaded
                    if ((mods == null) || (mods.size() == 0)) {
                        throw new FloodlightModuleException("ERROR! Could not " +
                                "find an IFloodlightModule that provides service " +
                                c.toString());
                    } else if (mods.size() == 1) {
                        IFloodlightModule mod = mods.iterator().next();
                        traverseDeps(mod.getClass().getCanonicalName(),
                                     modsToLoad, moduleList,
                                     moduleMap, modsVisited);
                    } else {
                        boolean found = false;
                        for (IFloodlightModule moduleDep : mods) {
                            String d = moduleDep.getClass().getCanonicalName();
                            if (modsToLoad.contains(d)) {
                                modsToLoad.remove(d);
                                traverseDeps(d,
                                             modsToLoad, moduleList,
                                             moduleMap, modsVisited);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            Priority maxp = Priority.MINIMUM;
                            ArrayList<IFloodlightModule> curMax = new ArrayList<>();
                            for (IFloodlightModule moduleDep : mods) {
                                FloodlightModulePriority fmp =
                                        moduleDep.getClass().
                                        getAnnotation(FloodlightModulePriority.class);
                                Priority curp = Priority.NORMAL;
                                if (fmp != null) {
                                    curp = fmp.value();
                                }
                                if (curp.value() > maxp.value()) {
                                    curMax.clear();
                                    curMax.add(moduleDep);
                                    maxp = curp;
                                } else if  (curp.value() == maxp.value()) {
                                    curMax.add(moduleDep);
                                }
                            }

                            if (curMax.size() == 1) {
                                traverseDeps(curMax.get(0).
                                             getClass().getCanonicalName(),
                                             modsToLoad, moduleList,
                                             moduleMap, modsVisited);
                            } else {
                                StringBuilder sb = new StringBuilder();
                                for (IFloodlightModule mod : curMax) {
                                    sb.append(mod.getClass().getCanonicalName());
                                    sb.append(", ");
                                }
                                String duplicateMods = sb.toString();

                                throw new FloodlightModuleException("ERROR! Found more " +
                                        "than one (" + mods.size() + ") IFloodlightModules that provides " +
                                        "service " + c.toString() +
                                        ". This service is required for " + moduleName +
                                        ". Please specify one of the following modules in the config: " +
                                        duplicateMods);
                            }
                        }
                    }
                }
            }
        }

        // Add the module to be loaded
        addModule(moduleMap, moduleList, module);
    }

    /**
     * Add a module to the set of modules to load and register its services
     * @param moduleMap the module map
     * @param moduleList the module set
     * @param module the module to add
     */
    protected void addModule(Map<Class<? extends IFloodlightService>,
                                           IFloodlightModule> moduleMap,
                            Collection<IFloodlightModule> moduleList,
                            IFloodlightModule module) {
        Collection<Class<? extends IFloodlightService>> servs =
                moduleServiceMap.get(module);
        if (servs != null) {
            for (Class<? extends IFloodlightService> c : servs)
                moduleMap.put(c, module);
        }
        moduleList.add(module);
    }

    /**
     * Allocate  service implementations and then init all the modules
     * @param moduleSet The set of modules to call their init function on
     * @throws FloodlightModuleException If a module can not properly be loaded
     */
    protected void initModules(Collection<IFloodlightModule> moduleSet)
                                           throws FloodlightModuleException {
        for (IFloodlightModule module : moduleSet) {
            // Get the module's service instance(s)
            Map<Class<? extends IFloodlightService>,
                IFloodlightService> simpls = module.getServiceImpls();

            // add its services to the context
            if (simpls != null) {
                for (Entry<Class<? extends IFloodlightService>,
                        IFloodlightService> s : simpls.entrySet()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Setting " + s.getValue() +
                                     "  as provider for " +
                                     s.getKey().getCanonicalName());
                    }
                    if (floodlightModuleContext.getServiceImpl(s.getKey()) == null) {
                        floodlightModuleContext.addService(s.getKey(),
                                                           s.getValue());
                    } else {
                        throw new FloodlightModuleException("Cannot set "
                                                            + s.getValue()
                                                            + " as the provider for "
                                                            + s.getKey().getCanonicalName()
                                                            + " because "
                                                            + floodlightModuleContext.getServiceImpl(s.getKey())
                                                            + " already provides it");
                    }
                }
            }
        }

        for (IFloodlightModule module : moduleSet) {
            // init the module
            if (logger.isDebugEnabled()) {
                logger.debug("Initializing " +
                             module.getClass().getCanonicalName());
            }
            module.init(floodlightModuleContext);
        }
    }

    /**
     * Call each loaded module's startup method
     * @param moduleSet the module set to start up
     * @throws FloodlightModuleException
     */
    protected void startupModules(Collection<IFloodlightModule> moduleSet)
            throws FloodlightModuleException {
        for (IFloodlightModule m : moduleSet) {
            if (logger.isDebugEnabled()) {
                logger.debug("Starting " + m.getClass().getCanonicalName());
            }
            m.startUp(floodlightModuleContext);
        }
    }

    /** Tuple of floodlight module and run method */
    private static class RunMethod {
        private final IFloodlightModule module;
        private final Method method;
        public RunMethod(IFloodlightModule module, Method method) {
            this.module = module;
            this.method = method;
        }

        public void run() throws FloodlightModuleException {
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Running {}", this);
                }
                method.invoke(module);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                throw new FloodlightModuleException("Failed to invoke "
                        + "module Run method " + this, e);
            }
        }

        @Override
        public String toString() {
            return module.getClass().getCanonicalName() + "." + method;
        }


    }

    public void runModules() throws FloodlightModuleException {
        List<RunMethod> mainLoopMethods = Lists.newArrayList();

        for (IFloodlightModule m : getModuleList()) {
            for (Method method : m.getClass().getDeclaredMethods()) {
                Run runAnnotation = method.getAnnotation(Run.class);
                if (runAnnotation != null) {
                    RunMethod runMethod = new RunMethod(m, method);
                    if(runAnnotation.mainLoop()) {
                        mainLoopMethods.add(runMethod);
                    } else {
                        runMethod.run();
                    }
                }
            }
        }
        if(mainLoopMethods.size() == 1) {
            mainLoopMethods.get(0).run();
        } else if (mainLoopMethods.size() > 1) {
            throw new FloodlightModuleException("Invalid module configuration -- "
                    + "multiple run methods annotated with mainLoop detected: " + mainLoopMethods);
        }
    }

    /**
     * Parses configuration parameters for each module
     * @param prop The properties file to use
     */
    @LogMessageDoc(level="WARN",
                   message="Module {module} not found or loaded. " +
                           "Not adding configuration option {key} = {value}",
                   explanation="Ignoring a configuration parameter for a " +
                        "module that is not loaded.")
    protected void parseConfigParameters(Properties prop) {
        if (prop == null) return;

        Enumeration<?> e = prop.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();

            String configValue = null;
            int lastPeriod = key.lastIndexOf(".");
            String moduleName = key.substring(0, lastPeriod);
            String configKey = key.substring(lastPeriod + 1);
            // Check to see if it's overridden on the command line
            String systemKey = System.getProperty(key);
            if (systemKey != null) {
                configValue = systemKey;
            } else {
                configValue = prop.getProperty(key);
            }

            IFloodlightModule mod = moduleNameMap.get(moduleName);
            if (mod == null) {
                logger.debug("Module {} not found or loaded. " +
                            "Not adding configuration option {} = {}",
                            new Object[]{moduleName, configKey, configValue});
            } else {
            	logger.debug("Adding configuration option {} = {} for module {}",
                        new Object[]{configKey, configValue, moduleName});
                floodlightModuleContext.addConfigParam(mod, configKey, configValue);
            }
        }
    }

    public boolean isStartupModules() {
        return startupModules;
    }

    public void setStartupModules(boolean startupModules) {
        this.startupModules = startupModules;
    }
}
