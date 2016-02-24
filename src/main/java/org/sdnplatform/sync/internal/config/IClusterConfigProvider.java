package org.sdnplatform.sync.internal.config;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

/**
 * Provides configuration for the sync service
 * @author readams
 */
public interface IClusterConfigProvider {
    /**
     * Initialize the provider with the configuration parameters from the
     * Floodlight module context.
     * @param config
     * @throws SyncException 
     */
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) throws SyncException;

    /**
     * Get the {@link ClusterConfig} that represents the current cluster
     * @return the {@link ClusterConfig} object
     * @throws SyncException
     */
    public ClusterConfig getConfig() throws SyncException;
}
