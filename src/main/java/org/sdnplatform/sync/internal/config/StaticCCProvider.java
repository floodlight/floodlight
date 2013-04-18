package org.sdnplatform.sync.internal.config;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class StaticCCProvider implements IClusterConfigProvider {
    public final ClusterConfig config;

    public StaticCCProvider(ClusterConfig config) {
        super();
        this.config = config;
    }

    @Override
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) {

    }

    @Override
    public ClusterConfig getConfig() throws SyncException {
        return config;
    }
}
