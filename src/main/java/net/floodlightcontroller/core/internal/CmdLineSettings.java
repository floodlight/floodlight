package net.floodlightcontroller.core.internal;

import org.kohsuke.args4j.Option;

/**
 * Expresses the port settings of OpenFlow controller.
 */
public class CmdLineSettings {
    public static final String DEFAULT_CONFIG_FILE = "config/floodlight.properties";

    @Option(name="-cf", aliases="--configFile", metaVar="FILE", usage="Floodlight configuration file")
    private String configFile = DEFAULT_CONFIG_FILE;
    
    public String getModuleFile() {
    	return configFile;
    }
}
