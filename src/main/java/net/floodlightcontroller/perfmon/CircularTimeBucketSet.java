package net.floodlightcontroller.perfmon;

import net.floodlightcontroller.core.IOFMessageListener.FlListenerID;

public class CircularTimeBucketSet {

    /**
     * How many timer buckets have valid data, initially it is false then it
     * stays at true after the circle is completed
     */
    boolean allBucketsValid;
    int     curBucketIdx; // most recent bucket *being* filled
    int     numComps;
    CumulativeTimeBucket [] timeBucketSet;
    // TBD: Somehow need to get BB last listener id from BB
    protected static final int BB_LAST_LISTENER_ID = 13;
    protected static final int  ONE_BUCKET_DURATION_SECONDS_INT  = 10;// seconds 

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

    public CircularTimeBucketSet(int numComps, int numBuckets) {
        timeBucketSet   = new CumulativeTimeBucket[numBuckets];
        for (int idx= 0; idx < numBuckets; idx++) {
            timeBucketSet[idx] = new CumulativeTimeBucket(numComps);
            timeBucketSet[idx].setBucketNo(idx);
        }
        allBucketsValid = false;
        curBucketIdx    = 0;
        this.numComps   = numComps;
    }

    // Called when the bucket time ends
    public void fillTimeBucket(CumulativeTimeBucket ctb, int numBuckets) {
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
        if (curBucketIdx >= numBuckets-1) {
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