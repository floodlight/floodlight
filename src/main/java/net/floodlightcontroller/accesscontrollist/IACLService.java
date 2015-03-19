/**
 *    Copyright 2015, Big Switch Networks, Inc.
 *    Originally created by Pengfei Lu, Network and Cloud Computing Laboratory, Dalian University of Technology, China 
 *    Advisers: Keqiu Li and Heng Qi 
 *    This work is supported by the State Key Program of National Natural Science of China(Grant No. 61432002) 
 *    and Prospective Research Project on Future Networks in Jiangsu Future Networks Innovation Institute.
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

package net.floodlightcontroller.accesscontrollist;

import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IACLService extends IFloodlightService {
	
    public List<ACLRule> getRules();
    
    /**
     * add a new ACL rule
     * @param rule
     * @return whether the rule is added successfully
     */
    public boolean addRule(ACLRule rule);

    public void removeRule(int ruleid);
    
    public void removeAllRules();

}
