package org.sdnplatform.sync.internal.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;

public class StorageCCProvider
    implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(StorageCCProvider.class.getName());

    private IStorageSourceService storageSource;

    String thisControllerID;
    AuthScheme authScheme;
    String keyStorePath;
    String keyStorePassword;

    protected static final String CONTROLLER_TABLE_NAME = "controller_controller";
    protected static final String CONTROLLER_ID = "id";
    protected static final String CONTROLLER_SYNC_ID = "sync_id";
    protected static final String CONTROLLER_SYNC_DOMAIN_ID = "sync_domain_id";
    protected static final String CONTROLLER_SYNC_PORT = "sync_port";

    protected static final String CONTROLLER_INTERFACE_TABLE_NAME = "controller_controllerinterface";
    protected static final String CONTROLLER_INTERFACE_CONTROLLER_ID = "controller_id";
    protected static final String CONTROLLER_INTERFACE_DISCOVERED_IP = "discovered_ip";
    protected static final String CONTROLLER_INTERFACE_TYPE = "type";
    protected static final String CONTROLLER_INTERFACE_NUMBER = "number";

    protected static final String BOOT_CONFIG =
            "/opt/bigswitch/run/boot-config";

    // **********************
    // IClusterConfigProvider
    // **********************

    @Override
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) {
        storageSource = context.getServiceImpl(IStorageSourceService.class);

        // storageSource.addListener(CONTROLLER_TABLE_NAME, this);

        Map<String, String> config =
                context.getConfigParams(FloodlightProvider.class);
        thisControllerID = config.get("controllerid");

        config = context.getConfigParams(SyncManager.class);
        keyStorePath = config.get("keyStorePath");
        keyStorePassword = config.get("keyStorePassword");
        authScheme = AuthScheme.NO_AUTH;
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
        } catch (Exception e) {}
    }

    @Override
    public ClusterConfig getConfig() throws SyncException {
        if (thisControllerID == null) {
            Properties bootConfig = new Properties();
            FileInputStream is = null;
            try {
                is = new FileInputStream(BOOT_CONFIG);
                bootConfig.load(is);
                thisControllerID = bootConfig.getProperty("controller-id");
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
        logger.debug("Using controller ID: {}", thisControllerID);

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

        if (nodes.size() == 0)
            throw new SyncException("No valid nodes found");
        if (thisNodeId < 0)
            throw new SyncException("Could not find a node for the local node");

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
}
