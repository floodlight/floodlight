package net.floodlightcontroller.dhcpserver;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * This Class bundles all DHCP tests and run them all at once
 *
 * @author Qing Wang (qw@g.clemson.edu) at 3/3/18
 */

@RunWith(Suite.class)

@Suite.SuiteClasses({
        DHCPBindingTest.class,
        DHCPPoolTest.class,
        DHCPInstanceTest.class,
        DHCPMessageHandlerTest.class

})

public class DHCPTestSuite {
    // This class remains empty
    // Used only as a holder for the above annotations

}
