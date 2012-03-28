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

    public long getSumProcTimeNs() {
        return totalProcTimeNs;
    }

    public long getMaxProcTimeNs() {
        return maxProcTimeNs;
    }

    public long getMinProcTimeNs() {
        return minProcTimeNs;
    }

    public long getAvgProcTimeNs() {
        return avgProcTimeNs;
    }

    public long getSigmaProcTimeNs() {
        return sigmaProcTimeNs;
    }
    
    public long getSumSquaredProcTimeNs() {
        return sumSquaredProcTimeNs2;
    }

    // Methods used to update the counters
    
    private void increasePktCount() {
        pktCnt++;
    }
    
    private void updateTotalProcessingTime(long procTimeNs) {
        totalProcTimeNs += procTimeNs;
    }
    
    private void updateAvgProcessTime() {
        avgProcTimeNs = totalProcTimeNs / pktCnt;
    }
    
    private void updateSquaredProcessingTime(long procTimeNs) {
        sumSquaredProcTimeNs2 += (Math.pow(procTimeNs, 2));
    }
    
    private void calculateMinProcTime(long curTimeNs) {
        if (curTimeNs < minProcTimeNs)
            minProcTimeNs = curTimeNs;
    }
    
    private void calculateMaxProcTime(long curTimeNs) {
        if (curTimeNs > maxProcTimeNs)
            maxProcTimeNs = curTimeNs;
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
        updateAvgProcessTime();
        updateSquaredProcessingTime(procTimeNs);
    }
    
    @Override
    public int hashCode() {
        return compId;
    }
}