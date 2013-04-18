package org.sdnplatform.sync.internal.config;

import java.util.Map;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        Short thisNodeId;
        try {
            thisNodeId = Short.parseShort(config.get("thisNode"));
        } catch (NumberFormatException e) {
            throw new SyncException("Failed to parse thisNode " +
                    "node ID: " + config.get("thisNode"), e);
        }
        try {
            return new ClusterConfig(config.get("nodes"), thisNodeId);
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
