package org.sdnplatform.sync.internal.config;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.types.DatapathId;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;


/**
 * Provide a fallback local configuration
 * @author readams
 */
public class FallbackCCProvider implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(FallbackCCProvider.class.getName());
    protected volatile boolean warned = false;
    AuthScheme authScheme;
    String keyStorePath;
    String keyStorePassword;
    
    int syncPort = 6642;
    
    public FallbackCCProvider() throws SyncException {
        
    }

    @Override
    public ClusterConfig getConfig() throws SyncException {
        if (!warned) {
            logger.info("Cluster not yet configured; using fallback local " + 
                        "configuration");
            warned = true;
        }
        return new ClusterConfig(Collections.
                                 singletonList(new Node("localhost",
                                                        syncPort,
                                                        Short.MAX_VALUE,
                                                        Short.MAX_VALUE)),
                                                        Short.MAX_VALUE,
                                                        "localhost",
                                                        authScheme,
                                                        keyStorePath,
                                                        keyStorePassword);
    }

    
    
    @Override
    public void init(SyncManager syncManager, FloodlightModuleContext context) {
        Map<String, String> config = context.getConfigParams(syncManager);
        keyStorePath = config.get("keyStorePath");
        keyStorePassword = config.get("keyStorePassword");
        
       
        
        authScheme = AuthScheme.NO_AUTH;
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
            syncPort = Integer.parseInt(config.get("port"));
        } catch (Exception e) {}
    }
}
