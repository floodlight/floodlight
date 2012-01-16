package net.floodlightcontroller.core.internal;

import org.kohsuke.args4j.Option;

/**
 * Expresses the command line settings of the OpenFlow controller.
 */
public class CmdLineSettings {
    private final int DEFAULT_OPENFLOW_PORT = 6633;
    private final int DEFAULT_REST_PORT = 8080;
    private final boolean DEFAULT_CBENCH_SUPPORT = false;

    @Option(name="-ofp", aliases="--openFlowPort",metaVar="PORT", usage="Port number for OpenFlow")
    private int openFlowPort = DEFAULT_OPENFLOW_PORT;
    
    @Option(name="-rp", aliases="--restPort", metaVar="PORT", usage="Port number for REST API")
    private int restPort = DEFAULT_REST_PORT;
    
    @Option(name="-cbench", aliases="--cbenchSupport", usage="Support for cbench (dummy) switch")
    private boolean cbenchSupported = DEFAULT_CBENCH_SUPPORT;
    
    public int getOpenFlowPort() {
        return openFlowPort;
    }

    public int getRestPort() {
        return restPort;
    }
    
    public boolean isCbenchSupported() {
    	return cbenchSupported;
    }
}
