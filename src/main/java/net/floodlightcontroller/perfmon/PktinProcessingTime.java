/**
 * Performance monitoring package
 */
package net.floodlightcontroller.perfmon;

import net.floodlightcontroller.core.IOFMessageListener.FlListenerID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author subrata
 *
 */
public class PktinProcessingTime  {
    
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
        LoggerFactory.getLogger(PktinProcessingTime.class);    
    
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
    
    protected PerfMonConfigs  perfMonCfgs;
    // Maintains the time when the last packet was processed
    protected long lastPktTime_ns; 
    protected long curBucketStartTime;
    // Time bucket created once and reused as needed
    public CumulativeTimeBucket  ctb;   // Current time bucket being filled
    public CircularTimeBucketSet ctbs;  // Set of all time buckets

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

    public CircularTimeBucketSet getCtbs() {
        return ctbs;
    }

    public void setCtbs(CircularTimeBucketSet ctbs) {
        this.ctbs = ctbs;
    }

    public PerfMonConfigs getPerfMonCfgs() {
        return perfMonCfgs;
    }

    public void setPerfMonCfgs(PerfMonConfigs perfMonCfgs) {
        this.perfMonCfgs = perfMonCfgs;
    }

    public PktinProcessingTime() {        
        FlListenerID.populateCompNames();
        perfMonCfgs = new PerfMonConfigs();
        ctbs = new CircularTimeBucketSet();
        ctb  = ctbs.timeBucketSet[ctbs.curBucketIdx];
        ctb.startTime_ms = System.currentTimeMillis();
        ctb.startTime_ns = System.nanoTime();        
    }
    
    /***
     * 30 buckets each holding 10s of processing time data, a total
     * of 30*10s = 5mins of processing time data is maintained
     */
    protected static final long ONE_BUCKET_DURATION_SECONDS_LONG = 10;// seconds
    protected static final int  ONE_BUCKET_DURATION_SECONDS_INT  = 10;// seconds 
    protected static final long ONE_BUCKET_DURATION_NANOSECONDS = 
                                ONE_BUCKET_DURATION_SECONDS_LONG * 1000000000;
    protected static final int  BUCKET_SET_SIZE = 30;
    protected static final int  TOT_PROC_TIME_WARN_THRESHOLD_US =  5000;  // ms
    protected static final int  TOT_PROC_TIME_ALERT_THRESHOLD_US = 10000; // ms, TBD, alert not in logger    
    
    // TBD: Somehow need to get BB last listener id from BB
    protected static final int BB_LAST_LISTENER_ID = 12;  
    
    public class OneComponentTime {   
        int    compId;
        String compName;
        int    pktCnt;
        int    sumProcTime_us;
        long   sumSquaredProcTime_us2;
        int    maxProcTime_us;
        int    minProcTime_us;
        int    avgProcTime_us;
        int    sigmaProcTime_us;  // std. deviation
        
        public int getCompId() {
            return compId;
        }
        public void setCompId(int compId) {
            this.compId = compId;
        }
        public String getCompName() {
            return compName;
        }
        public void setCompName(String compName) {
            this.compName = compName;
        }
        public int getPktCnt() {
            return pktCnt;
        }
        public void setPktCnt(int pktCnt) {
            this.pktCnt = pktCnt;
        }
        public int getSumProcTime_us() {
            return sumProcTime_us;
        }
        public void setSumProcTime_us(int sumProcTime_us) {
            this.sumProcTime_us = sumProcTime_us;
        }
        public long getSumSquaredProcTime_us2() {
            return sumSquaredProcTime_us2;
        }
        public void setSumSquaredProcTime_us2(long sumSquaredProcTime_us2) {
            this.sumSquaredProcTime_us2 = sumSquaredProcTime_us2;
        }
        public int getMaxProcTime_us() {
            return maxProcTime_us;
        }
        public void setMaxProcTime_us(int maxProcTime_us) {
            this.maxProcTime_us = maxProcTime_us;
        }
        public int getMinProcTime_us() {
            return minProcTime_us;
        }
        public void setMinProcTime_us(int minProcTime_us) {
            this.minProcTime_us = minProcTime_us;
        }
        public int getAvgProcTime_us() {
            return avgProcTime_us;
        }
        public void setAvgProcTime_us(int avgProcTime_us) {
            this.avgProcTime_us = avgProcTime_us;
        }
        public int getSigmaProcTime_us() {
            return sigmaProcTime_us;
        }
        public void setSigmaProcTime_us(int sigmaProcTime_us) {
            this.sigmaProcTime_us = sigmaProcTime_us;
        }       
    }
    
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
        // We are not running any timer, packet arrivals themselves drive the rotation of the buckets
        this.lastPktTime_ns = curTime_ns;
        if ((curTime_ns - this.ctb.startTime_ns) >  ONE_BUCKET_DURATION_NANOSECONDS) {            
            // Go to next bucket            
            this.ctbs.fillTimeBucket();  
            /***
             * We might not have received packets for long time, in which case there would be
             * a gap in the start-time of the timer buckets indicating that there was no data
             * This should be a better utilization of resources instead of creating several empty
             * buckets.
             */            
        }
    }
    
    public long getStartTimeOnePkt() {
        if (this.perfMonCfgs.procTimeMonitoringState) {
            long startTime_ns = System.nanoTime();
            checkAndStartNextBucket(startTime_ns);
            return startTime_ns;
        }
        return 0L;
    }
    
    // Component refers to software component like forwarding
    public long getStartTimeOneComponent() { 
        if (this.perfMonCfgs.procTimeMonitoringState) {                 
            return System.nanoTime();
        }
        return 0L;
    }
           
    public class CumulativeTimeBucket {
        int      bucketNo;
        long     startTime_ms;
        long     startTime_ns; // First pkt time-stamp in this bucket     
        // duration for which pkts are put into this bucket in seconds
        int      duration_s;   
        ProcTime tComps;              // processing times of each component        
        int      totalPktCnt;
        int      totalSumProcTime_us; // total processing time for one pkt in
        long     totalSumSquaredProcTime_us;
        int      maxTotalProcTime_us;
        int      minTotalProcTime_us;
        int      avgTotalProcTime_us;
        int      sigmaTotalProcTime_us; // std. deviation
        
        public int getBucketNo() {
            return bucketNo;
        }

        public void setBucketNo(int bucketNo) {
            this.bucketNo = bucketNo;
        }

        public long getStartTime_ms() {
            return startTime_ms;
        }

        public void setStartTime_ms(long startTime_ms) {
            this.startTime_ms = startTime_ms;
        }

        public long getStartTime_ns() {
            return startTime_ns;
        }

        public void setStartTime_ns(long startTime_ns) {
            this.startTime_ns = startTime_ns;
        }

        public int getDuration_s() {
            return duration_s;
        }

        public void setDuration_s(int duration_s) {
            this.duration_s = duration_s;
        }

        public ProcTime getTComps() {
            return tComps;
        }

        public void setTComps(ProcTime tComps) {
            this.tComps = tComps;
        }

        public int getTotalPktCnt() {
            return totalPktCnt;
        }

        public void setTotalPktCnt(int totalPktCnt) {
            this.totalPktCnt = totalPktCnt;
        }

        public int getTotalSumProcTime_us() {
            return totalSumProcTime_us;
        }

        public void setTotalSumProcTime_us(int totalSumProcTime_us) {
            this.totalSumProcTime_us = totalSumProcTime_us;
        }

        public Long getTotalSumSquaredProcTime_us() {
            return totalSumSquaredProcTime_us;
        }

        public void setTotalSumSquaredProcTime_us(
                            Long totalSumSquaredProcTime_us) {
            this.totalSumSquaredProcTime_us = totalSumSquaredProcTime_us;
        }

        public int getMaxTotalProcTime_us() {
            return maxTotalProcTime_us;
        }

        public void setMaxTotalProcTime_us(int maxTotalProcTime_us) {
            this.maxTotalProcTime_us = maxTotalProcTime_us;
        }

        public int getMinTotalProcTime_us() {
            return minTotalProcTime_us;
        }

        public void setMinTotalProcTime_us(int minTotalProcTime_us) {
            this.minTotalProcTime_us = minTotalProcTime_us;
        }

        public int getAvgTotalProcTime_us() {
            return avgTotalProcTime_us;
        }

        public void setAvgTotalProcTime_us(int avgTotalProcTime_us) {
            this.avgTotalProcTime_us = avgTotalProcTime_us;
        }

        public int getSigmaTotalProcTime_us() {
            return sigmaTotalProcTime_us;
        }

        public void setSigmaTotalProcTime_us(int sigmaTotalProcTime_us) {
            this.sigmaTotalProcTime_us = sigmaTotalProcTime_us;
        }

        public CumulativeTimeBucket() {                     
            duration_s  = 0;
            maxTotalProcTime_us = Integer.MIN_VALUE;
            minTotalProcTime_us = Integer.MAX_VALUE; 
            tComps = new ProcTime();            
        }
        
        // Initialize the time bucket so that it can be reused for the next 
        // interval, thus not
        // creating lots of garbage
        public void initializeCumulativeTimeBucket(
                            CumulativeTimeBucket cumulativeTimeBkt) {
            assert(cumulativeTimeBkt != null);
            if (cumulativeTimeBkt == null)  {
                return;
            }
        
            cumulativeTimeBkt.startTime_ms = System.currentTimeMillis();
            cumulativeTimeBkt.startTime_ns = System.nanoTime();
            cumulativeTimeBkt.duration_s  = 0;
            cumulativeTimeBkt.totalPktCnt  = 0;
            cumulativeTimeBkt.totalSumProcTime_us            = 0;
            cumulativeTimeBkt.totalSumSquaredProcTime_us     = 0L;
            cumulativeTimeBkt.maxTotalProcTime_us = Integer.MIN_VALUE;
            cumulativeTimeBkt.minTotalProcTime_us = Integer.MAX_VALUE;
            cumulativeTimeBkt.avgTotalProcTime_us = 0;
            cumulativeTimeBkt.sigmaTotalProcTime_us = 0;
            for (int idx = FlListenerID.FL_FIRST_LISTENER_ID; 
                                    idx <= BB_LAST_LISTENER_ID; idx++) {
                OneComponentTime oct = cumulativeTimeBkt.tComps.oneComp[idx];
                oct.pktCnt = 0;
                oct.sumProcTime_us = 0;
                oct.sumSquaredProcTime_us2 = 0;
                oct.maxProcTime_us = Integer.MIN_VALUE;
                oct.minProcTime_us = Integer.MAX_VALUE;
                oct.avgProcTime_us = 0;
                oct.sigmaProcTime_us = 0;
            }                
        }
    }
    
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
            } else if (onePktOneCompProcTime_us < t_temp.minProcTime_us) {
                t_temp.minProcTime_us = onePktOneCompProcTime_us;
            }   
        }
    }
    
    public void updateCumulativeTimeTotal(long onePktStartTime_ns) {
        if (this.perfMonCfgs.procTimeMonitoringState) {
            // There is no api to get time in microseconds, milliseconds is too coarse
            // Hence we have to use nanoseconds and then divide by 1000 to get mucroseconds
            int onePktProcTime_us = (int)((System.nanoTime() - onePktStartTime_ns) / 1000);
            if (onePktProcTime_us > TOT_PROC_TIME_WARN_THRESHOLD_US) {
                logger.warn("Total processing time for one packet exceeded threshold: proc time: {}ms", onePktProcTime_us/1000);
            }
            this.ctb.totalPktCnt++;
            this.ctb.totalSumProcTime_us += onePktProcTime_us;
            this.ctb.totalSumSquaredProcTime_us += onePktProcTime_us * onePktProcTime_us;
            
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
        
        public CircularTimeBucketSet() {
            timeBucketSet   = new CumulativeTimeBucket[BUCKET_SET_SIZE];
            for (int idx= 0; idx < BUCKET_SET_SIZE; idx++) {
                timeBucketSet[idx] = new CumulativeTimeBucket();
                timeBucketSet[idx].setBucketNo(idx);
            }
            allBucketsValid = false;   
            curBucketIdx    = 0;
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
}
