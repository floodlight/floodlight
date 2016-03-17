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

public class StorageCCProvider
    implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(StorageCCProvider.class.getName());

    private IStorageSourceService storageSource;
    
	private List<Node> clusterInitialNode;

    String thisControllerID;
    AuthScheme authScheme;
    String keyStorePath;
    String keyStorePassword;

    protected static final String CONTROLLER_TABLE_NAME = "controller_controller";
    protected static final String CONTROLLER_ID = "controllerId";
    protected static final String CONTROLLER_SYNC_ID = "sync_id";
    protected static final String CONTROLLER_SYNC_DOMAIN_ID = "sync_domain_id";
    protected static final String CONTROLLER_SYNC_PORT = "sync_port";

    protected static final String CONTROLLER_INTERFACE_TABLE_NAME = "controller_controllerinterface";
    protected static final String CONTROLLER_INTERFACE_CONTROLLER_ID = "controller_id";
    protected static final String CONTROLLER_INTERFACE_DISCOVERED_IP = "discovered_ip";
    protected static final String CONTROLLER_INTERFACE_TYPE = "type";
    protected static final String CONTROLLER_INTERFACE_NUMBER = "number";

    //protected static final String BOOT_CONFIG =
      //      "/opt/bigswitch/run/boot-config";

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
        thisControllerID = config.get("controllerId");
        config = context.getConfigParams(SyncManager.class);
        
        logger.info("ControllerId: {}", thisControllerID);
        
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
        if (thisControllerID == null) {
            Properties bootConfig = new Properties();
            FileInputStream is = null;
            try {
                is = new FileInputStream(BOOT_CONFIG);
                bootConfig.load(is);
                thisControllerID = bootConfig.getProperty("controllerId");
            } catch (Exception e) {
                throw new SyncException("No controller ID configured and " +
                                        "could not read " + BOOT_CONFIG);
            } finally {
                if (is != null) try {
                    is.close();
                } catch (IOException e) {
                    throw new SyncException(e);
                }
            }
        }
        if (thisControllerID == null) {
            throw new SyncException("No controller ID configured");
        }
        logger.info("Using controller ID: {}", thisControllerID);

        List<Node> nodes = new ArrayList<Node>();
        short thisNodeId = -1;

        String[] cols = {CONTROLLER_ID,
                         CONTROLLER_SYNC_ID,
                         CONTROLLER_SYNC_DOMAIN_ID,
                         CONTROLLER_SYNC_PORT};
        IResultSet res = null;
        try {
            res = storageSource.executeQuery(CONTROLLER_TABLE_NAME,
                                             cols, null, null);
            while (res.next()) {
                String controllerId = res.getString(CONTROLLER_ID);
                if (!res.containsColumn(CONTROLLER_SYNC_ID) ||
                    !res.containsColumn(CONTROLLER_SYNC_DOMAIN_ID) ||
                    !res.containsColumn(CONTROLLER_SYNC_PORT)) {
                    logger.debug("No sync data found for {}", controllerId);
                    continue;
                }

                short nodeId = res.getShort(CONTROLLER_SYNC_ID);
                short domainId = res.getShort(CONTROLLER_SYNC_DOMAIN_ID);
                int port = res.getInt(CONTROLLER_SYNC_PORT);
                String syncIp = getNodeIP(controllerId);
                if (syncIp == null) {
                    logger.debug("No sync IP found for {}", controllerId);
                    continue;
                }
                Node node = new Node(syncIp, port, nodeId, domainId);
                nodes.add(node);

                if (thisControllerID.equals(controllerId))
                    thisNodeId = nodeId;
            }
        } finally {
            if (res != null) res.close();
        }

        nodes.add(new Node("192.168.1.131", 6642, (short)1, (short)1));
        nodes.add(new Node("192.168.1.131", 6643, (short)2, (short)2));
       
       
        if (nodes.size() == 0)
            throw new SyncException("No valid nodes found");
        if (thisNodeId < 0)
            throw new SyncException("Could not find a node for the local node");

        logger.info("Nodes: {}",nodes);
        return new ClusterConfig(nodes, thisNodeId, authScheme, 
                                 keyStorePath, keyStorePassword);
    }

    // *************
    // Local methods
    // *************

    private String getNodeIP(String controllerID) {

        String[] cols = {CONTROLLER_INTERFACE_CONTROLLER_ID,
                         CONTROLLER_INTERFACE_TYPE,
                         CONTROLLER_INTERFACE_NUMBER,
                         CONTROLLER_INTERFACE_DISCOVERED_IP};
        IResultSet res = null;
        try {
            res = storageSource.executeQuery(CONTROLLER_INTERFACE_TABLE_NAME,
                                             cols, null, null);
            while (res.next()) {
                logger.debug("{} {} {} {}",
                             new Object[] {res.getString(CONTROLLER_INTERFACE_CONTROLLER_ID),
                                           res.getString(CONTROLLER_INTERFACE_TYPE),
                                           res.getIntegerObject(CONTROLLER_INTERFACE_NUMBER),
                                           res.getString(CONTROLLER_INTERFACE_DISCOVERED_IP)});
                if ("Ethernet".equals(res.getString(CONTROLLER_INTERFACE_TYPE)) &&
                    Integer.valueOf(0).equals(res.getIntegerObject(CONTROLLER_INTERFACE_NUMBER)) &&
                    controllerID.equals(res.getString(CONTROLLER_INTERFACE_CONTROLLER_ID)))
                    return res.getString(CONTROLLER_INTERFACE_DISCOVERED_IP);
            }
            return null;

        } finally {
            if (res != null) res.close();
        }
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
