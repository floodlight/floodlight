package net.floodlightcontroller.hasupport;

import java.util.List;

/**
*
* @author Bhargav Srinivasan, Om Kale
*
*/

public interface ISyncAdapter {
	
	public void packJSON(List<String> updates);
	
	public void unpackJSON(String controllerID);

}
