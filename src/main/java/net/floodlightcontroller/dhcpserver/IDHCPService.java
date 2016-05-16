package net.floodlightcontroller.dhcpserver;

import java.util.Collection;

import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;

public interface IDHCPService extends IFloodlightService {

	public void enable();
	public void disable();
	public boolean isEnabled();
	
	public boolean addInstance(DHCPInstance instance);
	
	public DHCPInstance getInstance(String name);
	public DHCPInstance getInstance(NodePortTuple member);
	public DHCPInstance getInstance(VlanVid member);
	public Collection<DHCPInstance> getInstances();
	
	public boolean deleteInstance(String name);
}
