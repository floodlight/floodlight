package net.floodlightcontroller.hasupport;

import java.util.List;

/**
* ISyncAdapter
* 
* This interface specifies the methods used to push to and
* retrieve updates from the syncDB. packJSON is called by
* FilterQueue's dequeueForward method and unpackJSON is called
* by FilterQueue's subscribe method. unpackJSON retrieves 
* the updates and calls Filter Queue's enqueueReverse() in order
* to later pass them on to the subscribeHook().
*
* @author Bhargav Srinivasan, Om Kale
*
*/

public interface ISyncAdapter {
	
	public void packJSON(List<String> updates);
	
	public void unpackJSON(String controllerID);

}
