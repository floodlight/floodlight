package org.sdnplatform.sync.client;

import java.util.ArrayList;
import org.kohsuke.args4j.Option;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.internal.config.Node;
import org.sdnplatform.sync.internal.config.SyncStoreCCProvider;

import com.google.common.base.Joiner;
import com.google.common.net.HostAndPort;

/**
 * This tool makes bootstrapping a cluster simpler by writing the appropriate
 * configuration parameters to the local system store 
 * @author readams
 */
public class BootstrapTool extends SyncClientBase {

    /**
     * Command-line settings
     */
    protected BootstrapToolSettings bSettings;

    protected static class BootstrapToolSettings 
        extends SyncClientBaseSettings {
        
        @Option(name="--seeds", aliases="-s",
                usage="Comma-separated list of seeds specified as " + 
                      "ipaddr:port, such as 192.168.5.2:6642,192.168.6.2:6642")
        protected String seeds;
        
        @Option(name="--localNodeDomain", aliases="-d",
                usage="Set the domain ID of the local node")
        protected short domainId;

        @Option(name="--localNodePort",
                usage="Set the listen port for the local node")
        protected int localNodePort;

        @Option(name="--localNodeIface",
                usage="Set the interface name to listen on for the local node")
        protected String localNodeIface;

        @Option(name="--localNodeHost",
                usage="Set the hostname for the local node (overrides " + 
                      "localNodeIface)")
        protected String localNodeHost;
        
        @Option(name="--reseed", aliases="-r",
                usage="If you simultaneously change the IP of every node " + 
                      "in the cluster, the cluster may not automatically " +
                      "reform.  Run this command to cause it to rerun the " +
                      "bootstrap process while retaining existing node IDs. " + 
                      "The node will be put into its own local domain.")
        protected boolean reseed;
        
        @Option(name="--delete", 
                usage="Remove the specified node from the cluster.  Note " +
                      "that if the node is still active it will rejoin " + 
                      "automatically, so only run this once the node has " + 
                      "been disabled.")
        protected short deleteNode;
    }

    public BootstrapTool(BootstrapToolSettings bootstrapSettings) {
        super(bootstrapSettings);
        this.bSettings = bootstrapSettings;
    }

    protected void bootstrap() throws Exception {
        IStoreClient<String, String> uStoreClient = 
                syncManager.getStoreClient(SyncStoreCCProvider.
                                           SYSTEM_UNSYNC_STORE, 
                                           String.class, String.class);
        IStoreClient<Short, Node> nodeStoreClient = 
                syncManager.getStoreClient(SyncStoreCCProvider.
                                           SYSTEM_NODE_STORE, 
                                           Short.class, Node.class);
        
        if (bSettings.localNodeIface != null) {
            while (true) {
                try {
                    uStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_IFACE,
                                     bSettings.localNodeIface);
                    break;
                } catch (ObsoleteVersionException e) {}
            }
        }
        if (bSettings.localNodePort != 0) {
            while (true) {
                try {
                    uStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_IFACE,
                                     Integer.toString(bSettings.localNodePort));
                    break;
                } catch (ObsoleteVersionException e) {}
            }
        }
        if (bSettings.localNodeHost != null) {
            while (true) {
                try {
                    uStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_HOSTNAME,
                                     bSettings.localNodeHost);
                    break;
                } catch (ObsoleteVersionException e) {}
            }
        }
        if (bSettings.seeds != null) {
            String[] seedsStr = bSettings.seeds.split(",");
            boolean seedsvalid = true;
            ArrayList<HostAndPort> seedsList = new ArrayList<HostAndPort>();
            for (String s : seedsStr) {
                try {
                    seedsList.add(HostAndPort.fromString(s));
                } catch (IllegalArgumentException e) {
                    err.println("Invalid seed: " + e.getMessage());
                    seedsvalid = false;
                }
            }
            if (!seedsvalid) {
                System.exit(2);
            }
            String seeds = Joiner.on(',').join(seedsList);

            while (true) {
                try {
                    uStoreClient.put(SyncStoreCCProvider.AUTH_SCHEME, 
                                          bSettings.authScheme.toString());
                    uStoreClient.put(SyncStoreCCProvider.KEY_STORE_PATH, 
                                          bSettings.keyStorePath);
                    uStoreClient.put(SyncStoreCCProvider.KEY_STORE_PASSWORD, 
                                          bSettings.keyStorePassword);
                    uStoreClient.put(SyncStoreCCProvider.SEEDS, seeds);
                    break;
                } catch (ObsoleteVersionException e) {}
            }
        }
        Short localNodeId = null;
        if (bSettings.reseed || bSettings.domainId != 0) {
            String localNodeIdStr = 
                    waitForValue(uStoreClient,
                                 SyncStoreCCProvider.LOCAL_NODE_ID, 
                                 10000000000L);
            if (localNodeIdStr == null) {
                err.println("Error: Local node ID is not set; you must " + 
                            "first seed the cluster by using the --seeds " + 
                            "option");
                System.exit(3);
            }
            localNodeId = Short.valueOf(localNodeIdStr);
        }
        if (bSettings.reseed) {
            while (true) {
                try {
                    nodeStoreClient.delete(localNodeId);
                    break;
                } catch (ObsoleteVersionException e) { };
            }
        }
        if (bSettings.domainId != 0) {
            while (true) {
                try {
                    Versioned<Node> localNode = 
                            nodeStoreClient.get(localNodeId);
                    if (localNode.getValue() == null) {
                        err.println("Could not set domain ID for local node " + 
                                    "because local node not found in system " + 
                                    "node store");
                        System.exit(4);
                    }
                    
                    Node o = localNode.getValue();
                    localNode.setValue(new Node(o.getHostname(),
                                                o.getPort(),
                                                o.getNodeId(),
                                                bSettings.domainId));
                    nodeStoreClient.put(localNodeId, localNode);
                    break;
                } catch (ObsoleteVersionException e) { };
            }

        }
        if (bSettings.deleteNode != 0) {
            while (true) {
                try {
                    nodeStoreClient.delete(bSettings.deleteNode);
                    break;
                } catch (ObsoleteVersionException e) {}
            }
        }
    }

    private String waitForValue(IStoreClient<String, String> uStoreClient,
                               String key,
                               long maxWait) throws Exception {
        long start = System.nanoTime();
        while (start + maxWait > System.nanoTime()) {
            String v = uStoreClient.getValue(key);
            if (v != null) {
                return v;
            }
            Thread.sleep(100);
        }
        return null;
    }
    
    // ****
    // Main 
    // ****

    public static void main(String[] args) throws Exception {
        BootstrapToolSettings settings = new BootstrapToolSettings();
        settings.init(args);
        BootstrapTool client = new BootstrapTool(settings);
        client.connect();
        try {
            client.bootstrap();
        } finally {
            client.cleanup();
        }
    }

}
