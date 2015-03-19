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
import net.floodlightcontroller.accesscontrollist.ACLRule;
import net.floodlightcontroller.accesscontrollist.util.IPAddressUtil;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Test;

public class ACLRuleTest extends FloodlightTestCase{
	
	@Test
	public void testMatch(){
		
		int[] cidr = new int[2];
		ACLRule rule1, rule2;
		
		// rule1 & rule2 are the same
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(6);
		rule2.setTp_dst(80);
		assertTrue(rule1.match(rule2));
		
		// rule1 & rule2 are different in nw_proto
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(11);
		assertFalse(rule1.match(rule2));
		
		// rule1's nw_src is a subnet of rule2's nw_src
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(1);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/8");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(1);
		assertTrue(rule1.match(rule2));
		
		// rule1's nw_dst is a subnet of rule2's nw_dst
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(1);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/8");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(1);
		assertTrue(rule1.match(rule2));
		
		// rule1's nw_src is specified while rule2's is not
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		assertTrue(rule1.match(rule2));
		
		// rule1's nw_dst is specified while rule2's is not
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		assertTrue(rule1.match(rule2));
		
		// rule1's nw_proto is specified while rule2's is not
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		assertTrue(rule1.match(rule2));
		
		// rule1's tp_dst is specified while rule2's is not
		rule1 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule1.setNw_src_prefix(cidr[0]);
		rule1.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule1.setNw_dst_prefix(cidr[0]);
		rule1.setNw_dst_maskbits(cidr[1]);
		rule1.setNw_proto(6);
		rule1.setTp_dst(80);
		rule2 = new ACLRule();
		cidr = IPAddressUtil.parseCIDR("10.0.0.1/32");
		rule2.setNw_src_prefix(cidr[0]);
		rule2.setNw_src_maskbits(cidr[1]);
		cidr = IPAddressUtil.parseCIDR("10.0.0.2/32");
		rule2.setNw_dst_prefix(cidr[0]);
		rule2.setNw_dst_maskbits(cidr[1]);
		rule2.setNw_proto(6);
		assertTrue(rule1.match(rule2));
	}
}
