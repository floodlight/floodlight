package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.test.FloodlightTestCase;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/27/17
 */
public class DHCPBindingTest extends FloodlightTestCase {
    private IPv4Address ip;
    private MacAddress mac;
    private DHCPBinding binding;


    @Before
    @Override
    public void setUp() throws Exception {
        ip = IPv4Address.of("192.168.1.1");
        mac = MacAddress.of("00:A0:CC:23:AF:AA");
        binding = new DHCPBinding(ip, mac);
    }

    @Test
    public void testLeaseNotExpired() throws Exception {
        binding.setLeaseDuration(1000);
        assertEquals(false, binding.isBindingTimeout());
    }

    @Test
    public void testLeaseDoesExpired() throws Exception {
        binding.setLeaseDuration(0L);
        assertEquals(true, binding.isBindingTimeout());
    }

    @Test
    public void testCancelLease() throws Exception {
        binding.setLeaseDuration(100);
        binding.cancelLease();

        assertEquals(LeasingState.AVAILABLE, binding.getCurrLeaseState());
    }

    @Test
    public void testRenewLease() throws Exception {
        binding.setLeaseDuration(0);

        assertEquals(true, binding.isBindingTimeout());

        binding.renewLease(100);

        assertEquals(false, binding.isBindingTimeout());
        assertEquals(LeasingState.LEASED, binding.getCurrLeaseState());
    }

    @Test
    public void testConfigureNormalLease() throws Exception {
        assertEquals(LeasingState.AVAILABLE, binding.getCurrLeaseState());
        binding.configureNormalLease(MacAddress.of("00:00:00:00:00:AA"), 100);

        assertEquals(LeasingState.LEASED, binding.getCurrLeaseState());
        assertEquals(MacAddress.of("00:00:00:00:00:AA"), binding.getMACAddress());
    }

    @Test
    public void testConfigurePermanentLease() throws Exception {
        assertEquals(LeasingState.AVAILABLE, binding.getCurrLeaseState());
        binding.configurePermanentLease(MacAddress.of("00:00:00:00:00:BB"));

        assertEquals(LeasingState.PERMANENT_LEASED, binding.getCurrLeaseState());
        assertEquals(MacAddress.of("00:00:00:00:00:BB"), binding.getMACAddress());

    }

    @Test
    public void testCheckForTimeOut() throws Exception {
        binding.setLeaseDuration(0);
        assertTrue(binding.isBindingTimeout());

        binding.setLeaseDuration(100);
        assertFalse(binding.isBindingTimeout());
    }

}
