package net.floodlightcontroller.dhcpserver;

import com.google.common.collect.Sets;
import net.floodlightcontroller.core.test.PacketFactory;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.test.FloodlightTestCase;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 2/22/18
 */
public class DHCPInstanceTest extends FloodlightTestCase {
    private DHCPInstance instance;

    @Before
    @Override
    public void setUp() throws Exception { }


    @Test
    public void testBuildDHCPInstance() throws Exception {
        Map<MacAddress, IPv4Address> returnStaticAddresses = new HashMap<>();
        returnStaticAddresses.put(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("192.168.1.3"));
        returnStaticAddresses.put(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("192.168.1.5"));

        DHCPPool returnDHCPPool = new DHCPPool(IPv4Address.of("192.168.1.3"), 8);

        /* Create DHCP Instance */
        DHCPInstance instance = DHCPInstance.createInstance("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setBroadcastIP(IPv4Address.of("192.168.1.255"))
                .setRouterIP(IPv4Address.of("192.168.1.1"))
                .setSubnetMask(IPv4Address.of("255.255.255.0"))
                .setStartIP(IPv4Address.of("192.168.1.3"))
                .setEndIP(IPv4Address.of("192.168.1.10"))
                .setLeaseTimeSec(10)
                .setDNSServers(Arrays.asList(IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))
                .setNTPServers(Arrays.asList(IPv4Address.of("10.0.0.3"), IPv4Address.of("10.0.0.4")))
                .setIPforwarding(true)
                .setDomainName("testDomainName")
                .setStaticAddresses(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("192.168.1.3"))
                .setStaticAddresses(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("192.168.1.5"))
                .setClientMembers(Sets.newHashSet(MacAddress.of("00:11:22:33:44:55"), MacAddress.of("55:44:33:22:11:00")))
                .setVlanMembers(Sets.newHashSet(VlanVid.ofVlan(100), VlanVid.ofVlan(200)))
                .setNptMembers(Sets.newHashSet(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)), new NodePortTuple(DatapathId.of(2L), OFPort.of(2))))
                .build();


        assertNotNull(instance);
        assertNotNull(instance.getName());
        assertNotNull(instance.getDHCPPool());
        assertEquals("dhcpTestInstance", instance.getName());
        assertEquals(IPv4Address.of("192.168.1.2"), instance.getServerID());
        assertEquals(MacAddress.of("aa:bb:cc:dd:ee:ff"), instance.getServerMac());
        assertEquals(IPv4Address.of("192.168.1.255"), instance.getBroadcastIP());
        assertEquals(IPv4Address.of("192.168.1.1"), instance.getRouterIP());
        assertEquals(IPv4Address.of("255.255.255.0"), instance.getSubnetMask());
        assertEquals(IPv4Address.of("192.168.1.3"), instance.getStartIPAddress());
        assertEquals(IPv4Address.of("192.168.1.10"), instance.getEndIPAddress());
        assertEquals(10, instance.getLeaseTimeSec());
        assertEquals((int)(10*0.875), instance.getRebindTimeSec());
        assertEquals((int)(10*0.5), instance.getRenewalTimeSec());
        assertEquals(Arrays.asList(IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")), instance.getDNSServers());
        assertEquals(Arrays.asList(IPv4Address.of("10.0.0.3"), IPv4Address.of("10.0.0.4")), instance.getNtpServers());
        assertEquals(true, instance.getIpforwarding());
        assertEquals("testDomainName", instance.getDomainName());
        assertEquals(returnStaticAddresses, instance.getStaticAddresseses());
        assertEquals(Sets.newHashSet(MacAddress.of("00:11:22:33:44:55"), MacAddress.of("55:44:33:22:11:00")), instance.getClientMembers());
        assertEquals(Sets.newHashSet(VlanVid.ofVlan(100), VlanVid.ofVlan(200)), instance.getVlanMembers());
        assertEquals(Sets.newHashSet(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)), new NodePortTuple(DatapathId.of(2L), OFPort.of(2))), instance.getNptMembers());

    }

    @Test
    public void testConfigureStaticAddresses() throws Exception {
        /* Expected valid static address */
        Map<MacAddress, IPv4Address> expectStaticAddresses = new HashMap<>();
        expectStaticAddresses.put(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("192.168.1.3"));
        expectStaticAddresses.put(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("192.168.1.5"));

        /* valid static IP address as request */
        DHCPInstance instance1 = DHCPInstance.createInstance("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setStartIP(IPv4Address.of("192.168.1.3"))
                .setEndIP(IPv4Address.of("192.168.1.10"))
                .setLeaseTimeSec(10)
                .setStaticAddresses(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("192.168.1.3"))
                .setStaticAddresses(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("192.168.1.5"))
                .build();
        assertEquals(expectStaticAddresses, instance1.getStaticAddresseses());


        /* invalid static IP address request should return null */
        DHCPInstance instance2 = DHCPInstance.createInstance("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setStartIP(IPv4Address.of("192.168.1.3"))
                .setEndIP(IPv4Address.of("192.168.1.10"))
                .setLeaseTimeSec(10)
                .setStaticAddresses(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("20.20.20.20"))
                .setStaticAddresses(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("30.30.30.30"))
                .build();
        assertTrue(instance2.getStaticAddresseses().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildInstanceWithMissingFields() throws Exception {
        DHCPInstance instance = DHCPInstance.createInstance("dhcpTestInstance")
                .setLeaseTimeSec(10)
                .build();

    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildInstanceWithIncorrectStartIPAndEndIP() throws Exception {
        IPv4Address startIP = IPv4Address.of("10.0.0.5");
        IPv4Address endIP = IPv4Address.of("10.0.0.1");

        DHCPInstance instance = DHCPInstance.createInstance("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setBroadcastIP(IPv4Address.of("192.168.1.255"))
                .setRouterIP(IPv4Address.of("192.168.1.1"))
                .setSubnetMask(IPv4Address.of("255.255.255.0"))
                .setStartIP(startIP)
                .setEndIP(endIP)
                .setLeaseTimeSec(10)
                .build();

    }

    @Test
    public void testBuildInstanceWithStaticAddressesNotValid() throws Exception {
        DHCPInstance instance = DHCPInstance.createInstance("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setBroadcastIP(IPv4Address.of("192.168.1.255"))
                .setRouterIP(IPv4Address.of("192.168.1.1"))
                .setSubnetMask(IPv4Address.of("255.255.255.0"))
                .setStartIP(IPv4Address.of("192.168.1.3"))
                .setEndIP(IPv4Address.of("192.168.1.10"))
                .setLeaseTimeSec(10)
                .setDNSServers(Arrays.asList(IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))
                .setNTPServers(Arrays.asList(IPv4Address.of("10.0.0.3"), IPv4Address.of("10.0.0.4")))
                .setIPforwarding(true)
                .setDomainName("testDomainName")
                .setStaticAddresses(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("10.0.0.1"))
                .setStaticAddresses(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("10.0.0.2"))
                .setClientMembers(Sets.newHashSet(MacAddress.of("00:11:22:33:44:55"), MacAddress.of("55:44:33:22:11:00")))
                .setVlanMembers(Sets.newHashSet(VlanVid.ofVlan(100), VlanVid.ofVlan(200)))
                .setNptMembers(Sets.newHashSet(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)), new NodePortTuple(DatapathId.of(2L), OFPort.of(2))))
                .build();


        // Will return size = 0 if try to set static address but none of them are valid
        assertEquals(0, instance.getStaticAddresseses().size());


        DHCPInstance instance1 = DHCPInstance.createInstance("dhcpTestInstance1")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setBroadcastIP(IPv4Address.of("192.168.1.255"))
                .setRouterIP(IPv4Address.of("192.168.1.1"))
                .setSubnetMask(IPv4Address.of("255.255.255.0"))
                .setStartIP(IPv4Address.of("192.168.1.3"))
                .setEndIP(IPv4Address.of("192.168.1.10"))
                .setLeaseTimeSec(10)
                .setDNSServers(Arrays.asList(IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))
                .setNTPServers(Arrays.asList(IPv4Address.of("10.0.0.3"), IPv4Address.of("10.0.0.4")))
                .setIPforwarding(true)
                .setDomainName("testDomainName")
                .setClientMembers(Sets.newHashSet(MacAddress.of("00:11:22:33:44:55"), MacAddress.of("55:44:33:22:11:00")))
                .setVlanMembers(Sets.newHashSet(VlanVid.ofVlan(100), VlanVid.ofVlan(200)))
                .setNptMembers(Sets.newHashSet(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)), new NodePortTuple(DatapathId.of(2L), OFPort.of(2))))
                .build();

        // Will also return "null" if static address never been set
        assertEquals(0, instance1.getStaticAddresseses().size());

    }

    @Test
    public void testUpdateInstance() throws Exception {
        DHCPInstance instance = DHCPInstance.createInstance("dhcpTestInstance")
                .setServerID(IPv4Address.of("192.168.1.2"))
                .setServerMac(MacAddress.of("aa:bb:cc:dd:ee:ff"))
                .setBroadcastIP(IPv4Address.of("192.168.1.255"))
                .setRouterIP(IPv4Address.of("192.168.1.1"))
                .setSubnetMask(IPv4Address.of("255.255.255.0"))
                .setStartIP(IPv4Address.of("192.168.1.3"))
                .setEndIP(IPv4Address.of("192.168.1.10"))
                .setLeaseTimeSec(10)
                .setDNSServers(Arrays.asList(IPv4Address.of("10.0.0.1"), IPv4Address.of("10.0.0.2")))
                .setNTPServers(Arrays.asList(IPv4Address.of("10.0.0.3"), IPv4Address.of("10.0.0.4")))
                .setIPforwarding(true)
                .setDomainName("testDomainName")
                .setStaticAddresses(MacAddress.of("44:55:66:77:88:99"), IPv4Address.of("10.0.0.1"))
                .setStaticAddresses(MacAddress.of("99:88:77:66:55:44"), IPv4Address.of("10.0.0.2"))
                .setClientMembers(Sets.newHashSet(MacAddress.of("00:11:22:33:44:55"), MacAddress.of("55:44:33:22:11:00")))
                .setVlanMembers(Sets.newHashSet(VlanVid.ofVlan(100), VlanVid.ofVlan(200)))
                .setNptMembers(Sets.newHashSet(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)), new NodePortTuple(DatapathId.of(2L), OFPort.of(2))))
                .build();

        instance = instance.getBuilder().setServerID(IPv4Address.of("192.168.1.4")).build();
        instance = instance.getBuilder().setRouterIP(IPv4Address.of("192.168.1.3")).build();

        assertEquals(IPv4Address.of("192.168.1.4"), instance.getServerID());
        assertEquals(IPv4Address.of("192.168.1.3"), instance.getRouterIP());

    }

}
