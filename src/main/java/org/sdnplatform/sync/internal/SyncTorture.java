package org.sdnplatform.sync.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

/**
 * A floodlight module that will start up and start doing horrible,
 * horrible things to the sync service.
 * @author readams
 */
public class SyncTorture implements IFloodlightModule {
    protected static final Logger logger =
            LoggerFactory.getLogger(SyncTorture.class);

    private static final String SYNC_STORE_NAME =
            SyncTorture.class.getCanonicalName() + ".torture";
    
    ISyncService syncService;
    IDebugCounterService debugCounter;

    int numWorkers = 2;
    int keysPerWorker = 1024*1024;
    int iterations = 0;
    int delay = 0;

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ISyncService.class);
        l.add(IDebugCounterService.class);

        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        syncService = context.getServiceImpl(ISyncService.class);
        debugCounter = context.getServiceImpl(IDebugCounterService.class);

        try {
            syncService.registerStore(SYNC_STORE_NAME, Scope.GLOBAL);
        } catch (SyncException e) {
            throw new FloodlightModuleException(e);
        }
        
        Map<String,String> config = context.getConfigParams(this);
        if (config.containsKey("numWorkers")) {
            numWorkers = Integer.parseInt(config.get("numWorkers"));
        }
        if (config.containsKey("keysPerWorker")) {
            keysPerWorker = Integer.parseInt(config.get("keysPerWorker"));
        }
        if (config.containsKey("iterations")) {
            iterations = Integer.parseInt(config.get("iterations"));
        }
        if (config.containsKey("delay")) {
            delay = Integer.parseInt(config.get("delay"));
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
        try {
            final IStoreClient<String, TortureValue> storeClient = 
                    syncService.getStoreClient(SYNC_STORE_NAME, 
                                               String.class, 
                                               TortureValue.class);
            for (int i = 0; i < numWorkers; i++) {
                Thread thread = new Thread(new TortureWorker(storeClient, i),
                                           "Torture-" + i);
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
            }
        } catch (Exception e) {
            throw new FloodlightModuleException(e);
        }
    }

    protected static class TortureValue {
        private String string;
        private int integer;
        private boolean bool;

        public TortureValue() {
            super();
        }

        public TortureValue(String string, int integer, boolean bool) {
            super();
            this.string = string;
            this.integer = integer;
            this.bool = bool;
        }
        
        public String getString() {
            return string;
        }
        public void setString(String string) {
            this.string = string;
        }
        public int getInteger() {
            return integer;
        }
        public void setInteger(int integer) {
            this.integer = integer;
        }
        public boolean isBool() {
            return bool;
        }
        public void setBool(boolean bool) {
            this.bool = bool;
        }
    }
    
    protected class TortureWorker implements Runnable {
        final IStoreClient<String, TortureValue> storeClient;
        final int workerId;
        final List<TortureValue> values;

        public TortureWorker(IStoreClient<String, TortureValue> storeClient,
                             int workerId) {
            super();
            this.storeClient = storeClient;
            this.workerId = workerId;
            values = new ArrayList<TortureValue>();
            for (int i = 0; i < keysPerWorker; i++) {
                values.add(new TortureValue(workerId+":"+i, 0, true));
            }
        }

        @Override
        public void run() {
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) { }
            }
            int i = 0;
            while (iterations == 0 || i++ < iterations) {
                long start = System.currentTimeMillis();
                try {
                    for (TortureValue v : values) {
                        Versioned<TortureValue> vv =
                                storeClient.get(v.getString());
                        v.setInteger(v.getInteger() + 1);
                        v.setBool(!v.isBool());
                        vv.setValue(v);
                        storeClient.put(v.getString(), vv);
                    }
                } catch (Exception e) {
                    logger.error("Error in worker: ", e);
                }
                long iterend = System.currentTimeMillis();
                debugCounter.flushCounters();
                logger.info("Completed iteration of {} values in {}ms" + 
                            " ({}/s)", 
                            new Object[]{values.size(), (iterend-start),
                            1000*values.size()/(iterend-start)});
            }
            
        }
    }
}
