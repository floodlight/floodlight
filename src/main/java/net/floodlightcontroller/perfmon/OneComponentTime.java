/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.perfmon;

import org.codehaus.jackson.annotate.JsonProperty;

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
    
    @JsonProperty("module-name")
    public String getCompName() {
        return compName;
    }

    @JsonProperty("num-packets")
    public int getPktCnt() {
        return pktCnt;
    }

    @JsonProperty("total")
    public long getSumProcTimeNs() {
        return totalProcTimeNs;
    }

    @JsonProperty("max")
    public long getMaxProcTimeNs() {
        return maxProcTimeNs;
    }

    @JsonProperty("min")
    public long getMinProcTimeNs() {
        return minProcTimeNs;
    }

    @JsonProperty("average")
    public long getAvgProcTimeNs() {
        return avgProcTimeNs;
    }

    @JsonProperty("std-dev")
    public long getSigmaProcTimeNs() {
        return sigmaProcTimeNs;
    }
    
    @JsonProperty("average-squared")
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