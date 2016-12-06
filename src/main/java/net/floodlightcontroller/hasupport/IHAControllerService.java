package net.floodlightcontroller.hasupport;

import java.util.ArrayList;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IHAControllerService extends IFloodlightService {
	
	public String getLeader();
	
	public void setElectionPriorities(ArrayList<Integer> priorities);

}
