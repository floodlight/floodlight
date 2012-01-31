package net.floodlightcontroller.perfmon;

import net.floodlightcontroller.core.IFloodlightService;
import net.floodlightcontroller.perfmon.PktInProcessingTime.CircularTimeBucketSet;
import net.floodlightcontroller.perfmon.PktInProcessingTime.PerfMonConfigs;

public interface IPktInProcessingTimeService extends IFloodlightService {

    public Long getLastPktTime_ns();

    public void setLastPktTime_ns(Long lastPktTime_ns);

    public long getCurBucketStartTime();

    public void setCurBucketStartTime(long curBucketStartTime);

    public CumulativeTimeBucket getCtb();

    public void setCtb(CumulativeTimeBucket ctb);

    public CircularTimeBucketSet getCtbs();

    public void setCtbs(CircularTimeBucketSet ctbs);

    public PerfMonConfigs getPerfMonCfgs();

    public void setPerfMonCfgs(PerfMonConfigs perfMonCfgs);

    public int getNumComponents();

    public void setNumComponents(int numComponents);

    public long getStartTimeOnePkt();

    // Component refers to software component like forwarding
    public long getStartTimeOneComponent();

    public void updateCumulativeTimeOneComp(long onePktOneCompProcTime_ns,
                                            int id);

    public void updateCumulativeTimeTotal(long onePktStartTime_ns);

}
