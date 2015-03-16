package net.floodlightcontroller.accesscontrollist.test;

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
