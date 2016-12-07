package net.floodlightcontroller.hasupport;

import java.util.ArrayList;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * IHAControllerService
 * Exposes two of the HAController features to the user, 
 * first is to getLeader() which gets the current network-wide
 * leader and the second function can be used to set pre-defined
 * priorities for the election, by supplying an ordered list of 
 * Integers which hold the order in which the nodes are to be 
 * selected in the election process.
 * This service can be used by any module, as it is exposed using
 * IFloodlightService.
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface IHAControllerService extends IFloodlightService {
	
	public String getLeader();
	
	public void setElectionPriorities(ArrayList<Integer> priorities);

}
