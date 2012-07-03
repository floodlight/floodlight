package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.internal.RoleChanger.RoleChangeTask;

import org.easymock.EasyMock;
import org.jboss.netty.channel.Channel;
import org.junit.Before;
import org.junit.Test;

public class RoleChangerTest {
    public RoleChanger roleChanger;
    
    @Before
    public void setUp() throws Exception {
        roleChanger = new RoleChanger();
    }
    
    /**
     * Send a role request for SLAVE to a switch that doesn't support it. 
     * The connection should be closed.
     */
    @Test
    public void testSendRoleRequestSlaveNotSupported() {
        LinkedList<OFSwitchImpl> switches = new LinkedList<OFSwitchImpl>();
        
        // a switch that doesn't support role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        Channel channel1 = createMock(Channel.class);
        expect(sw1.getChannel()).andReturn(channel1);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(false); 
        expect(channel1.close()).andReturn(null);
        switches.add(sw1);
        
        replay(sw1, channel1);
        roleChanger.sendRoleRequest(switches, Role.SLAVE, 123456);
        verify(sw1, channel1);
        
        // sendRoleRequest needs to remove the switch from the list since
        // it closed its connection
        assertTrue(switches.isEmpty());
    }
    
    /**
     * Send a role request for MASTER to a switch that doesn't support it. 
     * The connection should be closed.
     */
    @Test
    public void testSendRoleRequestMasterNotSupported() {
        LinkedList<OFSwitchImpl> switches = new LinkedList<OFSwitchImpl>();
        
        // a switch that doesn't support role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(false); 
        switches.add(sw1);
        
        replay(sw1);
        roleChanger.sendRoleRequest(switches, Role.MASTER, 123456);
        verify(sw1);
        
        assertEquals(1, switches.size());
    }
    
    /**
     * Send a role request a switch that supports it and one that 
     * hasn't had a role request send to it yet
     */
    @Test
    public void testSendRoleRequestErrorHandling () throws Exception {
        LinkedList<OFSwitchImpl> switches = new LinkedList<OFSwitchImpl>();
        
        // a switch that supports role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(true); 
        expect(sw1.sendNxRoleRequest(Role.MASTER, 123456))
                    .andThrow(new IOException()).once();
        Channel channel1 = createMock(Channel.class);
        expect(sw1.getChannel()).andReturn(channel1);
        expect(channel1.close()).andReturn(null);
        switches.add(sw1);
        
        replay(sw1);
        roleChanger.sendRoleRequest(switches, Role.MASTER, 123456);
        verify(sw1);
        
        assertTrue(switches.isEmpty());
    }
    
    /**
     * Check error handling 
     * hasn't had a role request send to it yet
     */
    @Test
    public void testSendRoleRequestSupported() throws Exception {
        LinkedList<OFSwitchImpl> switches = new LinkedList<OFSwitchImpl>();
        
        // a switch that supports role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(true); 
        expect(sw1.sendNxRoleRequest(Role.MASTER, 123456)).andReturn(1).once();
        switches.add(sw1);
        
        // a switch for which we don't have SUPPORTS_NX_ROLE yet
        OFSwitchImpl sw2 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw2.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(null); 
        expect(sw2.sendNxRoleRequest(Role.MASTER, 123456)).andReturn(1).once();
        switches.add(sw2);
        
        
        replay(sw1, sw2);
        roleChanger.sendRoleRequest(switches, Role.MASTER, 123456);
        verify(sw1, sw2);
        
        assertEquals(2, switches.size());
    }
    
    @Test
    public void testVerifyRoleReplyReceived() {
        LinkedList<OFSwitchImpl> switches = new LinkedList<OFSwitchImpl>();
        
        // Add a switch that has received a role reply
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        expect(sw1.checkFirstPendingRoleRequestCookie(123456))
                        .andReturn(false).once();
        switches.add(sw1);
        
        // Add a switch that has not yet received a role reply
        OFSwitchImpl sw2 = EasyMock.createMock(OFSwitchImpl.class);
        expect(sw2.checkFirstPendingRoleRequestCookie(123456))
                        .andReturn(true).once();
        Channel channel2 = createMock(Channel.class);
        expect(sw2.getChannel()).andReturn(channel2);
        expect(channel2.close()).andReturn(null);
        switches.add(sw2);
        
        
        replay(sw1, sw2);
        roleChanger.verifyRoleReplyReceived(switches, 123456);
        verify(sw1, sw2);
        
        assertEquals(2, switches.size());
    }
    
    @Test
    public void testRoleChangeTask() {
        @SuppressWarnings("unchecked")
        Collection<OFSwitchImpl> switches = 
                EasyMock.createMock(Collection.class);
        long now = System.nanoTime();
        long dt1 = 10 * 1000*1000*1000L;
        long dt2 = 20 * 1000*1000*1000L;
        long dt3 = 15 * 1000*1000*1000L;
        RoleChangeTask t1 = new RoleChangeTask(switches, null, now+dt1);
        RoleChangeTask t2 = new RoleChangeTask(switches, null, now+dt2);
        RoleChangeTask t3 = new RoleChangeTask(switches, null, now+dt3);
        
        // FIXME: cannot test comparison against self. grrr
        //assertTrue( t1.compareTo(t1) <= 0 );
        assertTrue( t1.compareTo(t2) < 0 );
        assertTrue( t1.compareTo(t3) < 0 );
        
        assertTrue( t2.compareTo(t1) > 0 );
        //assertTrue( t2.compareTo(t2) <= 0 );
        assertTrue( t2.compareTo(t3) > 0 );
    }
    
    @Test
    public void testSubmitRequest() throws Exception {
        LinkedList<OFSwitchImpl> switches = new LinkedList<OFSwitchImpl>();
        roleChanger.timeout = 500*1000*1000; // 500 ms
        
        // a switch that supports role requests
        OFSwitchImpl sw1 = EasyMock.createStrictMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(true); 
        expect(sw1.sendNxRoleRequest(EasyMock.same(Role.MASTER), EasyMock.anyLong()))
                       .andReturn(1);
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(true); 
        expect(sw1.sendNxRoleRequest(EasyMock.same(Role.SLAVE), EasyMock.anyLong()))
                       .andReturn(1);
        // The following calls happen for timeout handling:
        expect(sw1.checkFirstPendingRoleRequestCookie(EasyMock.anyLong()))
                        .andReturn(false);
        expect(sw1.checkFirstPendingRoleRequestCookie(EasyMock.anyLong()))
                        .andReturn(false);
        switches.add(sw1);
        
        
        replay(sw1);
        roleChanger.submitRequest(switches, Role.MASTER);
        roleChanger.submitRequest(switches, Role.SLAVE);
        // Wait until role request has been sent. 
        // TODO: need to get rid of this sleep somehow
        Thread.sleep(100);
        // Now there should be exactly one timeout task pending
        assertEquals(2, roleChanger.pendingTasks.size());
        // Make sure it's indeed a timeout task
        assertSame(RoleChanger.RoleChangeTask.Type.TIMEOUT, 
                     roleChanger.pendingTasks.peek().type);
        // Check that RoleChanger indeed made a copy of switches collection
        assertNotSame(switches, roleChanger.pendingTasks.peek().switches);
        
        // Wait until the timeout triggers 
        // TODO: get rid of this sleep too.
        Thread.sleep(500);
        assertEquals(0, roleChanger.pendingTasks.size());
        verify(sw1);
        
    }
    
}
