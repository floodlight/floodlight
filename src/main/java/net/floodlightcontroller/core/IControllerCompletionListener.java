package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFMessage;

/**
 * Copyright (c) 2014, NetIDE Consortium (Create-Net (CN), Telefonica Investigacion Y Desarrollo SA (TID), Fujitsu 
 * Technology Solutions GmbH (FTS), Thales Communications & Security SAS (THALES), Fundacion Imdea Networks (IMDEA),
 * Universitaet Paderborn (UPB), Intel Research & Innovation Ireland Ltd (IRIIL), Fraunhofer-Institut für 
 * Produktionstechnologie (IPT), Telcaria Ideas SL (TELCA) )
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors:
 *     Pedro A. Aranda Gutiérrez, pedroa.aranda@telefonica.com
 */


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
