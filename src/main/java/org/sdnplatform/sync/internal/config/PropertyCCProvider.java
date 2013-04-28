package org.sdnplatform.sync.internal.config;

import java.util.List;
import java.util.Map;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class PropertyCCProvider implements IClusterConfigProvider {
    protected static Logger logger =
            LoggerFactory.getLogger(PropertyCCProvider.class.getName());

    private Map<String, String> config;

    @Override
    public ClusterConfig getConfig() throws SyncException {
        if (!config.containsKey("nodes") || !config.containsKey("thisNode"))
            throw new SyncException("Configuration properties nodes or " +
                    "thisNode not set");

        String keyStorePath = config.get("keyStorePath");
        String keyStorePassword = config.get("keyStorePassword");
        AuthScheme authScheme = AuthScheme.NO_AUTH;
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
        } catch (Exception e) {}
        
        Short thisNodeId;
        try {
            thisNodeId = Short.parseShort(config.get("thisNode"));
        } catch (NumberFormatException e) {
            throw new SyncException("Failed to parse thisNode " +
                    "node ID: " + config.get("thisNode"), e);
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Node> nodes = 
                    mapper.readValue(config.get("nodes"),
                                     new TypeReference<List<Node>>() { });
            return new ClusterConfig(nodes, thisNodeId, 
                                     authScheme, 
                                     keyStorePath, 
                                     keyStorePassword);
        } catch (Exception e) {
            throw new SyncException("Could not update " +
                    "configuration", e);
        }
    }    
    
    @Override
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) {
        this.config = context.getConfigParams(syncManager);
    }
}
