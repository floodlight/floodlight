package net.floodlightcontroller.dhcpserver;

import net.floodlightcontroller.test.FloodlightTestCase;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.IPv4Address;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/1/18
 */
public class DHCPPoolTest extends FloodlightTestCase {
    private DHCPPool dhcpPool;


    @Before
    @Override
    public void setUp() throws Exception {
        dhcpPool = initPool(IPv4Address.of("10.0.0.1"), 3);
    }

    private DHCPPool initPool(IPv4Address startIP, int poolSize) {
        return new DHCPPool(startIP, poolSize);
    }

    @Test
    public void testDisplayPool() throws Exception {
        dhcpPool.displayDHCPPool();
    }

}
