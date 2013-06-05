package org.sdnplatform.sync.client;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.NullDebugCounter;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.client.SyncClient;
import org.sdnplatform.sync.client.SyncClient.SyncClientSettings;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.config.Node;
import org.sdnplatform.sync.internal.util.CryptoUtil;


public class ClientTest {
    protected SyncManager syncManager;
    protected final static ObjectMapper mapper = new ObjectMapper();
    protected String nodeString;
    ArrayList<Node> nodes;
    ThreadPool tp;

    @Rule
    public TemporaryFolder keyStoreFolder = new TemporaryFolder();

    protected File keyStoreFile;
    protected String keyStorePassword = "verysecurepassword";
    
    @Before
    public void setUp() throws Exception {
        keyStoreFile = new File(keyStoreFolder.getRoot(), 
                "keystore.jceks");
        CryptoUtil.writeSharedSecret(keyStoreFile.getAbsolutePath(), 
                                     keyStorePassword, 
                                     CryptoUtil.secureRandom(16));
        
        nodes = new ArrayList<Node>();
        nodes.add(new Node("localhost", 40101, (short)1, (short)1));
        nodeString = mapper.writeValueAsString(nodes);
        
        tp = new ThreadPool();
        syncManager = new SyncManager();
        
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IDebugCounterService.class, new NullDebugCounter());
        
        fmc.addConfigParam(syncManager, "nodes", nodeString);
        fmc.addConfigParam(syncManager, "thisNode", ""+1);
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
    }

    @After
    public void tearDown() {
        if (null != tp)
            tp.getScheduledExecutor().shutdownNow();
        tp = null;

        if (null != syncManager)
            syncManager.shutdown();
        syncManager = null;
    }

    @Test
    public void testClientBasic() throws Exception {
        SyncClientSettings scs = new SyncClientSettings();
        scs.hostname = "localhost";
        scs.port = 40101;
        scs.storeName = "global";
        scs.debug = true;
        scs.authScheme = AuthScheme.CHALLENGE_RESPONSE;
        scs.keyStorePath = keyStoreFile.getAbsolutePath();
        scs.keyStorePassword = keyStorePassword;
        SyncClient client = new SyncClient(scs);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        client.out = new PrintStream(out);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        client.err = new PrintStream(err);
        client.connect();
        client.executeCommandLine("get \"key\"");
        assertEquals("", err.toString());
        assertEquals("Using remote sync service at localhost:40101\n" +
                "Getting Key:\n" +
                "\"key\"\n\n" +
                "Not found\n",
                out.toString());
        
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        client.out = new PrintStream(out);
        client.err = new PrintStream(err);
        client.executeCommandLine("put \"key\" {\"field1\": \"value1\", \"field2\": \"value2\"}");
        assertEquals("", err.toString());
        assertEquals("Putting Key:\n" +
                "\"key\"\n\n" +
                "Value:\n" +
                "{\n" +
                "  \"field1\" : \"value1\",\n" +
                "  \"field2\" : \"value2\"\n" +
                "}\n" +
                "Success\n",
                out.toString());
        
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        client.out = new PrintStream(out);
        client.err = new PrintStream(err);
        client.executeCommandLine("get \"key\"");
        assertEquals("", err.toString());
        assertEquals("Getting Key:\n" +
                "\"key\"\n\n" +
                "Value:\n" +
                "{\n" +
                "  \"field1\" : \"value1\",\n" +
                "  \"field2\" : \"value2\"\n" +
                "}\n",
                out.toString());
        
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        client.out = new PrintStream(out);
        client.err = new PrintStream(err);
        client.executeCommandLine("delete \"key\"");
        assertEquals("", err.toString());
        assertEquals("Deleting Key:\n" +
                "\"key\"\n\n" +
                "Success\n",
                out.toString());
        
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        client.out = new PrintStream(out);
        client.err = new PrintStream(err);
        client.executeCommandLine("get \"key\"");
        assertEquals("", err.toString());
        assertEquals("Getting Key:\n" +
                "\"key\"\n\n" +
                "Not found\n",
                out.toString());
        
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        client.out = new PrintStream(out);
        client.err = new PrintStream(err);
        client.executeCommandLine("quit");
        assertEquals("", err.toString());
        assertEquals("",
                out.toString());
        
        client.executeCommandLine("help");
        assert(!"".equals(out.toString()));
    }
}
