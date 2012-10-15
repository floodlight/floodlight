package net.floodlightcontroller.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounter;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.counter.SimpleCounter;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryEvType;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowReconcileManager 
        implements IFloodlightModule, IFlowReconcileService {

    /** The logger. */
    private static Logger logger =
                        LoggerFactory.getLogger(FlowReconcileManager.class);
    
    /** Reference to dependent modules */
    protected IThreadPoolService threadPool;
    protected ICounterStoreService counterStore;

    /**
     * The list of flow reconcile listeners that have registered to get
     * flow reconcile callbacks. Such callbacks are invoked, for example, when
     * a switch with existing flow-mods joins this controller and those flows
     * need to be reconciled with the current configuration of the controller.
     */
    protected ListenerDispatcher<OFType, IFlowReconcileListener>
                                               flowReconcileListeners;

    /** A FIFO queue to keep all outstanding flows for reconciliation */
    Queue<OFMatchReconcile> flowQueue;
    
    /** Asynchronous task to feed the flowReconcile pipeline */
    protected SingletonTask flowReconcileTask;
    
    String controllerPktInCounterName;
    protected SimpleCounter lastPacketInCounter;
    
    protected static int MAX_SYSTEM_LOAD_PER_SECOND = 50000;
    /** a minimum flow reconcile rate so that it won't stave */
    protected static int MIN_FLOW_RECONCILE_PER_SECOND = 1000;
    
    /** once per second */
    protected static int FLOW_RECONCILE_DELAY_MILLISEC = 10;
    protected Date lastReconcileTime;
    
    /** Config to enable or disable flowReconcile */
    protected static final String EnableConfigKey = "enable";
    protected boolean flowReconcileEnabled;
    
    public int flowReconcileThreadRunCount;
    
    @Override
    public synchronized void addFlowReconcileListener(
                IFlowReconcileListener listener) {
        flowReconcileListeners.addListener(OFType.FLOW_MOD, listener);

        if (logger.isTraceEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("FlowMod listeners: ");
            for (IFlowReconcileListener l :
                flowReconcileListeners.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            logger.trace(sb.toString());
        }
    }

    @Override
    public synchronized void removeFlowReconcileListener(
                IFlowReconcileListener listener) {
        flowReconcileListeners.removeListener(listener);
    }
    
    @Override
    public synchronized void clearFlowReconcileListeners() {
        flowReconcileListeners.clearListeners();
    }
    
    /**
     * Add to-be-reconciled flow to the queue.
     *
     * @param ofmRcIn the ofm rc in
     */
    public void reconcileFlow(OFMatchReconcile ofmRcIn) {
        if (ofmRcIn == null) return;
        
        // Make a copy before putting on the queue.
        OFMatchReconcile myOfmRc = new OFMatchReconcile(ofmRcIn);
    
        flowQueue.add(myOfmRc);
    
        Date currTime = new Date();
        long delay = 0;

        /** schedule reconcile task immidiately if it has been more than 1 sec
         *  since the last run. Otherwise, schedule the reconcile task in
         *  DELAY_MILLISEC.
         */
        if (currTime.after(new Date(lastReconcileTime.getTime() + 1000))) {
            delay = 0;
        } else {
            delay = FLOW_RECONCILE_DELAY_MILLISEC;
        }
        flowReconcileTask.reschedule(delay, TimeUnit.MILLISECONDS);
    
        if (logger.isTraceEnabled()) {
            logger.trace("Reconciling flow: {}, total: {}",
                myOfmRc.toString(), flowQueue.size());
        }
    }
    
    @Override
    public void updateFlowForDestinationDevice(IDevice device,
                                            IFlowQueryHandler handler,
                                            FCQueryEvType fcEvType) {
        // NO-OP
    }

    @Override
    public void updateFlowForSourceDevice(IDevice device,
                                          IFlowQueryHandler handler,
                                          FCQueryEvType fcEvType) {
        // NO-OP
    }
    
    @Override
    public void flowQueryGenericHandler(FlowCacheQueryResp flowResp) {
        if (flowResp.queryObj.evType != FCQueryEvType.GET) {
            OFMatchReconcile ofmRc = new OFMatchReconcile();;
            /* Re-provision these flows */
            for (QRFlowCacheObj entry : flowResp.qrFlowCacheObjList) {
                /* reconcile the flows in entry */
                entry.toOFMatchReconcile(ofmRc,
                        flowResp.queryObj.applInstName,
                        OFMatchReconcile.ReconcileAction.UPDATE_PATH);
                reconcileFlow(ofmRc);
            }
        }
        return;
    }
    
    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
            new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFlowReconcileService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> 
                                                            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        m.put(IFlowReconcileService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> 
                                                    getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IThreadPoolService.class);
        l.add(ICounterStoreService.class);
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        counterStore = context.getServiceImpl(ICounterStoreService.class);
    
        flowQueue = new ConcurrentLinkedQueue<OFMatchReconcile>();
        flowReconcileListeners = 
                new ListenerDispatcher<OFType, IFlowReconcileListener>();
        
        Map<String, String> configParam = context.getConfigParams(this);
        String enableValue = configParam.get(EnableConfigKey);
        // Set flowReconcile default to true
        flowReconcileEnabled = true;
        if (enableValue != null &&
            enableValue.equalsIgnoreCase("false")) {
            flowReconcileEnabled = false;
        }
        
        flowReconcileThreadRunCount = 0;
        lastReconcileTime = new Date(0);
        logger.debug("FlowReconcile is {}", flowReconcileEnabled);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // thread to do flow reconcile
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        flowReconcileTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                try {
                    if (doReconcile()) {
                        flowReconcileTask.reschedule(
                            FLOW_RECONCILE_DELAY_MILLISEC,
                            TimeUnit.MILLISECONDS);
                    }
                } catch (Exception e) {
                    logger.warn("Exception in doReconcile(): {}",
                                e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        
        String packetInName = OFType.PACKET_IN.toClass().getName();
        packetInName = packetInName.substring(packetInName.lastIndexOf('.')+1); 
        
        // Construct controller counter for the packet_in
        controllerPktInCounterName =
            CounterStore.createCounterName(ICounterStoreService.CONTROLLER_NAME, 
                                           -1,
                                           packetInName);
    }
    
    /**
     * Feed the flows into the flow reconciliation pipeline.
     * @return true if more flows to be reconciled
     *         false if no more flows to be reconciled.
     */
    protected boolean doReconcile() {
        if (!flowReconcileEnabled) {
            return false;
        }
    
        // Record the execution time.
        lastReconcileTime = new Date();
    
        ArrayList<OFMatchReconcile> ofmRcList =
                        new ArrayList<OFMatchReconcile>();
        
        // Get the maximum number of flows that can be reconciled.
        int reconcileCapacity = getCurrentCapacity();
        if (logger.isTraceEnabled()) {
            logger.trace("Reconcile capacity {} flows", reconcileCapacity);
        }
        while (!flowQueue.isEmpty() && reconcileCapacity > 0) {
            OFMatchReconcile ofmRc = flowQueue.poll();
            reconcileCapacity--;
            if (ofmRc != null) {
                ofmRcList.add(ofmRc);
                if (logger.isTraceEnabled()) {
                    logger.trace("Add flow {} to be the reconcileList", ofmRc.cookie);
                }
            } else {
                break;
            }
        }
        
        // Run the flow through all the flow reconcile listeners
        IFlowReconcileListener.Command retCmd;
        if (ofmRcList.size() > 0) {
            List<IFlowReconcileListener> listeners =
                flowReconcileListeners.getOrderedListeners();
            if (listeners == null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("No flowReconcile listener");
                }
                return false;
            }
        
            for (IFlowReconcileListener flowReconciler :
                flowReconcileListeners.getOrderedListeners()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Reconciling flow: call listener {}",
                            flowReconciler.getName());
                }
                retCmd = flowReconciler.reconcileFlows(ofmRcList);
                if (retCmd == IFlowReconcileListener.Command.STOP) {
                    break;
                }
            }
            flowReconcileThreadRunCount++;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("No flow to be reconciled.");
            }
        }
        
        // Return true if there are more flows to be reconciled
        if (flowQueue.isEmpty()) {
            return false;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("{} more flows to be reconciled.",
                            flowQueue.size());
            }
            return true;
        }
    }
    
    /**
     * Compute the maximum number of flows to be reconciled.
     * 
     * It computes the packetIn increment from the counter values in
     * the counter store;
     * Then computes the rate based on the elapsed time
     * from the last query;
     * Then compute the max flow reconcile rate by subtracting the packetIn
     * rate from the hard-coded max system rate.
     * If the system rate is reached or less than MIN_FLOW_RECONCILE_PER_SECOND,
     * set the maximum flow reconcile rate to the MIN_FLOW_RECONCILE_PER_SECOND
     * to prevent starvation.
     * Then convert the rate to an absolute number for the
     * FLOW_RECONCILE_PERIOD.
     * @return
     */
    protected int getCurrentCapacity() {
        ICounter pktInCounter =
            counterStore.getCounter(controllerPktInCounterName);
        int minFlows = MIN_FLOW_RECONCILE_PER_SECOND *
                        FLOW_RECONCILE_DELAY_MILLISEC / 1000;
        
        // If no packetInCounter, then there shouldn't be any flow.
        if (pktInCounter == null ||
            pktInCounter.getCounterDate() == null ||
            pktInCounter.getCounterValue() == null) {
            logger.debug("counter {} doesn't exist",
                        controllerPktInCounterName);
            return minFlows;
        }
        
        // Haven't get any counter yet.
        if (lastPacketInCounter == null) {
            logger.debug("First time get the count for {}",
                        controllerPktInCounterName);
            lastPacketInCounter = (SimpleCounter)
            SimpleCounter.createCounter(pktInCounter);
            return minFlows;
        }
        
        int pktInRate = getPktInRate(pktInCounter, new Date());
        
        // Update the last packetInCounter
        lastPacketInCounter = (SimpleCounter)
        SimpleCounter.createCounter(pktInCounter);
        int capacity = minFlows;
        if ((pktInRate + MIN_FLOW_RECONCILE_PER_SECOND) <=
                               MAX_SYSTEM_LOAD_PER_SECOND) {
            capacity = (MAX_SYSTEM_LOAD_PER_SECOND - pktInRate)
                    * FLOW_RECONCILE_DELAY_MILLISEC / 1000;
        }
        
        if (logger.isTraceEnabled()) {
            logger.trace("Capacity is {}", capacity);
        }
        return capacity;
    }
    
    protected int getPktInRate(ICounter newCnt, Date currentTime) {
        if (newCnt == null ||
            newCnt.getCounterDate() == null ||
            newCnt.getCounterValue() == null) {
            return 0;
        }
    
        // Somehow the system time is messed up. return max packetIn rate
        // to reduce the system load.
        if (newCnt.getCounterDate().before(
                lastPacketInCounter.getCounterDate())) {
            logger.debug("Time is going backward. new {}, old {}",
                    newCnt.getCounterDate(),
                    lastPacketInCounter.getCounterDate());
            return MAX_SYSTEM_LOAD_PER_SECOND;
        }
    
        long elapsedTimeInSecond = (currentTime.getTime() -
                    lastPacketInCounter.getCounterDate().getTime()) / 1000;
        if (elapsedTimeInSecond == 0) {
            // This should never happen. Check to avoid division by zero.
            return 0;
        }
    
        long diff = 0;
        switch (newCnt.getCounterValue().getType()) {
            case LONG:
                long newLong = newCnt.getCounterValue().getLong();
                long oldLong = lastPacketInCounter.getCounterValue().getLong();
                if (newLong < oldLong) {
                    // Roll over event
                    diff = Long.MAX_VALUE - oldLong + newLong;
                } else {
                    diff = newLong - oldLong;
                }
                break;
    
            case DOUBLE:
                double newDouble = newCnt.getCounterValue().getDouble();
                double oldDouble = lastPacketInCounter.getCounterValue().getDouble();
                if (newDouble < oldDouble) {
                    // Roll over event
                    diff = (long)(Double.MAX_VALUE - oldDouble + newDouble);
                } else {
                    diff = (long)(newDouble - oldDouble);
                }
                break;
        }
    
        return (int)(diff/elapsedTimeInSecond);
    }
}

