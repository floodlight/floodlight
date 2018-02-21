package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.test.FloodlightTestCase;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/1/18
 */
public class DHCPPoolTest extends FloodlightTestCase {
    private DHCPPool dhcpPool;
    
    @Before
    @Override
    public void setUp() throws Exception {
//        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 3);
    }

    private DHCPPool initPool(IPv4Address startIP, int poolSize) {
        return new DHCPPool(startIP, poolSize);
    }

    @Test
    public void testAssignLeaseToClient() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);
        
        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);
        
        assertTrue(lease.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), lease.get());
    }
    
    @Test
    public void testAssignLeaseToClientFailsWhenNoAvailableAddresses() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 0);

        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        assertFalse(lease.isPresent());
    }
    
    @Test
    public void testLeaseIsRemovedFromPool() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);

        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);
        Optional<IPv4Address> lease = dhcpPool.getLeaseIP(MacAddress.of(1));
        
        assertTrue(lease.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), lease.get());

        dhcpPool.cancelLeaseOfMac(MacAddress.of(1));
        lease = dhcpPool.getLeaseIP(MacAddress.of(1));

        assertFalse(lease.isPresent());
    }
}
