/**
 * Tulio Alberton Ribeiro
 * 
 * LaSIGE - Large-Scale Informatics Systems Laboratory
 * 
 * 03/2016
 * 
 * Without warrant
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 */

package org.sdnplatform.sync.internal.rpc;

public interface IRPCListener {
	
	/**
	 * Inform to listeners when a node is disconnected.
	 * @param nodeId
	 */
	public void disconnectedNode(Short nodeId);
	
	/**
	 * Inform to listeners when a node is connected.
	 * @param nodeId
	 */
	public void connectedNode(Short nodeId);
}
