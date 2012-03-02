/**
 * Performance monitoring package
 */
package net.floodlightcontroller.perfmon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.IOFMessageListener.FlListenerID;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author subrata
 *
 */
public class PktInProcessingTime
    implements IFloodlightModule, IPktInProcessingTimeService  {

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

    protected static  Logger  logger = 
        LoggerFactory.getLogger(PktInProcessingTime.class);

    protected PerfMonConfigs perfMonCfgs;
    // Maintains the time when the last packet was processed
    protected long lastPktTime_ns; 
    protected long curBucketStartTime;
    // Time bucket created once and reused as needed
    public CumulativeTimeBucket  ctb;   // Current time bucket being filled
    public CircularTimeBucketSet ctbs;  // Set of all time buckets
    private int numComponents;          // Numbert of components being monitored
    private int numBuckets;             // number of time buckets, each 10s long

    @Override
    public boolean isEnabled() {
        return perfMonCfgs.isProcTimeMonitoringState();
    }

    public Long getLastPktTime_ns() {
        return lastPktTime_ns;
    }

    public void setLastPktTime_ns(Long lastPktTime_ns) {
        this.lastPktTime_ns = lastPktTime_ns;
    }

    public long getCurBucketStartTime() {
        return curBucketStartTime;
    }

    public void setCurBucketStartTime(long curBucketStartTime) {
        this.curBucketStartTime = curBucketStartTime;
    }

    public CumulativeTimeBucket getCtb() {
        return ctb;
    }

    public void setCtb(CumulativeTimeBucket ctb) {
        this.ctb = ctb;
    }
    
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getCtbs()
     */
    @Override
    public CircularTimeBucketSet getCtbs() {
        return ctbs;
    }

    public void setCtbs(CircularTimeBucketSet ctbs) {
        this.ctbs = ctbs;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getPerfMonCfgs()
     */
    @Override
    public PerfMonConfigs getPerfMonCfgs() {
        return perfMonCfgs;
    }

    public void setPerfMonCfgs(PerfMonConfigs perfMonCfgs) {
        this.perfMonCfgs = perfMonCfgs;
    }

    public int getNumComponents() {
        return numComponents;
    }

    public void setNumComponents(int numComponents) {
        this.numComponents = numComponents;
    }

    public int getNumBuckets() {
        return numBuckets;
    }
    public void setNumBuckets(int numBuckets) {
        this.numBuckets = numBuckets;
    }
    
    /***
     * BUCKET_SET_SIZE buckets each holding 10s of processing time data, a total
     * of 30*10s = 5mins of processing time data is maintained
     */
    protected static final long ONE_BUCKET_DURATION_SECONDS_LONG = 10;// seconds
    protected static final long ONE_BUCKET_DURATION_NANOSECONDS  =
                                ONE_BUCKET_DURATION_SECONDS_LONG * 1000000000;
    protected static final int  BUCKET_SET_SIZE = 360; // 1hr (=1*60*60/10)
    protected static final int  TOT_PROC_TIME_WARN_THRESHOLD_US  =  5000;  // ms
    protected static final int  TOT_PROC_TIME_ALERT_THRESHOLD_US = 10000;
                                // ms, TBD, alert not in logger

    // TBD: Somehow need to get BB last listener id from BB
    protected static final int BB_LAST_LISTENER_ID = 13;

    public class ProcTime {
        OneComponentTime [] oneComp;

        public OneComponentTime[] getOneComp() {
            return oneComp;
        }

        public void setOneComp(OneComponentTime[] oneComp) {
            this.oneComp = oneComp;
        }

        public ProcTime() {
            oneComp = new OneComponentTime[BB_LAST_LISTENER_ID + 1];
            for (int idx = FlListenerID.FL_FIRST_LISTENER_ID;
                            idx <= BB_LAST_LISTENER_ID; idx++) {
                oneComp[idx] = new OneComponentTime();
                // Initialise the min and max values;
                oneComp[idx].maxProcTime_us = Integer.MIN_VALUE;
                oneComp[idx].minProcTime_us = Integer.MAX_VALUE;  
                // Set the component id and name
                oneComp[idx].setCompId(idx);
                oneComp[idx].setCompName(
                        FlListenerID.getListenerNameFromId(idx));
            }
        }
    }

    /***
     * This function is called when a packet in processing  starts
     * Check if it is time to go to the next bucket
     * @param curTime_ns
     */
    private void checkAndStartNextBucket(Long curTime_ns) {
        // We are not running any timer, packet arrivals themselves drive the 
        // rotation of the buckets
        this.lastPktTime_ns = curTime_ns;
        if ((curTime_ns - this.ctb.startTime_ns) >
                                            ONE_BUCKET_DURATION_NANOSECONDS) {
            // Go to next bucket
            this.ctbs.fillTimeBucket(ctb, numBuckets);
            /***
             * We might not have received packets for long time, in which case
             * there would be a gap in the start-time of the timer buckets
             * indicating that there was no data. This should be a better
             * utilization of resources instead of creating several empty
             * buckets.
             */
        }
    }

    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getStartTimeOnePkt()
     */
    @Override
    public long getStartTimeOnePkt() {
        if (this.perfMonCfgs.procTimeMonitoringState) {
            long startTime_ns = System.nanoTime();
            checkAndStartNextBucket(startTime_ns);
            return startTime_ns;
        }
        return 0L;
    }

    // Component refers to software component like forwarding
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getStartTimeOneComponent()
     */
    @Override
    public long getStartTimeOneComponent() {
        if (this.perfMonCfgs.procTimeMonitoringState) {
            return System.nanoTime();
        }
        return 0L;
    }

    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#updateCumulativeTimeOneComp(long, int)
     */
    @Override
    public void updateCumulativeTimeOneComp(
                                long onePktOneCompProcTime_ns, int id) {
        if (this.perfMonCfgs.procTimeMonitoringState) {
            int onePktOneCompProcTime_us = 
                (int)((System.nanoTime() - onePktOneCompProcTime_ns) / 1000);
            OneComponentTime t_temp = this.ctb.tComps.oneComp[id];
            t_temp.pktCnt++;
            t_temp.sumProcTime_us += onePktOneCompProcTime_us;
            t_temp.sumSquaredProcTime_us2 +=
                onePktOneCompProcTime_us * onePktOneCompProcTime_us;
            if (onePktOneCompProcTime_us > t_temp.maxProcTime_us) {
                t_temp.maxProcTime_us = onePktOneCompProcTime_us;
            }
            if (onePktOneCompProcTime_us < t_temp.minProcTime_us) {
                t_temp.minProcTime_us = onePktOneCompProcTime_us;
            }
        }
    }

    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#updateCumulativeTimeTotal(long)
     */
    @Override
    public void updateCumulativeTimeTotal(long onePktStartTime_ns) {
        if (this.perfMonCfgs.procTimeMonitoringState) {
            // There is no api to get time in microseconds, milliseconds is 
            // too coarse hence we have to use nanoseconds and then divide by 
            // 1000 to get microseconds
            int onePktProcTime_us = 
                        (int)((System.nanoTime() - onePktStartTime_ns) / 1000);
            if (onePktProcTime_us > TOT_PROC_TIME_WARN_THRESHOLD_US) {
                logger.warn("Total processing time for one packet exceeded" +
                        "threshold: proc time: {}ms", onePktProcTime_us/1000);
            }
            this.ctb.totalPktCnt++;
            this.ctb.totalSumProcTime_us += onePktProcTime_us;
            this.ctb.totalSumSquaredProcTime_us +=
                                    onePktProcTime_us * onePktProcTime_us;

            if (onePktProcTime_us > this.ctb.maxTotalProcTime_us) {
                this.ctb.maxTotalProcTime_us = onePktProcTime_us;
            } else if (onePktProcTime_us < this.ctb.minTotalProcTime_us) {
                this.ctb.minTotalProcTime_us = onePktProcTime_us;
            }
        }

    }

    

    public PktInProcessingTime() {
        perfMonCfgs = new PerfMonConfigs();
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
        // We don't have any dependencies
        return null;
    }
    
    @Override
    public void init(FloodlightModuleContext context)
                                             throws FloodlightModuleException {
        // no-op
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        // Our 'constructor'
        FlListenerID.populateCompNames();
        setNumComponents(BB_LAST_LISTENER_ID + 1);
        perfMonCfgs = new PerfMonConfigs();
        numBuckets = BUCKET_SET_SIZE;
        ctbs = new CircularTimeBucketSet(getNumComponents(), numBuckets);
        ctb  = ctbs.timeBucketSet[ctbs.curBucketIdx];
        ctb.startTime_ms = System.currentTimeMillis();
        ctb.startTime_ns = System.nanoTime();
    }
}
