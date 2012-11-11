package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.floodlightcontroller.core.HARoleUnsupportedException;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.jboss.netty.channel.Channel;
import org.junit.Before;
import org.junit.Test;

public class OFSwitchImplTest extends FloodlightTestCase {
    protected OFSwitchImpl sw;
    
    
    @Before
    public void setUp() throws Exception {
        sw = new OFSwitchImpl();
        Channel ch = createMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(ch.getRemoteAddress()).andReturn(sa).anyTimes();
        sw.setChannel(ch);
        MockFloodlightProvider floodlightProvider = new MockFloodlightProvider();
        sw.setFloodlightProvider(floodlightProvider);
    }    
    
    @Test
    public void testSendHARoleRequestFirstTime() {
        boolean ioExceptionCaught = false;
        boolean unsupportedExceptionCaught = false;
        try {
            sw.sendHARoleRequest(Role.MASTER, 12355);
        } catch (IOException e) {
            ioExceptionCaught = true;
        } catch (HARoleUnsupportedException e) {
            unsupportedExceptionCaught = true;
        }
        assertEquals(false, ioExceptionCaught);
        assertEquals(false, unsupportedExceptionCaught);
    }
    
    @Test
    public void testSendHARoleRequestSecondTime() {
        boolean ioExceptionCaught = false;
        boolean unsupportedExceptionCaught = false;
        try {
            sw.setHARole(Role.EQUAL, true);
            sw.sendHARoleRequest(Role.MASTER, 12355);
        } catch (IOException e) {
            ioExceptionCaught = true;
        } catch (HARoleUnsupportedException e) {
            unsupportedExceptionCaught = true;
        }
        assertEquals(false, ioExceptionCaught);
        assertEquals(false, unsupportedExceptionCaught);
    }
    
    @Test
    public void testSendHARoleRequestUnsupported() {
        boolean ioExceptionCaught = false;
        boolean unsupportedExceptionCaught = false;
        try {
            sw.setHARole(Role.EQUAL, false);
            sw.sendHARoleRequest(Role.MASTER, 12355);
        } catch (IOException e) {
            ioExceptionCaught = true;
        } catch (HARoleUnsupportedException e) {
            unsupportedExceptionCaught = true;
        }
        assertEquals(false, ioExceptionCaught);
        assertEquals(true, unsupportedExceptionCaught);
    }
    
    @Test
    public void testSetHARoleReplyReceived() {
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));

        sw.setHARole(Role.MASTER, true);
        assertEquals(Role.MASTER, sw.role);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.EQUAL, true);
        assertEquals(Role.EQUAL, sw.role);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.SLAVE, true);
        assertEquals(Role.SLAVE, sw.role);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
    }
    
    @Test
    public void testSetHARoleNoReply() {
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));

        sw.setHARole(Role.MASTER, false);
        assertEquals(Role.MASTER, sw.role);
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.EQUAL, false);
        assertEquals(Role.EQUAL, sw.role);
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.SLAVE, false);
        assertEquals(Role.SLAVE, sw.role);
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
    }

}
