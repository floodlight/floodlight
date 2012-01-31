package net.floodlightcontroller.core.internal;

import org.kohsuke.args4j.Option;

/**
 * Expresses the port settings of OpenFlow controller.
 */
public class CmdLineSettings {
    private final int DEFAULT_OPENFLOW_PORT = 6633;
    private final int DEFAULT_REST_PORT = 8080;
    private static String DEFAULT_CONFIG_FILE = "floodlightconfig.json";
    
    @Option(name="-ofp", aliases="--openFlowPort",metaVar="PORT", usage="Port number for OpenFlow")
    private int openFlowPort = DEFAULT_OPENFLOW_PORT;
    @Option(name="-rp", aliases="--restPort", metaVar="PORT", usage="Port number for REST API")
    private int restPort = DEFAULT_REST_PORT;
    @Option(name="-cf", aliases="--configFile", metaVar="FILE", usage="Floodlight configuration file")
    private String configFile = DEFAULT_CONFIG_FILE;
    
    public int getOpenFlowPort() {
        return openFlowPort;
    }

    public int getRestPort() {
        return restPort;
    }
    
    public String getModuleFile() {
    	return configFile;
    }
}
