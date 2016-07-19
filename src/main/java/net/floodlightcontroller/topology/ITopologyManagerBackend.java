package net.floodlightcontroller.topology;

import net.floodlightcontroller.routing.IRoutingService.PATH_METRIC;

public interface ITopologyManagerBackend extends ITopologyService {
    public TopologyInstance getCurrentTopologyInstance();
    
    public PATH_METRIC getPathMetric();
    public void setPathMetric(PATH_METRIC metric);
    
    public int getMaxPathsToCompute();
    public void setMaxPathsToCompute(int max);
    
    public boolean forceRecompute();
}