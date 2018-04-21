package net.floodlightcontroller.forwarding;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * This test class bundles all Forwrding/Routing tests and run them all at once
 *
 * @author Qing Wang (qw@g.clemson.edu) at 4/20/18
 */
@RunWith(Suite.class)

@Suite.SuiteClasses({
        L3RoutingTest.class,
        ForwardingTest.class
})

public class ForwardingTestSuit {
    // This class remains empty
    // Used only as a holder for the above annotations
}
