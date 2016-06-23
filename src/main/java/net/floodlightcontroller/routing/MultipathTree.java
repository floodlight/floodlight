package net.floodlightcontroller.routing;

import java.util.HashMap;
import java.util.List;
import net.floodlightcontroller.core.types.NodePortTuple;

import org.projectfloodlight.openflow.types.DatapathId;

public class MultipathTree {
	
	protected HashMap<DatapathId, List<List<NodePortTuple>>> dpidPaths;
	
	public MultipathTree() {
		dpidPaths = new HashMap<DatapathId, List<List<NodePortTuple>>>();
	}
	
	public HashMap<DatapathId, List<List<NodePortTuple>>> getDpidPaths() {
		return dpidPaths;
	}
	
	public List<List<NodePortTuple>> getPaths(DatapathId dpid) {
		return dpidPaths.get(dpid);
	}
	
	
	
}