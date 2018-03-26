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
    public void setUp() throws Exception { }

    private DHCPPool initPool(IPv4Address startIP, int poolSize) {
        return new DHCPPool(startIP, poolSize);
    }

    /* Tests for getLeaseIP() */
    @Test
    public void testGetLeaseIP() throws Exception {
        // Will return the leased IP for the registered client
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);
        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);
        Optional<IPv4Address> leaseIP = dhcpPool.getLeaseIP(MacAddress.of(1));

        assertTrue(leaseIP.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), leaseIP.get());
    }

    @Test
    public void testGetLeaseIPFailsWhenClientNotRegistered() throws Exception {
        // Will return non-present lease IP if client is not registered yet
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);
        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);
        Optional<IPv4Address> leaseIP = dhcpPool.getLeaseIP(MacAddress.of(2));

        assertFalse(leaseIP.isPresent());
    }

    /* Tests for getLeaseBinding() */
    @Test
    public void testGetLeaseBinding() throws Exception {
        // Will return the lease binding for the registered client
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);
        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        Optional<DHCPBinding> binding = dhcpPool.getLeaseBinding((MacAddress.of(1)));

        assertTrue(binding.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), binding.get().getIPv4Address());
        assertEquals(MacAddress.of(1), binding.get().getMACAddress());
    }

    @Test
    public void testGetLeaseBindingFailsWhenClientNotRegistered() throws Exception {
        // Will return non-present lease IP if client is not registered yet
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);
        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        Optional<DHCPBinding> binding = dhcpPool.getLeaseBinding(MacAddress.of(2));

        assertFalse(binding.isPresent());
    }

    /* Tests for assignLeaseToClient() */
    @Test
    public void testAssignLeaseToClient() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);
        
        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        assertTrue(lease.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), lease.get());
    }

    @Test
    public void testAssignLeaseToClientWhenClientRegistered() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);

        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);
        Optional<IPv4Address> lease1 = dhcpPool.assignLeaseToClient(MacAddress.of(1), 10);

        assertEquals(lease, lease1);
    }

    @Test
    public void testAssignLeaseToClientFailsWhenNoAvailableAddresses() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 0);

        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        assertFalse(lease.isPresent());
    }

    /* Tests for assignLeaseToClientWithRequestIP */
    @Test
    public void testAssignLeaseToClientWithRequestIP() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);

        // Will return the request IP if it is available
        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        assertTrue(lease.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), lease.get());
    }

    @Test
    public void testAssignLeaseToClientWithRequestIPNotExist() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 3);

        // Will return the first available IP in DHCP pool
        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.1.10"), MacAddress.of(1), 60, false);

        assertNotEquals(IPv4Address.of("10.0.1.10"), lease.get());
        assertEquals(IPv4Address.of("10.0.0.1"), lease.get());
    }

    @Test
    public void testAssignLeaseToClientWithRequestIPNotAvaliable() throws Exception {
        // DHCP pool is full, will return a non-present Optional IPv4Address
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.2"), MacAddress.of(2), 60, false);

        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(3), 10, false);
        assertFalse(lease.isPresent());

        // Request IP is an static or permanent IP, will return an available IP in the pool
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1));
        Optional<IPv4Address> lease1 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(3), 10, false);

        assertTrue(lease1.isPresent());
        assertEquals(IPv4Address.of("10.0.0.2"), lease1.get());

        // Request IP already leased to another client, will return an available IP in the pool
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        Optional<IPv4Address> lease2 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(3), 10, false);

        assertTrue(lease2.isPresent());
        assertEquals(IPv4Address.of("10.0.0.2"), lease2.get());

    }


    @Test
    public void testAssignLeaseToClientWithRequestIPWhenClientRegistered() throws Exception {
        // Will return the new request IP if it is still available in the pool
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        Optional<IPv4Address> lease1 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.2"), MacAddress.of(1), 10, false);

        assertTrue(lease1.isPresent());
        assertEquals(IPv4Address.of("10.0.0.2"), lease1.get());

        // Will return the IP previously given to client if the new request IP is not available
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.2"), MacAddress.of(2), 60, false);
        Optional<IPv4Address> lease2 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.2"), MacAddress.of(1), 10, false);

        assertTrue(lease2.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), lease2.get());

        // Will return the IP previously given to client if the new request IP is not exist in the pool
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        Optional<IPv4Address> lease3 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.5"), MacAddress.of(1), 60, false);

        assertTrue(lease3.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), lease3.get());

    }

    @Test
    public void testAssignLeaseToClientWithRequestIPWhenClientRegisteredWithStaticIP() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);

        // Static/permanent IP will trump the request IP if client requests again
        Optional<IPv4Address> lease = dhcpPool.assignPermanentLeaseToClient(MacAddress.of(1));
        Optional<IPv4Address> lease1 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);
        Optional<IPv4Address> lease2 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.2"), MacAddress.of(1), 60, false);
        Optional<IPv4Address> lease3 = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.5"), MacAddress.of(1), 60, false);


        assertEquals(lease.get(), lease1.get());
        assertEquals(lease.get(), lease2.get());
        assertEquals(lease.get(), lease3.get());
    }

    @Test
    public void testAssignLeaseToClientWithRequestIPFailsWhenNoAvailableAddress() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 0);
        Optional<IPv4Address> lease = dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 60, false);

        assertFalse(lease.isPresent());
    }

    /* Tests for assignPermanentLeaseToClient() */
    @Test
    public void testAssignPermanentLeaseToClient() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);

        // Will return permanent IP
        Optional<IPv4Address> leaseIP = dhcpPool.assignPermanentLeaseToClient(MacAddress.of(1));
        Optional<DHCPBinding> leaseBinding = dhcpPool.getLeaseBinding(MacAddress.of(1));

        assertTrue(leaseIP.isPresent());
        assertTrue(leaseBinding.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), leaseIP.get());
        assertEquals(LeasingState.PERMANENT_LEASED, leaseBinding.get().getCurrLeaseState());
    }

    @Test
    public void testAssignPermanentLeaseToClientFailsWhenPoolNotAvailable() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 0);

        Optional<IPv4Address> leaseIP = dhcpPool.assignPermanentLeaseToClient(MacAddress.of(1));

        assertFalse(leaseIP.isPresent());
    }

    @Test
    public void testAssignPermanentLeaseToClientWhenClientRegisteredAlready() throws Exception {
        // Will set the previous IP as permanent IP and return it
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClient(MacAddress.of(1), 10);
        Optional<IPv4Address> leaseIP = dhcpPool.assignPermanentLeaseToClient(MacAddress.of(1));
        Optional<DHCPBinding> leaseBinding = dhcpPool.getLeaseBinding(MacAddress.of(1));

        assertTrue(leaseIP.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), leaseIP.get());
        assertEquals(LeasingState.PERMANENT_LEASED, leaseBinding.get().getCurrLeaseState());

        // If previous IP is permanent IP, directly return it
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignPermanentLeaseToClient(MacAddress.of(1));
        Optional<IPv4Address> leaseIP1 = dhcpPool.assignPermanentLeaseToClient(MacAddress.of(1));
        Optional<DHCPBinding> leaseBinding1 = dhcpPool.getLeaseBinding(MacAddress.of(1));

        assertTrue(leaseIP1.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), leaseIP1.get());
        assertEquals(LeasingState.PERMANENT_LEASED, leaseBinding1.get().getCurrLeaseState());
    }

    /* Tests for assignPermanentLeaseToClientWithRequestIP() */
    @Test
    public void testAssignPermanentLeaseToClientWithRequestIP() throws Exception {
        // Request IP "10.0.0.1" as permanent, will return a permanent lease for "10.0.0.1"
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 9);
        Optional<IPv4Address> leaseIP = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"),
                                        MacAddress.of(1));
        Optional<DHCPBinding> leaseBinding = dhcpPool.getLeaseBinding(MacAddress.of(1));

        assertEquals(IPv4Address.of("10.0.0.1"), leaseIP.get());
        assertEquals(LeasingState.PERMANENT_LEASED, leaseBinding.get().getCurrLeaseState());

        // Request IP "10.0.0.9" as permanent, will return a permanent lease for "10.0.0.9"
        Optional<IPv4Address> leaseIP1 = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.9"),
                MacAddress.of(2));
        Optional<DHCPBinding> leaseBinding1 = dhcpPool.getLeaseBinding(MacAddress.of(2));

        assertEquals(IPv4Address.of("10.0.0.9"), leaseIP1.get());
        assertEquals(LeasingState.PERMANENT_LEASED, leaseBinding1.get().getCurrLeaseState());
    }

    @Test
    public void testAssignPermanentLeaseToClientWithRequestIPFailsWhenIPNotValid() throws Exception {
        // DHCP pool is full
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 0);
        Optional<IPv4Address> leaseIP = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"),
                MacAddress.of(1));

        assertFalse(leaseIP.isPresent());

        // Request IP not exists in pool
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        Optional<IPv4Address> leaseIP1 = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.5"),
                MacAddress.of(1));

        assertFalse(leaseIP1.isPresent());

        // Request IP is already in lease for another client
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 10, false);
        Optional<IPv4Address> leaseIP2 = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"),
                MacAddress.of(2));

        assertFalse(leaseIP2.isPresent());

        // Client registered w/ IP address same as request IP address, should update same IP states to permanent
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 10, false);
        Optional<IPv4Address> leaseIP3 = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"),
                MacAddress.of(1));

        assertTrue(leaseIP3.isPresent());
        assertEquals(IPv4Address.of("10.0.0.1"), leaseIP3.get());
        assertEquals(LeasingState.PERMANENT_LEASED, dhcpPool.getLeaseBinding(MacAddress.of(1)).get().getCurrLeaseState());

        // Client registered w/ different IP address, should return original address to repository and assign request IP as permanent
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 2);
        dhcpPool.assignLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1), 10, false);
        Optional<IPv4Address> leaseIP4 = dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.2"),
                MacAddress.of(1));

        assertTrue(leaseIP4.isPresent());
        assertEquals(IPv4Address.of("10.0.0.2"), leaseIP4.get());
        assertEquals(LeasingState.PERMANENT_LEASED, dhcpPool.getLeaseBinding(MacAddress.of(1)).get().getCurrLeaseState());
        assertEquals(1, dhcpPool.getLeasingPoolSize());
        assertEquals(1, dhcpPool.getRepositorySize());

    }

    /* Tests for cancelLeaseOfMac() */
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

    /* Tests for renewLeaseOfMac() */
    @Test
    public void testRenewLease() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);

        dhcpPool.assignLeaseToClient(MacAddress.of(1), 0);
        dhcpPool.checkExpiredLeases();

        assertEquals(LeasingState.EXPIRED, dhcpPool.getLeaseBinding(MacAddress.of(1)).get().getCurrLeaseState());

        assertTrue(dhcpPool.renewLeaseOfMAC(MacAddress.of(1), 60));
        assertEquals(LeasingState.LEASED, dhcpPool.getLeaseBinding(MacAddress.of(1)).get().getCurrLeaseState());
    }

    @Test
    public void testRenewLeaseFailsWhenClientNotRegistered() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);

        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        assertFalse(dhcpPool.renewLeaseOfMAC(MacAddress.of(2), 60));
    }

    @Test
    public void testRenewLeaseFailsWhenLeaseIsPermanent() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);

        dhcpPool.assignPermanentLeaseToClientWithRequestIP(IPv4Address.of("10.0.0.1"), MacAddress.of(1));

        assertFalse(dhcpPool.renewLeaseOfMAC(MacAddress.of(1), 60));
    }

    @Test
    public void testRenewLeaseWhenLeaseStillAlive() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 1);

        dhcpPool.assignLeaseToClient(MacAddress.of(1), 60);

        assertTrue(dhcpPool.renewLeaseOfMAC(MacAddress.of(1), 60));
    }


}
