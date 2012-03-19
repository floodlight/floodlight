package net.floodlightcontroller.perfmon;

import java.util.Set;

import net.floodlightcontroller.core.IOFMessageListener;

public class CircularTimeBucketSet {
    /**
     * How many timer buckets have valid data, initially it is false then it
     * stays at true after the circle is completed
     */
    private boolean allBucketsValid;
    private int curBucketIdx; // most recent bucket *being* filled
    private CumulativeTimeBucket[] timeBucketSet;
    private int bucketDurationS = 0; // seconds

    public CircularTimeBucketSet(Set<IOFMessageListener> listeners, int numBuckets, int durationS) {
        bucketDurationS = durationS;
        timeBucketSet = new CumulativeTimeBucket[numBuckets];
        for (int idx= 0; idx < numBuckets; idx++) {
            timeBucketSet[idx] = new CumulativeTimeBucket(listeners, idx, bucketDurationS);
        }
        allBucketsValid = false;
        curBucketIdx = 0;
    }
    
    public CumulativeTimeBucket getCurBucket() {
        return timeBucketSet[curBucketIdx];
    }
    
    public boolean isAllBucketsValid() {
        return allBucketsValid;
    }

    // Called when the bucket time ends
    public void fillTimeBucket(CumulativeTimeBucket ctb) {
        if (ctb.getTotalPktCnt() > 0) {
            ctb.computeAverages();
        }

        // Move to the new bucket
        if (curBucketIdx >= (timeBucketSet.length - 1)) {
            curBucketIdx = 0; 
            allBucketsValid = true;
        } else {
            curBucketIdx++;
        }
        // Get the next bucket to be filled ready
        ctb = timeBucketSet[curBucketIdx];
        // Empty it's counters
        ctb.reset();
    }
}