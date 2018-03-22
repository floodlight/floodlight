/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.loadbalancer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */

@JsonSerialize(using=LBStatsSerializer.class)
public class LBStats {
    protected long bytesIn;
    protected long bytesOut;
	protected int activeFlows;
    protected int totalFlows;
    
    public LBStats() {
        bytesIn = 0;
        bytesOut = 0;
        activeFlows = 0;
        totalFlows = 0;
    }
    
    public int getActiveFlows() {
		return activeFlows;
	}

	public void setActiveFlows(int activeFlows) {
		this.activeFlows = activeFlows;
	}

	public int getTotalFlows() {
		return totalFlows;
	}

	public void setTotalFlows(int totalFlows) {
		this.totalFlows = totalFlows;
	}

	public LBStats getStats(){
		return this;
	}
    
    public long getBytesIn() {
		return bytesIn;
	}

	public void setBytesIn(int bytesIn) {
		this.bytesIn = bytesIn;
	}

	public long getBytesOut() {
		return bytesOut;
	}

	public void setBytesOut(int bytesOut) {
		this.bytesOut = bytesOut;
	}

}