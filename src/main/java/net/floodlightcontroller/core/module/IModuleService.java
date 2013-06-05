package net.floodlightcontroller.core.module;

import java.util.Collection;
import java.util.Properties;

public interface IModuleService extends IFloodlightService {
    /**
     * Loads modules (and their dependencies) specified in the list.
     * @param configMods The collection of fully qualified module names to load.
     * @param prop The list of properties that are configuration options.
     * @return The ModuleContext containing all the loaded modules.
     * @throws FloodlightModuleException
     */
    public IFloodlightModuleContext 
        loadModulesFromList(Collection<String> configMods, 
                            Properties prop) throws FloodlightModuleException; 

}
