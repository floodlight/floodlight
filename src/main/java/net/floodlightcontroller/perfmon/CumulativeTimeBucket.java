package net.floodlightcontroller.perfmon;

import net.floodlightcontroller.core.IOFMessageListener.FlListenerID;

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
    int      numComps;

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

    public ProcTime gettComps() {
        return tComps;
    }

    public void settComps(ProcTime tComps) {
        this.tComps = tComps;
    }

    public int getNumComps() {
        return numComps;
    }

    public void setNumComps(int numComps) {
        this.numComps = numComps;
    }

    public void setTotalSumSquaredProcTime_us(long totalSumSquaredProcTime_us) {
        this.totalSumSquaredProcTime_us = totalSumSquaredProcTime_us;
    }

    public class ProcTime {
        OneComponentTime [] oneComp;

        public OneComponentTime[] getOneComp() {
            return oneComp;
        }

        public void setOneComp(OneComponentTime[] oneComp) {
            this.oneComp = oneComp;
        }

        public ProcTime(int numComponents) {
            oneComp = new OneComponentTime[numComponents];
            for (int idx = FlListenerID.FL_FIRST_LISTENER_ID;
                    idx < numComponents; idx++) {
                oneComp[idx] = new OneComponentTime();
                // Initialize the min and max values;
                oneComp[idx].maxProcTime_us = Integer.MIN_VALUE;
                oneComp[idx].minProcTime_us = Integer.MAX_VALUE;  
                // Set the component id and name
                oneComp[idx].setCompId(idx);
                oneComp[idx].setCompName(
                        FlListenerID.getListenerNameFromId(idx));
            }
        }
    }

    public CumulativeTimeBucket(int numComponents) {
        duration_s  = 0;
        maxTotalProcTime_us = Integer.MIN_VALUE;
        minTotalProcTime_us = Integer.MAX_VALUE; 
        tComps = new ProcTime(numComponents);
        numComps = numComponents;
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
                        idx <= PktInProcessingTime.BB_LAST_LISTENER_ID; idx++) {
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