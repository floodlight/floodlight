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


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.U64;
import org.sdnplatform.sync.internal.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.loadbalancer.LoadBalancer.IPClient;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

/**
 * Data structure for Load Balancer based on
 * Quantum proposal http://wiki.openstack.org/LBaaS/CoreResourceModel/proposal 
 * 
 * @author KC Wang
 */


@JsonSerialize(using=LBPoolSerializer.class)
public class LBPool {
	protected static Logger log = LoggerFactory.getLogger(LBPool.class);
	protected String id;
	protected String name;
	protected String tenantId;
	protected String netId;
	protected short lbMethod;
	protected byte protocol;
	protected ArrayList<String> members;
	protected ArrayList<String> monitors;
	protected short adminState;
	protected short status;
	protected final static short STATISTICS = 2;
	protected final static short ROUND_ROBIN = 1;

	protected String vipId;

	protected int previousMemberIndex;

	public LBPool() {
		id = String.valueOf((int) (Math.random()*10000));
		name = null;
		tenantId = null;
		netId = null;
		lbMethod = 0;
		protocol = 0;
		members = new ArrayList<String>();
		monitors = new ArrayList<String>();
		adminState = 0;
		status = 0;
		previousMemberIndex = -1;
	}

	public String pickMember(IPClient client, HashMap<String,U64> membersbandwidth) {

		// Get the members that belong to this pool and the statistics for them
		if(members.size() > 0){
			if (lbMethod == STATISTICS && membersbandwidth.values() !=null) {	
				ArrayList<String> memb = new ArrayList<String>();
				for(String memberId: membersbandwidth.keySet()){
					for(int i=0;i<members.size();i++){
						if(members.get(i).equals(memberId)){
							memb.add(memberId);					

							log.info("MEMBER OF THIS POOL: {}", memberId);
							log.info("RecievedX: {}",membersbandwidth.get(memberId).getBigInteger());

						}
					}
				}
				// return the member which has the minimum bandwidth usage, out of this pool members
				if(!memb.isEmpty()){
					ArrayList<U64> vals = new ArrayList<U64>();

					for(int j=0;j<memb.size();j++){
						vals.add(membersbandwidth.get(memb.get(j)));
					}
					log.info("Members {}", memb);
					log.info("VALS {}", vals);
					log.info("MEMBER PICKED {}", memb.get(vals.indexOf(Collections.min(vals))));

					return memb.get(vals.indexOf(Collections.min(vals)));
				}
				// simple round robin
			} else if(lbMethod == ROUND_ROBIN || lbMethod == 0){
				log.info("ROUND_ROBIN");

				previousMemberIndex = (previousMemberIndex + 1) % members.size();
				return members.get(previousMemberIndex);
			}
		}
		return null;
	}
}
