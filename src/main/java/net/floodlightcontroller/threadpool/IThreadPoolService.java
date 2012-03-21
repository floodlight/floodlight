package net.floodlightcontroller.threadpool;

import java.util.concurrent.ScheduledExecutorService;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IThreadPoolService extends IFloodlightService {
    /**
     * Get the master scheduled thread pool executor maintained by the
     * ThreadPool provider.  This can be used by other modules as a centralized
     * way to schedule tasks.
     * @return
     */
    public ScheduledExecutorService getScheduledExecutor();
}
