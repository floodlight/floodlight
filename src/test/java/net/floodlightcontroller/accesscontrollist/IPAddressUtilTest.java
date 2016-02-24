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
import net.floodlightcontroller.accesscontrollist.util.IPAddressUtil;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Test;

public class IPAddressUtilTest extends FloodlightTestCase {
	
	@Test
	public void testParseCIDR(){
		
		String cidr = "10.0.0.1/32";
		int[] resultArray = IPAddressUtil.parseCIDR(cidr);
		assertEquals(resultArray[0],IPv4.toIPv4Address("10.0.0.1"));
		assertEquals(resultArray[1],32);
	}
	
	@Test
	public void testContainIP(){
		
		int[] cidr = IPAddressUtil.parseCIDR("10.0.0.0/8");
		int ip = IPv4.toIPv4Address("10.0.0.1");
		assertTrue(IPAddressUtil.containIP(cidr[0], cidr[1], ip));
	}
	
	@Test
	public void testIsSubnet(){
		
		assertFalse(IPAddressUtil.isSubnet("10.0.0.1/32", "10.0.0.2/32"));
		assertTrue(IPAddressUtil.isSubnet("10.0.0.1/8", "10.0.0.2/8"));
		assertTrue(IPAddressUtil.isSubnet("10.0.0.1/32", "10.0.0.2/8"));
		assertFalse(IPAddressUtil.isSubnet("10.0.0.1/8", "10.0.0.2/32"));
		assertTrue(IPAddressUtil.isSubnet("10.0.0.1/8", null));
		assertFalse(IPAddressUtil.isSubnet(null, "10.0.0.2/32"));
	}

}
