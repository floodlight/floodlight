package net.floodlightcontroller.accesscontrollist.test;

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
