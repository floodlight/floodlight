package net.floodlightcontroller.core.util;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionTest {
    private static final Logger log = LoggerFactory.getLogger(VersionTest.class);
    
    @Test
    public void testVersion() {
        String version = ControllerVersion.getFloodlightVersion();
        if (version.equals("unknown")) {
            assertTrue("Version should have been detected", false);
        }
        log.info("Found Floodlight version {}", version);
    }
    
    @Test
    public void testName() {
        String name = ControllerVersion.getFloodlightName();
        if (name.equals("unknown")) {
            assertTrue("Name should have been detected", false);
        }
        log.info("Found Floodlight name {}", name);
    }
}