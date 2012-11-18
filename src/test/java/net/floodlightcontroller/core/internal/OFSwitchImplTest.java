package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;

public class OFSwitchImplTest extends FloodlightTestCase {
    protected OFSwitchImpl sw;
    
    
    @Before
    public void setUp() throws Exception {
        sw = new OFSwitchImpl();
    }    
    
    @Test
    public void testSetHARoleReplyReceived() {
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));

        sw.setHARole(Role.MASTER, true);
        assertEquals(Role.MASTER, sw.getHARole());
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.EQUAL, true);
        assertEquals(Role.EQUAL, sw.getHARole());
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.SLAVE, true);
        assertEquals(Role.SLAVE, sw.getHARole());
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
    }
    
    @Test
    public void testSetHARoleNoReply() {
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));

        sw.setHARole(Role.MASTER, false);
        assertEquals(Role.MASTER, sw.getHARole());
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.EQUAL, false);
        assertEquals(Role.EQUAL, sw.getHARole());
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.SLAVE, false);
        assertEquals(Role.SLAVE, sw.getHARole());
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
    }

}
