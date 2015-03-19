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

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.accesscontrollist.ap.AP;
import net.floodlightcontroller.accesscontrollist.ap.APManager;
import net.floodlightcontroller.accesscontrollist.util.IPAddressUtil;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Test;

public class APManagerTest extends FloodlightTestCase {
	
	@Test
	public void testGetDpidSet(){
		
		AP ap1 = new AP("10.0.0.1","00:00:00:00:00:00:00:01");
		AP ap2 = new AP("10.0.0.2","00:00:00:00:00:00:00:02");
		AP ap3 = new AP("10.0.0.3","00:00:00:00:00:00:00:03");
		
		APManager apManager = new APManager();
		apManager.addAP(ap1);
		apManager.addAP(ap2);
		apManager.addAP(ap3);
		
		int cidr[];
		
		// test CIDR IP with suffix that equals "/32"
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		Set<String> resultSet = apManager.getDpidSet(cidr[0],cidr[1]);
		Set<String> expectedSet = new HashSet<String>();
		expectedSet.add("00:00:00:00:00:00:00:01");
		assertEquals(resultSet, expectedSet);
		
		// test CIDR IP with suffix that does not equal "/32"
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/30");
		resultSet = apManager.getDpidSet(cidr[0],cidr[1]);
		expectedSet = new HashSet<String>();
		expectedSet.add("00:00:00:00:00:00:00:01");
		expectedSet.add("00:00:00:00:00:00:00:02");
		expectedSet.add("00:00:00:00:00:00:00:03");
		assertEquals(resultSet, expectedSet);
		
		// test CIDR IP does not exist in the network
		cidr = IPAddressUtil.parseCIDR("10.0.0.4/32");
		resultSet = apManager.getDpidSet(cidr[0],cidr[1]);
		expectedSet = new HashSet<String>();
		assertEquals(resultSet, expectedSet);
		
	}
}
