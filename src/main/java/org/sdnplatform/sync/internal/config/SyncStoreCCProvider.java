package org.sdnplatform.sync.internal.config;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.config.bootstrap.BootstrapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.net.HostAndPort;

/**
 * Configure sync service from a persistent sync store, and support 
 * bootstrapping without manually configuring it.
 * @author readams
 */
public class SyncStoreCCProvider 
    implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(SyncStoreCCProvider.class);

    private SyncManager syncManager;
    private IThreadPoolService threadPool; 

    private SingletonTask bootstrapTask;

    private IStoreClient<Short, Node> nodeStoreClient;
    private IStoreClient<String, String> unsyncStoreClient;
    
    private volatile AuthScheme authScheme;
    private volatile String keyStorePath;
    private volatile String keyStorePassword;
    
    private static final String PREFIX = 
            SyncManager.class.getCanonicalName();
    public static final String SYSTEM_NODE_STORE = 
            PREFIX + ".systemNodeStore";
    public static final String SYSTEM_UNSYNC_STORE = 
            PREFIX + ".systemUnsyncStore";

    public static final String SEEDS = "seeds";
    public static final String LOCAL_NODE_ID = "localNodeId";
    public static final String LOCAL_NODE_IFACE = "localNodeIface";
    public static final String LOCAL_NODE_HOSTNAME = "localNodeHostname";
    public static final String LOCAL_NODE_PORT = "localNodePort";
    public static final String AUTH_SCHEME = "authScheme";
    public static final String KEY_STORE_PATH = "keyStorePath";
    public static final String KEY_STORE_PASSWORD = "keyStorePassword";

    Map<String, String> config;
    
    // **********************
    // IClusterConfigProvider
    // **********************

    @Override
    public void init(SyncManager syncManager, FloodlightModuleContext context) 
            throws SyncException {
        this.syncManager = syncManager;
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        syncManager.registerPersistentStore(SYSTEM_NODE_STORE, Scope.GLOBAL);
        syncManager.registerPersistentStore(SYSTEM_UNSYNC_STORE, 
                                            Scope.UNSYNCHRONIZED);
        this.nodeStoreClient = 
                syncManager.getStoreClient(SYSTEM_NODE_STORE, 
                                           Short.class, Node.class);
        this.nodeStoreClient.addStoreListener(new ShortListener());
        this.unsyncStoreClient = 
                syncManager.getStoreClient(SYSTEM_UNSYNC_STORE, 
                                           String.class, String.class);
        this.unsyncStoreClient.addStoreListener(new StringListener());
        
        config = context.getConfigParams(syncManager);
    }

    @Override
    public ClusterConfig getConfig() throws SyncException {
        if (bootstrapTask == null)
            bootstrapTask = new SingletonTask(threadPool.getScheduledExecutor(), 
                                              new BootstrapTask());

        keyStorePath = config.get("keyStorePath");
        keyStorePassword = config.get("keyStorePassword");
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
        } catch (Exception e) {
            authScheme = null;
        }

        if (keyStorePath == null)
            keyStorePath = unsyncStoreClient.getValue(KEY_STORE_PATH);
        if (keyStorePassword == null)
            keyStorePassword = 
                unsyncStoreClient.getValue(KEY_STORE_PASSWORD);
        if (authScheme == null) {
            try {
                authScheme = 
                        AuthScheme.valueOf(unsyncStoreClient.
                                           getValue(AUTH_SCHEME));
            } catch (Exception e) {
                authScheme = AuthScheme.NO_AUTH;
            }
        }

        Short localNodeId = getLocalNodeId();
        if (localNodeId == null) {
            String seedStr = 
                    unsyncStoreClient.getValue(SyncStoreCCProvider.SEEDS);
            if (seedStr == null) {
                throw new SyncException("No local node ID and no seeds");
            }
            bootstrapTask.reschedule(0, TimeUnit.SECONDS);
            throw new SyncException("Local node ID not yet configured");
        }
        
        IClosableIterator<Entry<Short, Versioned<Node>>> iter = 
                nodeStoreClient.entries();
        List<Node> nodes = new ArrayList<Node>();
        try {
            while (iter.hasNext()) {
                Entry<Short, Versioned<Node>> e = iter.next();
                if (e.getValue().getValue() != null) {
                    if (e.getValue().getValue().getNodeId() == localNodeId)
                        continue;
                    nodes.add(e.getValue().getValue());
                }
            }

            Node oldLocalNode = null;
            Node newLocalNode = null;
            while (true) {
                try {
                    Versioned<Node> v =
                            nodeStoreClient.get(Short.valueOf(localNodeId));
                    oldLocalNode = v.getValue();
                    if (oldLocalNode != null) {
                        newLocalNode = getLocalNode(oldLocalNode.getNodeId(),
                                                    oldLocalNode.getDomainId());
                        v.setValue(newLocalNode);
                    }
                    break;
                } catch (ObsoleteVersionException e) { }
            }
            if (newLocalNode == null) {
                newLocalNode = getLocalNode(localNodeId, localNodeId);
            }
            nodes.add(newLocalNode);
            
            if (oldLocalNode == null || !oldLocalNode.equals(newLocalNode)) {
                // If we have no local node or our hostname or port changes, 
                // we should trigger a new cluster join to ensure that the 
                // new value can propagate everywhere
                bootstrapTask.reschedule(0, TimeUnit.SECONDS);    
            }
            
            ClusterConfig config = new ClusterConfig(nodes, localNodeId,
                                                     authScheme,
                                                     keyStorePath, 
                                                     keyStorePassword);
            updateSeeds(syncManager.getClusterConfig());
            return config;
        } finally {
            iter.close();
        }
    }
    
    // *************
    // Local methods 
    // *************

    private Short getLocalNodeId() throws SyncException {
        String localNodeIdStr = unsyncStoreClient.getValue(LOCAL_NODE_ID);
        if (localNodeIdStr == null)
            return null;

        short localNodeId;
        try {
            localNodeId = Short.parseShort(localNodeIdStr);
        } catch (NumberFormatException e) {
            throw new SyncException("Failed to parse local node ID: " + 
                                    localNodeIdStr, e);
        }
        return localNodeId;
    }
    
    private void updateSeeds(ClusterConfig config) throws SyncException {
        List<String> hosts = new ArrayList<String>();
        for (Node n : config.getNodes()) {
            if (!config.getNode().equals(n)) {
                HostAndPort h = 
                        HostAndPort.fromParts(n.getHostname(), n.getPort());
                hosts.add(h.toString());
            }
        }
        Collections.sort(hosts);
        String seeds = Joiner.on(',').join(hosts);
        while (true) {
            try {
                Versioned<String> sv = unsyncStoreClient.get(SEEDS);
                if (sv.getValue() == null || !sv.getValue().equals(seeds)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Updating seeds to \"{}\" from \"{}\"", 
                                     new Object[]{config.getNode().getNodeId(),
                                                  seeds, sv.getValue()});
                    }
                    unsyncStoreClient.put(SEEDS, seeds);
                }
                break;
            } catch (ObsoleteVersionException e) { }
        }
    }
    
    private Node getLocalNode(short nodeId, short domainId) 
            throws SyncException {
        String hostname = unsyncStoreClient.getValue(LOCAL_NODE_HOSTNAME);
        if (hostname == null) 
            hostname = getLocalHostname();

        int port = 6642;
        String portStr = unsyncStoreClient.getValue(LOCAL_NODE_PORT);
        if (portStr != null) {
            port = Integer.parseInt(portStr);
        }

        return new Node(hostname, port, nodeId, domainId);
    }
    
    private String getLocalHostname() throws SyncException {
        String ifaceStr = unsyncStoreClient.getValue(LOCAL_NODE_IFACE);
        
        try {
            Enumeration<NetworkInterface> ifaces = 
                    NetworkInterface.getNetworkInterfaces();

            InetAddress bestAddr = null;
            for (NetworkInterface iface : Collections.list(ifaces)) {
                try {
                    if (iface.isLoopback()) continue;
                    if (ifaceStr != null) {
                        if (!ifaceStr.equals(iface.getName()))
                            continue;
                    }
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    for (InetAddress addr : Collections.list(addrs)) {
                        if (bestAddr == null ||
                            (!addr.isLinkLocalAddress() && 
                             bestAddr.isLinkLocalAddress()) ||
                             (addr instanceof Inet6Address &&
                              bestAddr instanceof Inet4Address)) {
                            bestAddr = addr;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to examine address", e);
                }
            }
            if (bestAddr != null)
                return bestAddr.getHostName();
        } catch (Exception e) {
            throw new SyncException("Failed to find interface addresses", e);
        }
        throw new SyncException("No usable interface addresses found");
    }

    protected class BootstrapTask implements Runnable {
        @Override
        public void run() {
            Short localNodeId = null;
            try {
                Node localNode = null;

                localNodeId = getLocalNodeId();
                if (localNodeId != null)
                    localNode = nodeStoreClient.getValue(localNodeId);

                String seedStr = 
                        unsyncStoreClient.getValue(SyncStoreCCProvider.SEEDS);
                if (seedStr == null) return;

                logger.debug("[{}] Attempting to bootstrap cluster", 
                             localNodeId);

                if (seedStr.equals("")) {
                    localNode = setupLocalNode(localNode, localNodeId, true);
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] First node configuration: {}",
                                     localNode.getNodeId(), localNode);
                    }

                    while (true) {
                        try {
                            nodeStoreClient.put(localNode.getNodeId(), 
                                                localNode);
                            break;
                        } catch (ObsoleteVersionException e) {}
                    }

                    while (true) {
                        try {
                            unsyncStoreClient.put(LOCAL_NODE_ID, 
                                                  Short.toString(localNode.
                                                                 getNodeId()));
                            break;
                        } catch (ObsoleteVersionException e) {}
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Successfully bootstrapped", 
                                     localNode.getNodeId());
                    }
                } else {
                    localNode = setupLocalNode(localNode, localNodeId, false);
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Adding new node from seeds {}: {}", 
                                     new Object[]{localNodeId, seedStr, 
                                                  localNode});
                    }
                    
                    String[] seeds = seedStr.split(",");
                    ArrayList<HostAndPort> hosts = new ArrayList<HostAndPort>();
                    for (String s : seeds) {
                        hosts.add(HostAndPort.fromString(s).
                                      withDefaultPort(6642));
                    }
                    BootstrapClient bs = new BootstrapClient(syncManager,
                                                 authScheme,
                                                 keyStorePath, 
                                                 keyStorePassword);
                    bs.init();
                    try {
                        for (HostAndPort host : hosts) {
                            if (bs.bootstrap(host, localNode))
                                break;
                        }
                    } finally {
                        bs.shutdown();
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Successfully bootstrapped", 
                                     unsyncStoreClient.getValue(LOCAL_NODE_ID));
                    }
                }
                syncManager.updateConfiguration();
            } catch (Exception e) {
                logger.error("[" + localNodeId + 
                             "] Failed to bootstrap cluster", e);
            }
        }

        private Node setupLocalNode(Node localNode, Short localNodeId,
                                    boolean firstNode) 
                throws SyncException {
            short nodeId = -1;
            short domainId = -1;
            if (localNode != null) {
                nodeId = localNode.getNodeId();
                domainId = localNode.getDomainId();
            } else if (localNodeId != null) {
                domainId = nodeId = localNodeId;
            } else if (firstNode) {
                domainId = nodeId = 
                        (short)(new Random().nextInt(Short.MAX_VALUE));
            }
            Node n = getLocalNode(nodeId, domainId);
            return n;
        }
    }
    
    protected class ShortListener implements IStoreListener<Short> {
        @Override
        public void keysModified(Iterator<Short> keys, UpdateType type) {
            syncManager.updateConfiguration();
        }        
    }

    protected class StringListener implements IStoreListener<String> {
        @Override
        public void keysModified(Iterator<String> keys, UpdateType type) {
            syncManager.updateConfiguration();
        }        
    }
}
