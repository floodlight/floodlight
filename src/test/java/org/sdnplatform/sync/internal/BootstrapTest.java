package org.sdnplatform.sync.internal;

import java.io.File;
import java.util.ArrayList;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.MockDebugEventService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.config.Node;
import org.sdnplatform.sync.internal.config.SyncStoreCCProvider;
import org.sdnplatform.sync.internal.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;

import static org.junit.Assert.*;
import static org.sdnplatform.sync.internal.SyncManagerTest.waitForValue;
import static org.sdnplatform.sync.internal.SyncManagerTest.waitForFullMesh;

public class BootstrapTest {
    protected static Logger logger =
            LoggerFactory.getLogger(BootstrapTest.class);
    
    @Rule
    public TemporaryFolder dbFolder = new TemporaryFolder();
    
    @Test
    public void testBootstrap() throws Exception {
        ArrayList<SyncManager> syncManagers = new ArrayList<SyncManager>();
        ArrayList<IStoreClient<Short,Node>> nodeStores = 
                new ArrayList<IStoreClient<Short,Node>>();
        ArrayList<IStoreClient<String,String>> unsyncStores = 
                new ArrayList<IStoreClient<String,String>>();
        ArrayList<Short> nodeIds = new ArrayList<Short>();
        ArrayList<Node> nodes = new ArrayList<Node>();
        
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        ThreadPool tp = new ThreadPool();

        int curPort = 6699;
        
        String keyStorePath = new File(dbFolder.getRoot(), 
                                       "keystore.jceks").getAbsolutePath();
        String keyStorePassword = "bootstrapping is fun!";
        CryptoUtil.writeSharedSecret(keyStorePath, 
                                     keyStorePassword, 
                                     CryptoUtil.secureRandom(16));
        
        // autobootstrap a cluster of 4 nodes
        for (int i = 0; i < 4; i++) {
            SyncManager syncManager = new SyncManager();
            syncManagers.add(syncManager);

            fmc.addService(IThreadPoolService.class, tp);
            fmc.addService(IDebugCounterService.class, new MockDebugCounterService());
            fmc.addService(IDebugEventService.class, new MockDebugEventService());
            String dbPath = 
                    new File(dbFolder.getRoot(), 
                             "server" + i).getAbsolutePath();
            fmc.addConfigParam(syncManager, "dbPath", dbPath);

            tp.init(fmc);
            syncManager.init(fmc);
            tp.startUp(fmc);
            syncManager.startUp(fmc);
            syncManager.registerStore("localTestStore", Scope.LOCAL);
            syncManager.registerStore("globalTestStore", Scope.GLOBAL);
            
            IStoreClient<String, String> unsyncStore = 
                    syncManager.getStoreClient(SyncStoreCCProvider.
                                               SYSTEM_UNSYNC_STORE, 
                                               String.class, String.class);
            IStoreClient<Short, Node> nodeStore = 
                    syncManager.getStoreClient(SyncStoreCCProvider.
                                               SYSTEM_NODE_STORE, 
                                                Short.class, Node.class);
            unsyncStores.add(unsyncStore);
            nodeStores.add(nodeStore);
            
            // Note that it will end up going through a transitional state
            // where it will listen on 6642 because it will use the fallback
            // config
            unsyncStore.put("localNodePort", String.valueOf(curPort));
            unsyncStore.put(SyncStoreCCProvider.KEY_STORE_PATH, keyStorePath);
            unsyncStore.put(SyncStoreCCProvider.KEY_STORE_PASSWORD, 
                            keyStorePassword);
            unsyncStore.put(SyncStoreCCProvider.AUTH_SCHEME, 
                            AuthScheme.CHALLENGE_RESPONSE.toString());

            String curSeed = "";
            if (i > 0) {
                curSeed = HostAndPort.fromParts(nodes.get(i-1).getHostname(), 
                                                nodes.get(i-1).getPort()).
                                                toString();
            }
            // The only thing really needed for bootstrapping is to put
            // a value for "seeds" into the unsynchronized store.
            unsyncStore.put("seeds", curSeed);

            waitForValue(unsyncStore, "localNodeId", null, 
                         3000, "unsyncStore" + i);
            short nodeId = 
                    Short.parseShort(unsyncStore.getValue("localNodeId"));
            Node node = nodeStore.getValue(nodeId);
            nodeIds.add(nodeId);
            nodes.add(node);

            while (syncManager.getClusterConfig().
                    getNode().getNodeId() != nodeId) {
                Thread.sleep(100);
            }
            while (syncManager.getClusterConfig().
                    getNode().getPort() != curPort) {
                Thread.sleep(100);
            }
            for (int j = 0; j <= i; j++) {
                for (int k = 0; k <= i; k++) {
                    waitForValue(nodeStores.get(j), nodeIds.get(k), 
                                 nodes.get(k), 3000, "nodeStore" + j);
                }
            }
            curPort -= 1;
        }
        for (SyncManager syncManager : syncManagers) {
            assertEquals(syncManagers.size(), 
                         syncManager.getClusterConfig().getNodes().size());
        }        
        
        SyncManager[] syncManagerArr = 
                syncManagers.toArray(new SyncManager[syncManagers.size()]);
        waitForFullMesh(syncManagerArr, 5000);
        
        logger.info("Cluster successfully built.  Attempting reseed");
        
        // Test reseeding
        nodeStores.get(0).delete(nodeIds.get(0));
        
        for (int j = 0; j < nodeIds.size(); j++) {
            for (int k = 0; k < nodeIds.size(); k++) {
                waitForValue(nodeStores.get(j), nodeIds.get(k), 
                             nodes.get(k), 3000, "nodeStore" + j);
            }
        }
    }
}
