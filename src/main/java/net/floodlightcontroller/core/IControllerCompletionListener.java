package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFMessage;

public interface IControllerCompletionListener {

	/**
	 * This mimics the behaviour of the IOFMessageListener. Will be called at the end of the message processing loop
	 * Modules implementing this interface will know when the message processing queue has digested an input event
	 * 
	 * @param sw
	 * @param msg
	 * @param cntx
	 *
	 */
	public void onMessageConsumed(IOFSwitch sw, OFMessage msg, FloodlightContext cntx);

	public String getName();
	
}
