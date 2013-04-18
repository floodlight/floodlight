package org.sdnplatform.sync.internal.config;

import java.util.ArrayList;
import java.util.List;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;


/**
 * Delegate cluster configuration to a list of providers
 * @author readams
 */
public class DelegatingCCProvider implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(DelegatingCCProvider.class.getName());

    List<IClusterConfigProvider> providers =
            new ArrayList<IClusterConfigProvider>();

    public void addProvider(IClusterConfigProvider provider) {
        this.providers.add(provider);
    }

    @Override
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) {
        for (IClusterConfigProvider provider : providers)
            provider.init(syncManager, context);
    }

    @Override
    public ClusterConfig getConfig() throws SyncException {
        for (IClusterConfigProvider provider : providers) {
            try {
                return provider.getConfig();
            } catch (Exception e) {
                logger.debug("ClusterConfig provider {} failed: {}",
                             provider.getClass().getSimpleName(),
                             e.getMessage());
            }
        }
        throw new SyncException("All cluster config providers failed");
    }
}
