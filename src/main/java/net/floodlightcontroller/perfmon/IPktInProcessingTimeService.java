package net.floodlightcontroller.perfmon;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.perfmon.CircularTimeBucketSet;

public interface IPktInProcessingTimeService extends IFloodlightService {

    public boolean isEnabled();
    
    public CircularTimeBucketSet getCtbs();

    public long getStartTimeOnePkt();

    // Component refers to software component like forwarding
    public long getStartTimeOneComponent();

    public void updateCumulativeTimeOneComp(long onePktOneCompProcTime_ns,
                                            int id);

    public void updateCumulativeTimeTotal(long onePktStartTime_ns);

}
