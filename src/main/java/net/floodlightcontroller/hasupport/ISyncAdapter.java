package net.floodlightcontroller.hasupport;

import java.util.List;

public interface ISyncAdapter {
	
	public void packJSON(List<String> updates);
	
	public void unpackJSON(String controllerID);

}
