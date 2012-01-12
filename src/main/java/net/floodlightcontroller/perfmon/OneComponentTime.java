package net.floodlightcontroller.perfmon;

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