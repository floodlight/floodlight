package net.floodlightcontroller.hasupport;


import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightModule;

public interface IHAWorker extends IFloodlightModule {
	
	public List<String> assembleUpdate();
	
	public boolean publishHook();
	
	public boolean subscribeHook(String controllerID);
	
	

}
