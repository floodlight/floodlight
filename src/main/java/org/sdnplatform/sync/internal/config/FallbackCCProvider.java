package org.sdnplatform.sync.internal.config;

import java.util.Collections;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Provide a fallback local configuration
 * @author readams
 */
@LogMessageCategory("State Synchronization")
public class FallbackCCProvider extends StaticCCProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(FallbackCCProvider.class.getName());
    protected volatile boolean warned = false;

    public FallbackCCProvider() throws SyncException {
        super(new ClusterConfig(Collections.
                                singletonList(new Node("localhost",
                                                       6642,
                                                       Short.MAX_VALUE,
                                                       Short.MAX_VALUE)),
                                                       Short.MAX_VALUE));
    }

    @Override
    @LogMessageDoc(level="WARN",
        message="Using fallback local configuration",
        explanation="No other nodes are known")
    public ClusterConfig getConfig() throws SyncException {
        if (!warned) {
            logger.warn("Using fallback local configuration");
            warned = true;
        }
        return super.getConfig();
    }
}
