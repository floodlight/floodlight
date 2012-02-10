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

    protected static Logger logger = 
        LoggerFactory.getLogger(PktInProcessingTime.class);    

    /***
     * procTimeMonitoringState: true if monitoring is on, default is false
     * this variable is controller using a cli under the controller node
     * (config-controller)> [no] performance-monitor processing-time
     */

    public class PerfMonConfigs {
        // overall performance monitoring knob; turned off by default
        protected boolean procTimeMonitoringState;
        // overall per-component performance monitoring knob; off by default
        protected boolean procTimePerCompMonitoringState;
        // knob for database performance monitoring
        protected boolean dbTimePerfMonState;

        public boolean isProcTimeMonitoringState() {
            return procTimeMonitoringState;
        }
        public void setProcTimeMonitoringState(
                        boolean procTimeMonitoringState) {
            this.procTimeMonitoringState = procTimeMonitoringState;
        }
        public boolean isProcTimePerCompMonitoringState() {
            return procTimePerCompMonitoringState;
        }
        public void setProcTimePerCompMonitoringState(
                boolean procTimePerCompMonitoringState) {
            this.procTimePerCompMonitoringState = 
                        procTimePerCompMonitoringState;
        }
        public boolean isDbTimePerfMonState() {
            return dbTimePerfMonState;
        }
        public void setDbTimePerfMonState(boolean dbTimePerfMonState) {
            this.dbTimePerfMonState = dbTimePerfMonState;
        }

        public PerfMonConfigs() {
            procTimeMonitoringState        = false;
            procTimePerCompMonitoringState = false;
            dbTimePerfMonState             = false;
        }
    }

    protected PerfMonConfigs perfMonCfgs;
    // Maintains the time when the last packet was processed
    protected long lastPktTime_ns; 
    protected long curBucketStartTime;
    // Time bucket created once and reused as needed
    public CumulativeTimeBucket  ctb;   // Current time bucket being filled
    public CircularTimeBucketSet ctbs;  // Set of all time buckets
    private int numComponents;


    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getLastPktTime_ns()
     */
    @Override
    public Long getLastPktTime_ns() {
        return lastPktTime_ns;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#setLastPktTime_ns(java.lang.Long)
     */
    @Override
    public void setLastPktTime_ns(Long lastPktTime_ns) {
        this.lastPktTime_ns = lastPktTime_ns;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getCurBucketStartTime()
     */
    @Override
    public long getCurBucketStartTime() {
        return curBucketStartTime;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#setCurBucketStartTime(long)
     */
    @Override
    public void setCurBucketStartTime(long curBucketStartTime) {
        this.curBucketStartTime = curBucketStartTime;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getCtb()
     */
    @Override
    public CumulativeTimeBucket getCtb() {
        return ctb;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#setCtb(net.floodlightcontroller.perfmon.CumulativeTimeBucket)
     */
    @Override
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
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#setCtbs(net.floodlightcontroller.perfmon.PktinProcessingTime.CircularTimeBucketSet)
     */
    @Override
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
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#setPerfMonCfgs(net.floodlightcontroller.perfmon.PktinProcessingTime.PerfMonConfigs)
     */
    @Override
    public void setPerfMonCfgs(PerfMonConfigs perfMonCfgs) {
        this.perfMonCfgs = perfMonCfgs;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#getNumComponents()
     */
    @Override
    public int getNumComponents() {
        return numComponents;
    }
    /* (non-Javadoc)
     * @see net.floodlightcontroller.perfmon.IPktInProcessingTimeService#setNumComponents(int)
     */
    @Override
    public void setNumComponents(int numComponents) {
        this.numComponents = numComponents;
    }

    /***
     * 30 buckets each holding 10s of processing time data, a total
     * of 30*10s = 5mins of processing time data is maintained
     */
    protected static final long ONE_BUCKET_DURATION_SECONDS_LONG = 10;// seconds
    protected static final int  ONE_BUCKET_DURATION_SECONDS_INT  = 10;// seconds 
    protected static final long ONE_BUCKET_DURATION_NANOSECONDS  =
                                ONE_BUCKET_DURATION_SECONDS_LONG * 1000000000;
    protected static final int  BUCKET_SET_SIZE = 30;
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
            this.ctbs.fillTimeBucket();
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

    public class CircularTimeBucketSet {

        /**
         * How many timer buckets have valid data, initially it is false then it
         * stays at true after the circle is completed
         */
        boolean allBucketsValid;
        int     curBucketIdx; // most recent bucket *being* filled
        int     numComps;
        CumulativeTimeBucket [] timeBucketSet;

        public boolean isAllBucketsValid() {
            return allBucketsValid;
        }

        public void setAllBucketsValid(boolean allBucketsValid) {
            this.allBucketsValid = allBucketsValid;
        }

        public int getCurBucketIdx() {
            return curBucketIdx;
        }

        public void setCurBucketIdx(int curBucketIdx) {
            this.curBucketIdx = curBucketIdx;
        }

        public int getNumComps() {
            return numComps;
        }

        public CumulativeTimeBucket[] getTimeBucketSet() {
            return timeBucketSet;
        }

        public void setTimeBucketSet(CumulativeTimeBucket[] timeBucketSet) {
            this.timeBucketSet = timeBucketSet;
        }

        private int computeSigma(int sum, Long sumSquared, int count) {
            // Computes std. deviation from the sum of count numbers and from
            // the sum of the squares of count numbers
            Long temp = (long) sum;
            temp = temp * temp / count;
            temp = (sumSquared - temp) / count;
            return  (int) Math.sqrt((double)temp);
        }

        public CircularTimeBucketSet(int numComps) {
            timeBucketSet   = new CumulativeTimeBucket[BUCKET_SET_SIZE];
            for (int idx= 0; idx < BUCKET_SET_SIZE; idx++) {
                timeBucketSet[idx] = new CumulativeTimeBucket(numComps);
                timeBucketSet[idx].setBucketNo(idx);
            }
            allBucketsValid = false;
            curBucketIdx    = 0;
            this.numComps   = numComps;
        }

        // Called when the bucket time ends
        public void fillTimeBucket() {
            // Wrap up computations on the current bucket data
            // The following operation can be done in the front end instead of
            // here if it turns out to be a performance issue
            if (ctb.totalPktCnt > 0) {
                ctb.avgTotalProcTime_us = 
                    ctb.totalSumProcTime_us / ctb.totalPktCnt;
                ctb.sigmaTotalProcTime_us = 
                    computeSigma(ctb.totalSumProcTime_us, 
                            ctb.totalSumSquaredProcTime_us, ctb.totalPktCnt);

                // Find the avg and std. dev. of each component's proc. time
                for (int idx = FlListenerID.FL_FIRST_LISTENER_ID; 
                    idx <= BB_LAST_LISTENER_ID; idx++) {
                    OneComponentTime oct = ctb.tComps.oneComp[idx];
                    if (oct.pktCnt > 0) {
                        oct.avgProcTime_us   = oct.sumProcTime_us / oct.pktCnt;
                        oct.sigmaProcTime_us = computeSigma(oct.sumProcTime_us,
                                oct.sumSquaredProcTime_us2, oct.pktCnt);
                    }
                }
            }
            ctb.duration_s = ONE_BUCKET_DURATION_SECONDS_INT;

            // Move to the new bucket
            if (curBucketIdx >= BUCKET_SET_SIZE-1) {
                curBucketIdx = 0; 
                allBucketsValid = true;
            } else {
                curBucketIdx++;
            }
            // Get the next bucket to be filled ready
            ctb = timeBucketSet[curBucketIdx];
            ctb.initializeCumulativeTimeBucket(ctb);
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
        // We don't have any dependencies
        return null;
    }
    
    @Override
    public void init(FloodlightModuleContext context)
                                             throws FloodlightModuleException {
        perfMonCfgs = new PerfMonConfigs();
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        // Our 'constructor'
        FlListenerID.populateCompNames();
        setNumComponents(BB_LAST_LISTENER_ID + 1);
        ctbs = new CircularTimeBucketSet(getNumComponents());
        ctb  = ctbs.timeBucketSet[ctbs.curBucketIdx];
        ctb.startTime_ms = System.currentTimeMillis();
        ctb.startTime_ns = System.nanoTime();
    }
}
