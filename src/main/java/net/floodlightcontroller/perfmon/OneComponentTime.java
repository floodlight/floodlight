package net.floodlightcontroller.perfmon;

import net.floodlightcontroller.core.IOFMessageListener;

/**
 * Holds OF message processing time information for one IFloodlightModule.
 * @author Subrata
 */
public class OneComponentTime {
    private int compId; // hascode of IOFMessageListener
    private String compName;
    private int pktCnt;
    // all times in nanoseconds
    private long totalProcTimeNs;
    private long sumSquaredProcTimeNs2; // squared
    private long maxProcTimeNs;
    private long minProcTimeNs;
    private long avgProcTimeNs;
    private long sigmaProcTimeNs;  // std. deviation

    public OneComponentTime(IOFMessageListener module) {
        compId = module.hashCode();
        compName = module.getClass().getCanonicalName();
        resetAllCounters();
    }
    
    public void resetAllCounters() {
        maxProcTimeNs = Long.MIN_VALUE;
        minProcTimeNs = Long.MAX_VALUE;
        pktCnt = 0;
        totalProcTimeNs = 0;
        sumSquaredProcTimeNs2 = 0;
        avgProcTimeNs = 0;
        sigmaProcTimeNs = 0;
    }
    
    public String getCompName() {
        return compName;
    }

    public int getPktCnt() {
        return pktCnt;
    }

    public long getSumProcTime_us() {
        return totalProcTimeNs;
    }

    public long getMaxProcTime_us() {
        return maxProcTimeNs;
    }

    public long getMinProcTime_us() {
        return minProcTimeNs;
    }

    public long getAvgProcTime_us() {
        return avgProcTimeNs;
    }

    public long getSigmaProcTime_us() {
        return sigmaProcTimeNs;
    }

    // Methods used to update the counters
    
    private void increasePktCount() {
        pktCnt++;
    }
    
    private void updateTotalProcessingTime(long curTimeMs) {
        totalProcTimeNs += curTimeMs;
    }
    
    private void updateAvgProcessTime(long curTimeMs) {
        avgProcTimeNs = totalProcTimeNs / pktCnt;
    }
    
    private void updateSquaredProcessingTime(long curTimeMs) {
        sumSquaredProcTimeNs2 += (Math.pow(curTimeMs, 2));
    }
    
    private void calculateMinProcTime(long curTimeMs) {
        if (curTimeMs < minProcTimeNs)
            minProcTimeNs = curTimeMs;
    }
    
    private void calculateMaxProcTime(long curTimeMs) {
        if (curTimeMs > maxProcTimeNs)
            maxProcTimeNs = curTimeMs;
    }
    
    public void computeSigma() {
        // Computes std. deviation from the sum of count numbers and from
        // the sum of the squares of count numbers
        double temp = totalProcTimeNs;
        temp = Math.pow(temp, 2) / pktCnt;
        temp = (sumSquaredProcTimeNs2 - temp) / pktCnt;
        sigmaProcTimeNs = (long) Math.sqrt(temp);
    }
    
    public void updatePerPacketCounters(long procTimeNs) {
        increasePktCount();
        updateTotalProcessingTime(procTimeNs);
        calculateMinProcTime(procTimeNs);
        calculateMaxProcTime(procTimeNs);
        updateAvgProcessTime(procTimeNs);
        updateSquaredProcessingTime(procTimeNs);
    }
    
    @Override
    public int hashCode() {
        return compId;
    }
}