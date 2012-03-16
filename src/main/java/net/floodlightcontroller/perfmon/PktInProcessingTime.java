/**
 * Performance monitoring package
 */
package net.floodlightcontroller.perfmon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IPredicate;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.storage.OperatorPredicate.Operator;

import org.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author subrata
 *
 */
public class PktInProcessingTime
    implements IFloodlightModule, IPktInProcessingTimeService, IStorageSourceListener {

    /***
     * This class contains a set of buckets (called time buckets as the
     * primarily contain 'times' that are used in a circular way to 
     * store information on packet in processing time.
     * Each bucket is meant to store the various processing time 
     * related data for a fixed duration.
     * Buckets are reused to reduce garbage generation! Once the
     * last bucket is used up the LRU bucket is reused.
     * 
     * Naming convention for variable or constants
     * variable_s : value in seconds
     * variable_ms: value in milliseconds
     * variable_us: value in microseconds
     * variable_ns: value in nanoseconds
     * 
     * Key Constants:
     * ONE_BUCKET_DURATION_SECONDS_INT:  time duration of each bucket
     * BUCKET_SET_SIZE: Number of buckets
     * TOT_PROC_TIME_WARN_THRESHOLD_US: if processing time for a packet
     *    exceeds this threshold then a warning LOG message is generated
     * TOT_PROC_TIME_ALERT_THRESHOLD_US: same as above but an alert level
     *    syslog is generated instead
     * 
     */
    
    // Our dependencies
    private IStorageSourceService storageSource;
    private IRestApiService restApi;
    
    protected long ptWarningThresholdInNano;

    // DB storage tables
    protected static final String ControllerTableName = "controller_controller";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_PERF_MON = "performance_monitor_feature";
    
    protected static  Logger  logger = 
        LoggerFactory.getLogger(PktInProcessingTime.class);
    
    protected boolean isEnabled = false;
    protected boolean isInited = false;
    // Maintains the time when the last packet was processed
    protected long lastPktTime_ns;
    public CircularTimeBucketSet ctbs = null; // Set of all time buckets
    private int numBuckets = 360;

    
    /***
     * BUCKET_SET_SIZE buckets each holding 10s of processing time data, a total
     * of 30*10s = 5mins of processing time data is maintained
     */
    protected static final int ONE_BUCKET_DURATION_SECONDS = 10;// seconds
    protected static final long ONE_BUCKET_DURATION_NANOSECONDS  =
                                ONE_BUCKET_DURATION_SECONDS * 1000000000;
    
    @Override
    public void bootstrap(Set<IOFMessageListener> listeners) {
        ctbs = new CircularTimeBucketSet(listeners, numBuckets, ONE_BUCKET_DURATION_SECONDS);
        isInited = true;
    }
    
    @Override
    public boolean isEnabled() {
        return isEnabled && isInited;
    }
    
    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }
    
    private long startTimePktNs;
    private long startTimeCompNs;
    @Override
    public void recordStartTimeComp(IOFMessageListener listener) {
        if (isEnabled()) {
            startTimeCompNs = System.nanoTime();
            checkAndStartNextBucket(startTimeCompNs);
        }
    }
    
    @Override
    public void recordEndTimeComp(IOFMessageListener listener) {
        if (isEnabled()) {
            long procTime = System.nanoTime() - startTimeCompNs;
            ctbs.getCurBucket().updateOneComponent(listener, procTime);
        }
    }
    
    @Override
    public void recordStartTimePktIn() {
        if (isEnabled()) {
            startTimePktNs = System.nanoTime();
        }
    }
    
    @Override
    public void recordEndTimePktIn(IOFSwitch sw, OFMessage m, FloodlightContext cntx) {
        if (isEnabled()) {
            long procTimeNs = System.nanoTime() - startTimePktNs;
            ctbs.getCurBucket().updatePerPacketCounters(procTimeNs);
            
            if (ptWarningThresholdInNano > 0 && procTimeNs > ptWarningThresholdInNano) {
                logger.warn("Time to process packet-in: {} us", procTimeNs/1000);
                logger.warn("{}", OFMessage.getDataAsString(sw, m, cntx));
            }
        }
    }
    
    @Override
    public CircularTimeBucketSet getCtbs() {
        return ctbs;
    }

    /***
     * This function is called when a packet in processing starts
     * Check if it is time to go to the next bucket
     * @param curTime_ns
     */
    private void checkAndStartNextBucket(long curTime_ns) {
        // We are not running any timer, packet arrivals themselves drive the 
        // rotation of the buckets
        CumulativeTimeBucket ctb = ctbs.getCurBucket();
        if ((curTime_ns - ctb.getStartTimeNs()) > ONE_BUCKET_DURATION_NANOSECONDS) {
            // Go to next bucket
            this.ctbs.fillTimeBucket(ctb);
            /***
             * We might not have received packets for long time, in which case
             * there would be a gap in the start-time of the timer buckets
             * indicating that there was no data. This should be a better
             * utilization of resources instead of creating several empty
             * buckets.
             */
        }
    }
    
    // IFloodlightModule methods
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IPktInProcessingTimeService.class);
        return l;
    }
    
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(IPktInProcessingTimeService.class, this);
        return m;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStorageSourceService.class);
        l.add(IRestApiService.class);
        return l;
    }
    
    @Override
    public void init(FloodlightModuleContext context)
                                             throws FloodlightModuleException {
        storageSource = 
                context.getServiceImpl(IStorageSourceService.class);
        restApi =
                context.getServiceImpl(IRestApiService.class);
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        // Subscribe to the storage (config change) notifications for the 
        // controller node
        storageSource.addListener(ControllerTableName, this);
        
        // Add our REST API
        restApi.addRestletRoutable(new PerfWebRoutable());
        
        // TODO - Alex - change this to a config option
        ptWarningThresholdInNano = Long.parseLong(System.getProperty(
             "net.floodlightcontroller.core.PTWarningThresholdInMilli", "0")) * 1000000;
        if (ptWarningThresholdInNano > 0) {
            logger.info("Packet processing time threshold for warning set to {} ms.",
                 ptWarningThresholdInNano/1000000);
        }
    }
    
    // IStorageSourceListener
    
    private boolean readPerfMonFromStorage() {
        boolean value=false;
        try {
            IPredicate predicate =
                    new OperatorPredicate(COLUMN_ID, Operator.EQ, "localhost");
            IResultSet resultSet = storageSource.executeQuery(
                    ControllerTableName,
                    new String[] { COLUMN_PERF_MON }, predicate, null);
            if (resultSet.next()) {
                value = resultSet.getBoolean(COLUMN_PERF_MON);
            }
        } catch (StorageException e) {
            logger.error("Failed to read controller table: {}", e.getMessage());
        }
        return value;
    }
    
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        logger.debug("Processing modification of Table {}", tableName);        
        if (ControllerTableName.equals(tableName)) {
            if (rowKeys.contains("localhost")) {
                // Get the current state
                boolean oldProcTimeMonitoringState = isEnabled();
                // Get the new state
                boolean newProcTimeMonitoringState = readPerfMonFromStorage();
                // See if the state changes from disabled to enabled
                if (newProcTimeMonitoringState && !oldProcTimeMonitoringState) {                    
                    setEnabled(newProcTimeMonitoringState);
                    logger.info("Packet-In performance monitoring for " +
                            "controller localhost is enabled");
                } else if (!newProcTimeMonitoringState && 
                            oldProcTimeMonitoringState) {  
                    setEnabled(newProcTimeMonitoringState);
                    logger.info("Performance monitoring for controller " +
                            "localhost is disabled");
                } else {
                    logger.info("Performance monitoring for controller " +
                            "localhost is unchanged: {}", 
                            newProcTimeMonitoringState);
                }
            }
            return;
        }
    }
    
    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        // no-op
    }
}
