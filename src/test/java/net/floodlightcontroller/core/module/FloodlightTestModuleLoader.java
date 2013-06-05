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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.sdnplatform.sync.test.MockSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleLoader;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.counter.NullCounterStore;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.perfmon.NullPktInProcessingTime;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.topology.TopologyManager;

public class FloodlightTestModuleLoader extends FloodlightModuleLoader {
    protected static Logger log = LoggerFactory.getLogger(FloodlightTestModuleLoader.class);

    // List of default modules to use unless specified otherwise
    public static final Class<? extends IFloodlightModule> DEFAULT_STORAGE_SOURCE =
            MemoryStorageSource.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_FLOODLIGHT_PRPOVIDER =
            MockFloodlightProvider.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_TOPOLOGY_PROVIDER =
            TopologyManager.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_DEVICE_SERVICE =
            MockDeviceManager.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_COUNTER_STORE =
            NullCounterStore.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_THREADPOOL =
            MockThreadPoolService.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_ENTITY_CLASSIFIER =
            DefaultEntityClassifier.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_PERFMON =
            NullPktInProcessingTime.class;
    public static final Class<? extends IFloodlightModule> DEFAULT_SYNC_SERVICE =
            MockSyncService.class;

    protected static final Collection<Class<? extends IFloodlightModule>> DEFAULT_MODULE_LIST;

    static {
        DEFAULT_MODULE_LIST = new ArrayList<Class<? extends IFloodlightModule>>();
        DEFAULT_MODULE_LIST.add(DEFAULT_DEVICE_SERVICE);
        DEFAULT_MODULE_LIST.add(DEFAULT_FLOODLIGHT_PRPOVIDER);
        DEFAULT_MODULE_LIST.add(DEFAULT_STORAGE_SOURCE);
        DEFAULT_MODULE_LIST.add(DEFAULT_TOPOLOGY_PROVIDER);
        DEFAULT_MODULE_LIST.add(DEFAULT_COUNTER_STORE);
        DEFAULT_MODULE_LIST.add(DEFAULT_THREADPOOL);
        DEFAULT_MODULE_LIST.add(DEFAULT_ENTITY_CLASSIFIER);
        DEFAULT_MODULE_LIST.add(DEFAULT_PERFMON);
        DEFAULT_MODULE_LIST.add(DEFAULT_SYNC_SERVICE);
    }

    protected IFloodlightModuleContext fmc;

    /**
     * Adds default modules to the list of modules to load. This is done
     * in order to avoid the module loader throwing errors about duplicate
     * modules and neither one is specified by the user.
     * @param userModules The list of user specified modules to add to.
     */
    protected void addDefaultModules(Collection<Class<? extends IFloodlightModule>> userModules) {
        Collection<Class<? extends IFloodlightModule>> defaultModules =
                new ArrayList<Class<? extends IFloodlightModule>>(DEFAULT_MODULE_LIST.size());
        defaultModules.addAll(DEFAULT_MODULE_LIST);

        Iterator<Class<? extends IFloodlightModule>> modIter = userModules.iterator();
        while (modIter.hasNext()) {
            Class<? extends IFloodlightModule> userMod = modIter.next();
            Iterator<Class<? extends IFloodlightModule>> dmIter = defaultModules.iterator();
            while (dmIter.hasNext()) {
                Class<? extends IFloodlightModule> dmMod = dmIter.next();
                Collection<Class<? extends IFloodlightService>> userModServs;
                Collection<Class<? extends IFloodlightService>> dmModServs;
                try {
                    dmModServs = dmMod.newInstance().getModuleServices();
                    userModServs = userMod.newInstance().getModuleServices();
                } catch (InstantiationException e) {
                    log.error(e.getMessage());
                    break;
                } catch (IllegalAccessException e) {
                    log.error(e.getMessage());
                    break;
                }

                // If either of these are null continue as they have no services
                if (dmModServs == null || userModServs == null) continue;

                // If the user supplied modules has a service
                // that is in the default module list we remove
                // the default module from the list.
                boolean shouldBreak = false;
                Iterator<Class<? extends IFloodlightService>> userModServsIter
                    = userModServs.iterator();
                while (userModServsIter.hasNext()) {
                    Class<? extends IFloodlightService> userModServIntf = userModServsIter.next();
                    Iterator<Class<? extends IFloodlightService>> dmModsServsIter
                        = dmModServs.iterator();
                    while (dmModsServsIter.hasNext()) {
                        Class<? extends IFloodlightService> dmModServIntf
                            = dmModsServsIter.next();

                        if (dmModServIntf.getCanonicalName().equals(
                                userModServIntf.getCanonicalName())) {
                            logger.debug("Removing default module {} because it was " +
                                    "overriden by an explicitly specified module",
                                    dmModServIntf.getCanonicalName());
                            dmIter.remove();
                            shouldBreak = true;
                            break;
                        }
                    }
                    if (shouldBreak) break;
                }
                if (shouldBreak) break;
            }
        }

        // Append the remaining default modules to the user specified ones.
        // This avoids the module loader throwing duplicate module errors.
        userModules.addAll(defaultModules);
        log.debug("Using module set " + userModules.toString());
    }

    /**
     * Sets up all modules and their dependencies.
     * @param modules The list of modules that the user wants to load.
     * @param mockedServices The list of services that will be mocked. Any
     * module that provides this service will not be loaded.
     */
    public void setupModules(Collection<Class<? extends IFloodlightModule>> modules,
            Collection<IFloodlightService> mockedServices) throws FloodlightModuleException {
        addDefaultModules(modules);
        Collection<String> modulesAsString = new ArrayList<String>();
        for (Class<? extends IFloodlightModule> m : modules) {
            modulesAsString.add(m.getCanonicalName());
        }

        fmc = loadModulesFromList(modulesAsString, null, mockedServices);
    }

    /**
     * Gets the inited/started instance of a module from the context.
     * @param ifl The name if the module to get, i.e. "LearningSwitch.class".
     * @return The inited/started instance of the module.
     */
    public IFloodlightModule getModuleByName(Class<? extends IFloodlightModule> ifl) {
        Collection<IFloodlightModule> modules = fmc.getAllModules();
        for (IFloodlightModule m : modules) {
            if (ifl.getCanonicalName().equals(m.getClass().getCanonicalName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * Gets an inited/started instance of a service from the context.
     * @param ifs The name of the service to get, i.e. "ITopologyService.class".
     * @return The inited/started instance of the service from teh context.
     */
    public IFloodlightService getModuleByService(Class<? extends IFloodlightService> ifs) {
        Collection<IFloodlightModule> modules = fmc.getAllModules();
        for (IFloodlightModule m : modules) {
            Collection<Class<? extends IFloodlightService>> mServs = m.getModuleServices();
            if (mServs == null) continue;
            for (Class<? extends IFloodlightService> mServClass : mServs) {
                if (mServClass.getCanonicalName().equals(ifs.getCanonicalName())) {
                    assert(m instanceof IFloodlightService);
                    return (IFloodlightService)m;
                }
            }
        }
        return null;
    }
}
