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

import java.util.Collection;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface ILoadBalancerService extends IFloodlightService {

    /**
     * List all current Vips.
     */
    public Collection<LBVip> listVips();

    /**
     * List selected Vip by its ID.
     * @param vipId Id of requested Vip
     */
    public Collection<LBVip> listVip(String vipId);

    /**
     * Create and return a new Vip.
     * @param LBVip vip: data structure with caller provided Vip attributes 
     * @return LBVip: Created Vip 
     */
    public LBVip createVip(LBVip vip);

    /**
     * Update and return an existing Vip.
     * @param LBVip vip: data structure with caller provided Vip attributes 
     * @return LBVip: Updated Vip 
     */
    public LBVip updateVip(LBVip vip);

    /**
     * Remove an existing Vip.
     * @param String vipId  
     * @return int: removal status 
     */
    public int removeVip(String vipId);

    /**
     * List all current pools.
     */
    public Collection<LBPool> listPools();

    /**
     * List selected pool by its ID.
     * @param poolId Id of requested pool
     */
    public Collection<LBPool> listPool(String poolId);

    /**
     * Create and return a new pool.
     * @param LBPool pool: data structure with caller provided pool attributes 
     * @return LBPool: Created pool 
     */
    public LBPool createPool(LBPool pool);

    /**
     * Update and return an existing pool.
     * @param LBPool pool: data structure with caller provided pool attributes 
     * @return LBPool: Updated pool 
     */
    public LBPool updatePool(LBPool pool);

    /**
     * Remove an existing pool.
     * @param String poolId 
     * @return int: removal status 
     */
    public int removePool(String poolId);
    
    /**
     * List all current members.
     */
    public Collection<LBMember> listMembers();

    /**
     * List selected member by its ID.
     * @param memberId Id of requested member
     */
    public Collection<LBMember> listMember(String memberId);

    /**
     * List all members in a specified pool.
     */
    public Collection<LBMember> listMembersByPool(String poolId);
    
    /**
     * Create and return a new member.
     * @param LBMember member: data structure with caller provided member attributes 
     * @return LBMember: Created member 
     */
    public LBMember createMember(LBMember member);

    /**
     * Update and return an existing member.
     * @param LBMember member: data structure with caller provided member attributes 
     * @return LBMember: Updated member 
     */
    public LBMember updateMember(LBMember member);

    /**
     * Remove an existing member.
     * @param String memberId 
     * @return int: removal status 
     */
    public int removeMember(String memberId);
    
    /**
     * List all current monitors.
     */
    public Collection<LBMonitor> listMonitors();

    /**
     * List selected monitor by its ID.
     * @param monitorId Id of requested monitor
     */
    public Collection<LBMonitor> listMonitor(String monitorId);

    /**
     * Create and return a new monitor.
     * @param LBMonitor monitor: data structure with caller provided monitor attributes 
     * @return LBMonitor: Created monitor 
     */
    public LBMonitor createMonitor(LBMonitor monitor);

    /**
     * Update and return an existing monitor.
     * @param LBMonitor monitor: data structure with caller provided pool attributes 
     * @return LBMonitor: Updated monitor 
     */
    public LBMonitor updateMonitor(LBMonitor monitor);

    /**
     * Remove an existing monitor.
     * @param String monitorId 
     * @return int: removal status 
     */
    public int removeMonitor(String monitorId);
    
    /**
     * Set member weight for WRR algorithm.
     * @param String memberId: the Id of the member
     * @param String weight: the weight to use in the WRR
     * @return int: removal status
     */
	public int setMemberWeight(String memberId, String weight);

	public int setPriorityMember(String poolId, String memberId);
    
    /**
     * Create and return a new L7Rule.
     * @param L7Rule l7_rule: data structure with caller provided L7Rule attributes 
     * @return L7Rule: Created L7Rule 
     */
    

    /**
     * Update an existing L7Rule.
     * @param L7Rule l7_rule: data structure with caller provided L7Rule attributes 
     * @return L7Rule: Created L7Rule 
     */
    
    
    /**
     * List all current L7Rules.
     */
   

    /**
     * List selected L7Rule by its ID.
     * @param ruleId Id of requested L7Rule
     */
   
    
    /**
     * Remove an existing L7Rule.
     * @param String ruleId 
     * @return int: removal status 
     */
    
    /**
     * Create and return a new L7Policy.
     * @param L7Policy l7_policy: data structure with caller provided L7Policy attributes 
     * @return L7Policy: Created L7Policy 
     */


    /**
     * Update an existing L7Policy.
     * @param L7Policy l7_policy: data structure with caller provided L7Policy attributes 
     * @return L7Policy: Created L7Policy 
     */
  
    
    /**
     * List all current L7Policies.
     */
   

    /**
     * List selected L7Policy by its ID.
     * @param policyId Id of requested L7Policy
     */
   
    
    /**
     * Remove an existing L7Policy.
     * @param String policyId 
     * @return int: removal status 
     */
   
    
    

}
