package net.floodlightcontroller.statistics;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import javafx.util.Pair;

import java.util.Map;
import java.util.Set;

public interface IStatisticsService extends IFloodlightService {
	
	public String setFlowStatsPeriod(int period);
	
	public String setPortStatsPeriod(int period);
	
	public Map<NodePortTuple, PortDesc> getPortDesc();
	
	public PortDesc getPortDesc(DatapathId dpid, OFPort p);
	
	public Map<Pair<Match,DatapathId>, FlowRuleStats> getFlowStats();
	
	public Set<FlowRuleStats> getFlowStats(DatapathId dpid);

	public SwitchPortBandwidth getBandwidthConsumption(DatapathId dpid, OFPort p);
		
	public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthConsumption();
	
	public void collectStatistics(boolean collect);
}
