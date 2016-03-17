package org.sdnplatform.sync.internal.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.python.antlr.PythonParser.print_stmt_return;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;

public class FTCCProvider
    implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(FTCCProvider.class.getName());

    private IStorageSourceService storageSource;
    
	private List<Node> clusterInitialNode;

    String controllerID;
    AuthScheme authScheme;
    String keyStorePath;
    String keyStorePassword;

    protected static final String BOOT_CONFIG = 
    		"/src/main/resources/floodlight/storageBootstrap.properties";

    // **********************
    // IClusterConfigProvider
    // **********************

    @Override
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) {
        storageSource = context.getServiceImpl(IStorageSourceService.class);

        //storageSource.addListener(CONTROLLER_TABLE_NAME, this);

        Map<String, String> config =
                context.getConfigParams(FloodlightProvider.class);
        controllerID = config.get("controllerId");
        config = context.getConfigParams(SyncManager.class);
        
        logger.info("ControllerId: {}", controllerID);
        
        keyStorePath = config.get("keyStorePath");
        keyStorePassword = config.get("keyStorePassword");
        authScheme = AuthScheme.NO_AUTH;
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
        } catch (Exception e) {}
        String clusterNodes = config.get("clusterNodes");
		clusterInitialNode = jsonToNodeMap(clusterNodes, config.get("nodeId"));
		logger.info("Initial Cluster Node: {} {}",config.get("nodeId"),clusterInitialNode);

    
    }

    @Override
    public ClusterConfig getConfig() throws SyncException {
        if (controllerID == null) {
            Properties bootConfig = new Properties();
            
        }
        if (controllerID == null) {
            throw new SyncException("No controller ID configured");
        }
        logger.info("Using controller ID: {}", controllerID);

        List<Node> nodes = new ArrayList<Node>();
        //short thisNodeId = -1;

        nodes.add(new Node("192.168.1.131", 6642, (short)1, (short)1));
        nodes.add(new Node("192.168.1.131", 6643, (short)2, (short)1));
        ClusterConfig cc =null;
        try {
        	cc = new ClusterConfig(nodes, Short.parseShort(controllerID), authScheme, 
                    keyStorePath, keyStorePassword);
        	logger.info("ClusterConfig: {}",cc);
        } catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
        
        
        
        return cc;
    }

       /**
	 * @param String org.sdnplatform.sync.internal.SyncManager.clusterNodes foodlightdefault.properties.
	 * @param String controllerId
	 * @return Map<String, Node>
	 */
	private static List<Node> jsonToNodeMap(String json, String controllerId) {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		List<Node> nodes = new ArrayList<Node>();

		if (json == null || json.isEmpty()) {
			return nodes;
		}

		try {
			try {
				jp = f.createParser(json);
			} catch (JsonParseException e) {
				throw new IOException(e);
			}

			jp.nextToken();
			if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
				throw new IOException("Expected START_OBJECT");
			}

			while (jp.nextToken() != JsonToken.END_OBJECT) {
				if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
					throw new IOException("Expected FIELD_NAME");
				}

				String nodeId = jp.getCurrentName();

				String host=null;
				String domainId = controllerId;
				String [] aux;
				int port;
				Node node=null;

				jp.nextToken();
				if (jp.getText().equals("")) {
					continue;
				}
				host = jp.getValueAsString();

				aux= host.split(":");
				host = aux[0];
				port = Integer.parseInt(aux[1]);
				try {
					logger.debug("Creating node: {}:{} {} {}", 
							new Object[]{host, port, nodeId, nodeId}
							);
					node = new Node(host, port, Short.parseShort(nodeId), Short.parseShort(nodeId));
					nodes.add(node);
				} catch(Exception e){
					e.printStackTrace();
				}

			}
		} catch (IOException e) {
			logger.error("Problem: {}", e);
		}
		return nodes;
	}


   

}
