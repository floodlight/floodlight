package net.floodlightcontroller.statistics;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

public interface IStatisticsService extends IFloodlightService {

	public SwitchPortBandwidth getBandwidthConsumption(DatapathId dpid, OFPort p);
		
	public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthConsumption();
	
	public void collectStatistics(boolean collect);
}
