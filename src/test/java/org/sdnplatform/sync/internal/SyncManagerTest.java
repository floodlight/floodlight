package org.sdnplatform.sync.internal;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.NullDebugCounter;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IInconsistencyResolver;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.IStoreListener.UpdateType;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.internal.AbstractSyncManager;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.SyncTorture;
import org.sdnplatform.sync.internal.config.Node;
import org.sdnplatform.sync.internal.config.PropertyCCProvider;
import org.sdnplatform.sync.internal.store.Key;
import org.sdnplatform.sync.internal.store.TBean;
import org.sdnplatform.sync.internal.util.CryptoUtil;
import org.sdnplatform.sync.internal.version.VectorClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SyncManagerTest {
    protected static Logger logger =
            LoggerFactory.getLogger(SyncManagerTest.class);
    
    protected FloodlightModuleContext[] moduleContexts;
    protected SyncManager[] syncManagers;
    protected final static ObjectMapper mapper = new ObjectMapper();
    protected String nodeString;
    ArrayList<Node> nodes;

    ThreadPool tp;

    @Rule
    public TemporaryFolder keyStoreFolder = new TemporaryFolder();

    protected File keyStoreFile;
    protected String keyStorePassword = "verysecurepassword";
    
    protected void setupSyncManager(FloodlightModuleContext fmc,
                                    SyncManager syncManager, Node thisNode)
            throws Exception {        
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IDebugCounterService.class, new NullDebugCounter());
        fmc.addConfigParam(syncManager, "configProviders", 
                           PropertyCCProvider.class.getName());
        fmc.addConfigParam(syncManager, "nodes", nodeString);
        fmc.addConfigParam(syncManager, "thisNode", ""+thisNode.getNodeId());
        fmc.addConfigParam(syncManager, "persistenceEnabled", "false");
        fmc.addConfigParam(syncManager, "authScheme", "CHALLENGE_RESPONSE");
        fmc.addConfigParam(syncManager, "keyStorePath", 
                           keyStoreFile.getAbsolutePath());
        fmc.addConfigParam(syncManager, "keyStorePassword", keyStorePassword);
        tp.init(fmc);
        syncManager.init(fmc);

        tp.startUp(fmc);
        syncManager.startUp(fmc);

        syncManager.registerStore("global", Scope.GLOBAL);
        syncManager.registerStore("local", Scope.LOCAL);
    }
    
    @Before
    public void setUp() throws Exception {
        keyStoreFile = new File(keyStoreFolder.getRoot(), 
                "keystore.jceks");
        CryptoUtil.writeSharedSecret(keyStoreFile.getAbsolutePath(), 
                                     keyStorePassword, 
                                     CryptoUtil.secureRandom(16));

        tp = new ThreadPool();
        
        syncManagers = new SyncManager[4];
        moduleContexts = new FloodlightModuleContext[4];

        nodes = new ArrayList<Node>();
        nodes.add(new Node("localhost", 40101, (short)1, (short)1));
        nodes.add(new Node("localhost", 40102, (short)2, (short)2));
        nodes.add(new Node("localhost", 40103, (short)3, (short)1));
        nodes.add(new Node("localhost", 40104, (short)4, (short)2));
        nodeString = mapper.writeValueAsString(nodes);

        for(int i = 0; i < 4; i++) {
            moduleContexts[i] = new FloodlightModuleContext();
            syncManagers[i] = new SyncManager();
            setupSyncManager(moduleContexts[i], syncManagers[i], nodes.get(i));
        }
    }

    @After
    public void tearDown() {
        tp.getScheduledExecutor().shutdownNow();
        tp = null;

        if (syncManagers != null) {
            for(int i = 0; i < syncManagers.length; i++) {
                if (null != syncManagers[i])
                    syncManagers[i].shutdown();
            }
        }
        syncManagers = null;
    }

    @Test
    public void testBasicOneNode() throws Exception {
        AbstractSyncManager sync = syncManagers[0];
        IStoreClient<Key, TBean> testClient =
                sync.getStoreClient("global", Key.class, TBean.class);
        Key k = new Key("com.bigswitch.bigsync.internal", "test");
        TBean tb = new TBean("hello", 42);
        TBean tb2 = new TBean("hello", 84);
        TBean tb3 = new TBean("hello", 126);
        
        assertNotNull(testClient.get(k));
        assertNull(testClient.get(k).getValue());
        
        testClient.put(k, tb);
        Versioned<TBean> result = testClient.get(k);
        assertEquals(result.getValue(), tb);

        result.setValue(tb2);
        testClient.put(k, result);

        try {
            result.setValue(tb3);
            testClient.put(k, result);
            fail("Should get ObsoleteVersionException");
        } catch (ObsoleteVersionException e) {
            // happy town
        }

        result = testClient.get(k);
        assertEquals(tb2, result.getValue());
        
    }

    @Test
    public void testIterator() throws Exception {
        AbstractSyncManager sync = syncManagers[0];
        IStoreClient<Key, TBean> testClient =
                sync.getStoreClient("local", Key.class, TBean.class);
        
        HashMap<Key, TBean> testMap = new HashMap<Key, TBean>();
        for (int i = 0; i < 100; i++) {
            Key k = new Key("com.bigswitch.bigsync.internal", "test" + i);
            TBean tb = new TBean("value", i);
            testMap.put(k, tb);
            testClient.put(k, tb);
        }
        
        IClosableIterator<Entry<Key, Versioned<TBean>>> iter = 
                testClient.entries();
        int size = 0;
        try {
            while (iter.hasNext()) {
                Entry<Key, Versioned<TBean>> e = iter.next();
                assertEquals(testMap.get(e.getKey()), e.getValue().getValue());
                size += 1;
            }
        } finally {
            iter.close();
        }
        assertEquals(testMap.size(), size);
    }

    protected static <K, V> Versioned<V> waitForValue(IStoreClient<K, V> client,
                                             K key, V value,
                                             int maxTime,
                                             String clientName) 
                                                     throws Exception {
        Versioned<V> v = null;
        long then = System.currentTimeMillis();
        while (true) {
            v = client.get(key);
            if (value != null) {
                if (v.getValue() != null && v.getValue().equals(value)) break;
            } else {
                if (v.getValue() != null) break;
            }
            if (v.getValue() != null)
                logger.info("{}: Value for key {} not yet right: " +
                            "expected: {}; actual: {}",
                            new Object[]{clientName, key, value, v.getValue()});
            else 
                logger.info("{}: Value for key {} is null: expected {}", 
                            new Object[]{clientName, key, value});
                

            Thread.sleep(100);
            assertTrue(then + maxTime > System.currentTimeMillis());
        }
        return v;
    }

    private void waitForFullMesh(int maxTime) throws Exception {
        waitForFullMesh(syncManagers, maxTime);
    }

    protected static void waitForFullMesh(SyncManager[] syncManagers,
                                          int maxTime) throws Exception {
        long then = System.currentTimeMillis();

        while (true) {
            boolean full = true;
            for(int i = 0; i < syncManagers.length; i++) {
                if (!syncManagers[i].rpcService.isFullyConnected())
                    full = false;
            }
            if (full) break;
            Thread.sleep(100);
            assertTrue(then + maxTime > System.currentTimeMillis());
        }
    }
    private void waitForConnection(SyncManager sm,
                                   short nodeId,
                                   boolean connected,
                                   int maxTime) throws Exception {
        long then = System.currentTimeMillis();

        while (true) {
            if (connected == sm.rpcService.isConnected(nodeId)) break;
            Thread.sleep(100);
            assertTrue(then + maxTime > System.currentTimeMillis());
        }
    }

    @Test
    public void testBasicGlobalSync() throws Exception {
        waitForFullMesh(2000);

        ArrayList<IStoreClient<String, String>> clients =
                new ArrayList<IStoreClient<String, String>>(syncManagers.length);
        // write one value to each node's local interface
        for (int i = 0; i < syncManagers.length; i++) {
            IStoreClient<String, String> client =
                    syncManagers[i].getStoreClient("global", 
                                                   String.class, String.class);
            clients.add(client);
            client.put("key" + i, ""+i);
        }

        // verify that we see all the values everywhere
        for (int j = 0; j < clients.size(); j++) {
            for (int i = 0; i < syncManagers.length; i++) {
                waitForValue(clients.get(j), "key" + i, ""+i, 2000, "client"+j);
            }
        }
    }

    @Test
    public void testBasicLocalSync() throws Exception {
        waitForFullMesh(2000);

        ArrayList<IStoreClient<String, String>> clients =
                new ArrayList<IStoreClient<String, String>>(syncManagers.length);
        // write one value to each node's local interface
        for (int i = 0; i < syncManagers.length; i++) {
            IStoreClient<String, String> client =
                    syncManagers[i].getStoreClient("local", 
                                                   String.class, String.class);
            clients.add(client);
            client.put("key" + i, ""+i);
        }

        // verify that we see all the values from each local group at all the
        // nodes of that local group
        for (int j = 0; j < clients.size(); j++) {
            IStoreClient<String, String> client = clients.get(j);
            for (int i = 0; i < syncManagers.length; i++) {
                if (i % 2 == j % 2)
                    waitForValue(client, "key" + i, ""+i, 2000, "client"+j);
                else {
                    Versioned<String> v = client.get("key" + i);
                    if (v.getValue() != null) {
                        fail("Node " + j + " reading key" + i + 
                             ": " + v.getValue());
                    }
                }
            }
        }
    }
    
    @Test
    public void testConcurrentWrite() throws Exception {
        waitForFullMesh(2000);
        
        // Here we generate concurrent writes and then resolve them using
        // a custom inconsistency resolver
        IInconsistencyResolver<Versioned<List<String>>> ir = 
                new IInconsistencyResolver<Versioned<List<String>>>() {
            @Override
            public List<Versioned<List<String>>>
                    resolveConflicts(List<Versioned<List<String>>> items) {
                VectorClock vc = null;
                List<String> strings = new ArrayList<String>();
                for (Versioned<List<String>> item : items) {
                    if (vc == null) 
                        vc = (VectorClock)item.getVersion();
                    else
                        vc = vc.merge((VectorClock)item.getVersion());
                    
                    strings.addAll(item.getValue());
                }
                Versioned<List<String>> v = 
                        new Versioned<List<String>>(strings, vc);
                return Collections.singletonList(v);
            }
        };
        
        TypeReference<List<String>> tr = new TypeReference<List<String>>() {};
        TypeReference<String> ktr = new TypeReference<String>() {};
        IStoreClient<String, List<String>> client0 =
                syncManagers[0].getStoreClient("local", ktr, tr, ir);
        IStoreClient<String, List<String>> client2 =
                syncManagers[2].getStoreClient("local", ktr, tr, ir);
        
        client0.put("key", Collections.singletonList("value"));
        Versioned<List<String>> v = client0.get("key");
        assertNotNull(v);
        
        // now we generate two writes that are concurrent to each other
        // but are both locally after the first write.  The result should be
        // two non-obsolete lists each containing a single element.
        // The inconsistency resolver above will resolve these by merging
        // the lists
        List<String> comp = new ArrayList<String>();
        v.setValue(Collections.singletonList("newvalue0"));
        comp.add("newvalue0");
        client0.put("key", v);
        v.setValue(Collections.singletonList("newvalue1"));
        comp.add("newvalue1");
        client2.put("key", v);
        
        v = waitForValue(client0, "key", comp, 1000, "client0");

        // add one more value to the array.  Now there will be exactly one
        // non-obsolete value
        List<String> newlist = new ArrayList<String>(v.getValue());
        assertEquals(2, newlist.size());
        newlist.add("finalvalue");
        v.setValue(newlist);
        client0.put("key", v);
        
        v = waitForValue(client2, "key", newlist, 2000, "client2");
        assertEquals(3, newlist.size());
      
    }

    @Test
    public void testReconnect() throws Exception {
        IStoreClient<String, String> client0 =
                syncManagers[0].getStoreClient("global", 
                                               String.class, 
                                               String.class);
        IStoreClient<String, String> client1 =
                syncManagers[1].getStoreClient("global", 
                                               String.class, String.class);
        IStoreClient<String, String> client2 =
                syncManagers[2].getStoreClient("global", 
                                               String.class, String.class);

        client0.put("key0", "value0");
        waitForValue(client2, "key0", "value0", 1000, "client0");

        logger.info("Shutting down server ID 1");
        syncManagers[0].shutdown();
        
        client1.put("newkey1", "newvalue1");
        client2.put("newkey2", "newvalue2");
        client1.put("key0", "newvalue0");
        client2.put("key2", "newvalue2");
        
        for (int i = 0; i < 500; i++) {
            client2.put("largetest" + i, "largetestvalue");
        }
        
        logger.info("Initializing server ID 1");
        syncManagers[0] = new SyncManager();
        setupSyncManager(moduleContexts[0], syncManagers[0], nodes.get(0));

        waitForFullMesh(2000);

        client0 = syncManagers[0].getStoreClient("global", 
                                                 String.class, String.class);
        waitForValue(client0, "newkey1", "newvalue1", 1000, "client0");
        waitForValue(client0, "newkey2", "newvalue2", 1000, "client0");
        waitForValue(client0, "key0", "newvalue0", 1000, "client0");
        waitForValue(client0, "key2", "newvalue2", 1000, "client0");

        for (int i = 0; i < 500; i++) {
            waitForValue(client0, "largetest" + i, 
                         "largetestvalue", 1000, "client0");
        }
    }
    
    protected class Update {
        String key;
        UpdateType type;

        public Update(String key, UpdateType type) {
            super();
            this.key = key;
            this.type = type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Update other = (Update) obj;
            if (!getOuterType().equals(other.getOuterType())) return false;
            if (key == null) {
                if (other.key != null) return false;
            } else if (!key.equals(other.key)) return false;
            if (type != other.type) return false;
            return true;
        }

        private SyncManagerTest getOuterType() {
            return SyncManagerTest.this;
        }
    }
    
    protected class TestListener implements IStoreListener<String> {
        HashSet<Update> notified = new HashSet<Update>();
        
        @Override
        public void keysModified(Iterator<String> keys, 
                                 UpdateType type) {
            while (keys.hasNext())
                notified.add(new Update(keys.next(), type));
        }
        
    }
    
    @SuppressWarnings("rawtypes")
    private void waitForNotify(TestListener tl, 
                               HashSet comp,
                               int maxTime) throws Exception {
        long then = System.currentTimeMillis();

        while (true) {
            if (tl.notified.containsAll(comp)) break;
            Thread.sleep(100);
            assertTrue(then + maxTime > System.currentTimeMillis());
        }
    }

    @Test
    public void testNotify() throws Exception {
        IStoreClient<String, String> client0 =
                syncManagers[0].getStoreClient("local", 
                                               String.class, String.class);
        IStoreClient<String, String> client2 =
                syncManagers[2].getStoreClient("local", 
                                               new TypeReference<String>() {}, 
                                               new TypeReference<String>() {});
        
        TestListener t0 = new TestListener();
        TestListener t2 = new TestListener();
        client0.addStoreListener(t0);
        client2.addStoreListener(t2);
        
        client0.put("test0", "value");
        client2.put("test2", "value");

        HashSet<Update> c0 = new HashSet<Update>();
        c0.add(new Update("test0", UpdateType.LOCAL));
        c0.add(new Update("test2", UpdateType.REMOTE));
        HashSet<Update> c2 = new HashSet<Update>();
        c2.add(new Update("test0", UpdateType.REMOTE));
        c2.add(new Update("test2", UpdateType.LOCAL));
        
        waitForNotify(t0, c0, 2000);
        waitForNotify(t2, c2, 2000);
        assertEquals(2, t0.notified.size());
        assertEquals(2, t2.notified.size());
        
        t0.notified.clear();
        t2.notified.clear();
        
        Versioned<String> v0 = client0.get("test0");
        v0.setValue("newvalue");
        client0.put("test0", v0);

        Versioned<String> v2 = client0.get("test2");
        v2.setValue("newvalue");
        client2.put("test2", v2);

        waitForNotify(t0, c0, 2000);
        waitForNotify(t2, c2, 2000);
        assertEquals(2, t0.notified.size());
        assertEquals(2, t2.notified.size());

        t0.notified.clear();
        t2.notified.clear();
        
        client0.delete("test0");
        client2.delete("test2");

        waitForNotify(t0, c0, 2000);
        waitForNotify(t2, c2, 2000);
        assertEquals(2, t0.notified.size());
        assertEquals(2, t2.notified.size());
    }
    
    @Test
    public void testAddNode() throws Exception {
        waitForFullMesh(2000);
        IStoreClient<String, String> client0 =
                syncManagers[0].getStoreClient("global", 
                                               String.class, String.class);
        IStoreClient<String, String> client1 =
                syncManagers[1].getStoreClient("global", 
                                               String.class, String.class);
        client0.put("key", "value");
        waitForValue(client1, "key", "value", 2000, "client1");
        
        nodes.add(new Node("localhost", 40105, (short)5, (short)5));
        SyncManager[] sms = Arrays.copyOf(syncManagers, 
                                          syncManagers.length + 1);
        FloodlightModuleContext[] fmcs =
                Arrays.copyOf(moduleContexts,
                              moduleContexts.length + 1);
        sms[syncManagers.length] = new SyncManager();
        fmcs[moduleContexts.length] = new FloodlightModuleContext();
        nodeString = mapper.writeValueAsString(nodes);

        setupSyncManager(fmcs[moduleContexts.length],
                         sms[syncManagers.length],
                         nodes.get(syncManagers.length));
        syncManagers = sms;
        moduleContexts = fmcs;

        for(int i = 0; i < 4; i++) {
            moduleContexts[i].addConfigParam(syncManagers[i],
                                             "nodes", nodeString);
            syncManagers[i].doUpdateConfiguration();
        }
        waitForFullMesh(2000);

        IStoreClient<String, String> client4 =
                syncManagers[4].getStoreClient("global", 
                                               String.class, String.class);
        client4.put("newkey", "newvalue");
        waitForValue(client4, "key", "value", 2000, "client4");
        waitForValue(client0, "newkey", "newvalue", 2000, "client0");
    }
    
    @Test
    public void testRemoveNode() throws Exception {
        waitForFullMesh(2000);
        IStoreClient<String, String> client0 =
                syncManagers[0].getStoreClient("global", 
                                               String.class, String.class);
        IStoreClient<String, String> client1 =
                syncManagers[1].getStoreClient("global", 
                                               String.class, String.class);
        IStoreClient<String, String> client2 =
                syncManagers[2].getStoreClient("global", 
                                               String.class, String.class);

        client0.put("key", "value");
        waitForValue(client1, "key", "value", 2000, "client1");
        
        nodes.remove(0);
        nodeString = mapper.writeValueAsString(nodes);

        SyncManager oldNode = syncManagers[0];
        syncManagers = Arrays.copyOfRange(syncManagers, 1, 4);
        moduleContexts = Arrays.copyOfRange(moduleContexts, 1, 4);

        try {
            for(int i = 0; i < syncManagers.length; i++) {
                moduleContexts[i].addConfigParam(syncManagers[i],
                                                 "nodes", nodeString);
                syncManagers[i].doUpdateConfiguration();
                waitForConnection(syncManagers[i], (short)1, false, 2000);
            }
        } finally {
            oldNode.shutdown();
        }
        waitForFullMesh(2000);

        client1.put("newkey", "newvalue");
        waitForValue(client2, "key", "value", 2000, "client4");
        waitForValue(client2, "newkey", "newvalue", 2000, "client0");
    }

    @Test
    public void testChangeNode() throws Exception {
        waitForFullMesh(2000);
        IStoreClient<String, String> client0 =
                syncManagers[0].getStoreClient("global", 
                                               String.class, String.class);
        IStoreClient<String, String> client2 =
                syncManagers[2].getStoreClient("global", 
                                               String.class, String.class);
        client0.put("key", "value");
        waitForValue(client2, "key", "value", 2000, "client2");

        nodes.set(2, new Node("localhost", 50103, (short)3, (short)1));
        nodeString = mapper.writeValueAsString(nodes);

        for(int i = 0; i < syncManagers.length; i++) {
            moduleContexts[i].addConfigParam(syncManagers[i],
                                             "nodes", nodeString);
            syncManagers[i].doUpdateConfiguration();
        }
        waitForFullMesh(2000);
        
        waitForValue(client2, "key", "value", 2000, "client2");
        client2 = syncManagers[2].getStoreClient("global", 
                                                 String.class, String.class);
        client0.put("key", "newvalue");
        waitForValue(client2, "key", "newvalue", 2000, "client2");
    }

    /**
     * Do a brain-dead performance test with one thread writing and waiting
     * for the values on the other node.  The result get printed to the log
     */
    public void testSimpleWritePerformance(String store) throws Exception {
        waitForFullMesh(5000);
        
        final int count = 1000000;

        IStoreClient<String, String> client0 =
                syncManagers[0].getStoreClient(store, 
                                               String.class, String.class);
        IStoreClient<String, String> client2 =
                syncManagers[2].getStoreClient(store, 
                                               String.class, String.class);
        
        long then = System.currentTimeMillis();
        
        for (int i = 1; i <= count; i++) {
            client0.put(""+i, ""+i);
        }

        long donewriting = System.currentTimeMillis();

        waitForValue(client2, ""+count, null, count, "client2");
        
        long now = System.currentTimeMillis();
        
        logger.info("Simple write ({}): {} values in {}+/-100 " +
                    "millis ({} synced writes/s) ({} local writes/s)",
                    new Object[]{store, count, (now-then), 
                                 1000.0*count/(now-then),
                                 1000.0*count/(donewriting-then)});

    }
    
    @Test
    @Ignore // ignored just to speed up routine tests
    public void testPerfSimpleWriteLocal() throws Exception {
        testSimpleWritePerformance("local");
    }

    @Test
    @Ignore // ignored just to speed up routine tests
    public void testPerfSimpleWriteGlobal() throws Exception {
        testSimpleWritePerformance("global");
    }

    @Test
    @Ignore
    public void testPerfOneNode() throws Exception {
        tearDown();
        tp = new ThreadPool();
        tp.init(null);
        tp.startUp(null);
        nodes = new ArrayList<Node>();
        nodes.add(new Node("localhost", 40101, (short)1, (short)1));
        nodeString = mapper.writeValueAsString(nodes);
        SyncManager sm = new SyncManager();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        setupSyncManager(fmc, sm, nodes.get(0));
        fmc.addService(ISyncService.class, sm);
        SyncTorture st = new SyncTorture();
        //fmc.addConfigParam(st, "iterations", "1");
        st.init(fmc);
        st.startUp(fmc);
        Thread.sleep(10000);
    }
}
