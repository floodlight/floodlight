package org.sdnplatform.sync.client;

import java.io.File;
import java.io.PrintStream;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import org.kohsuke.args4j.Option;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.remote.RemoteSyncManager;

/**
 * Base class for implementing command-line tools that interact with the
 * sync store
 * @author readams
 */
public abstract class SyncClientBase {
    /**
     * Remote sync manager provides connection to remote store
     */
    protected RemoteSyncManager syncManager;

    /**
     * Command-line settings
     */
    protected SyncClientBaseSettings settings;

    /**
     * Stream to use for output
     */
    protected PrintStream out = System.out;

    /**
     * Stream to use for errors
     */
    protected PrintStream err = System.err;
    
    /**
     * Command-line settings for base sync client
     * @author readams
     */
    protected static class SyncClientBaseSettings 
        extends AuthTool.AuthToolSettings {
        @Option(name="--hostname", aliases="-n", 
                usage="Server hostname (default \"localhost\")")
        protected String hostname = "localhost";
    
        @Option(name="--port", aliases="-p", 
                usage="Server port (default 6642)")
        protected int port = 6642;
        
        @Override
        protected void init(String[] args) {
            super.init(args);
            
            if (!AuthScheme.NO_AUTH.equals(authScheme)) {
                if (!(new File(keyStorePath)).canRead()) {
                    System.err.println("Cannot read from key store " + 
                                       keyStorePath);
                    System.exit(1);
                }
            }
        }
    }
    
    public SyncClientBase(SyncClientBaseSettings settings) {
        this.settings = settings;
    }

    /**
     * Set up the remote sync manager and prepare for requests
     * @throws Exception
     */
    protected void connect() throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        ThreadPool tp = new ThreadPool();
        syncManager = new RemoteSyncManager();
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ISyncService.class, syncManager);
        fmc.addConfigParam(syncManager, "hostname", settings.hostname);
        fmc.addConfigParam(syncManager, "port", 
                           Integer.toString(settings.port));
        if (settings.authScheme != null) {
            fmc.addConfigParam(syncManager, "authScheme", 
                               settings.authScheme.toString());
            fmc.addConfigParam(syncManager, "keyStorePath", settings.keyStorePath);
            fmc.addConfigParam(syncManager, "keyStorePassword", 
                               settings.keyStorePassword);
        }
        tp.init(fmc);
        syncManager.init(fmc);
        tp.startUp(fmc);
        syncManager.startUp(fmc);
        
        out.println("Using remote sync service at " + 
                    settings.hostname + ":" + settings.port);
    }
    
    protected void cleanup() throws InterruptedException {
        syncManager.shutdown();
    }

}
