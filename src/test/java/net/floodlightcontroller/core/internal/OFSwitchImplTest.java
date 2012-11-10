package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitchImpl.PendingRoleRequestEntry;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.easymock.Capture;
import org.jboss.netty.channel.Channel;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.vendor.OFVendorData;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;

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

    
    public void doSendNxRoleRequest(Role role, int nx_role) throws Exception {
        long cookie = System.nanoTime();
        
        // verify that the correct OFMessage is sent
        Capture<List<OFMessage>> msgCapture = new Capture<List<OFMessage>>();
        expect(sw.channel.write(capture(msgCapture))).andReturn(null);
        replay(sw.channel);
        int xid = sw.sendHARoleRequest(role, cookie);
        verify(sw.channel);
        List<OFMessage> msgList = msgCapture.getValue();
        assertEquals(1, msgList.size());
        OFMessage msg = msgList.get(0);
        assertEquals("Transaction Ids must match", xid, msg.getXid()); 
        assertTrue("Message must be an OFVendor type", msg instanceof OFVendor);
        assertEquals(OFType.VENDOR, msg.getType());
        OFVendor vendorMsg = (OFVendor)msg;
        assertEquals("Vendor message must be vendor Nicira",
                     OFNiciraVendorData.NX_VENDOR_ID, vendorMsg.getVendor());
        OFVendorData vendorData = vendorMsg.getVendorData();
        assertTrue("Vendor Data must be an OFRoleRequestVendorData",
                     vendorData instanceof OFRoleRequestVendorData);
        OFRoleRequestVendorData roleRequest = (OFRoleRequestVendorData)vendorData;
        assertEquals(nx_role, roleRequest.getRole());
        
        // Now verify that we've added the pending request correctly
        // to the pending queue
        assertEquals(1, sw.pendingRoleRequests.size());
        PendingRoleRequestEntry pendingRoleRequest = sw.pendingRoleRequests.poll();
        assertEquals(msg.getXid(), pendingRoleRequest.xid);
        assertEquals(role, pendingRoleRequest.role);
        assertEquals(cookie, pendingRoleRequest.cookie);
        reset(sw.channel);
    }
    
    @Test
    public void testSendNxRoleRequest() throws Exception {
        doSendNxRoleRequest(Role.MASTER, OFRoleVendorData.NX_ROLE_MASTER);
        doSendNxRoleRequest(Role.SLAVE, OFRoleVendorData.NX_ROLE_SLAVE);
        doSendNxRoleRequest(Role.EQUAL, OFRoleVendorData.NX_ROLE_OTHER);
    }
    
    
    @Test
    public void testDeliverRoleReplyOk() {
        // test normal case
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            (int)System.currentTimeMillis(),  // arbitrary xid
                            Role.MASTER,
                            System.nanoTime() // arbitrary cookie
                            );
        sw.pendingRoleRequests.add(pending);
        replay(sw.channel);
        sw.deliverRoleReply(pending.xid, pending.role);
        verify(sw.channel);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(pending.role, sw.role);
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testDeliverRoleReplyOkRepeated() {
        // test normal case. Not the first role reply
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            (int)System.currentTimeMillis(),  // arbitrary xid
                            Role.MASTER,
                            System.nanoTime() // arbitrary cookie
                            );
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        sw.pendingRoleRequests.add(pending);
        replay(sw.channel);
        sw.deliverRoleReply(pending.xid, pending.role);
        verify(sw.channel);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(pending.role, sw.role);
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testDeliverRoleReplyNonePending() {
        // nothing pending 
        expect(sw.channel.close()).andReturn(null);
        replay(sw.channel);
        sw.deliverRoleReply(1, Role.MASTER);
        verify(sw.channel);
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testDeliverRoleReplyWrongXid() {
        // wrong xid received 
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            (int)System.currentTimeMillis(),  // arbitrary xid
                            Role.MASTER,
                            System.nanoTime() // arbitrary cookie
                            );
        sw.pendingRoleRequests.add(pending);
        expect(sw.channel.close()).andReturn(null);
        replay(sw.channel);
        sw.deliverRoleReply(pending.xid+1, pending.role);
        verify(sw.channel);
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testDeliverRoleReplyWrongRole() {
        // correct xid but incorrect role received
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            (int)System.currentTimeMillis(),  // arbitrary xid
                            Role.MASTER,
                            System.nanoTime() // arbitrary cookie
                            );
        sw.pendingRoleRequests.add(pending);
        expect(sw.channel.close()).andReturn(null);
        replay(sw.channel);
        sw.deliverRoleReply(pending.xid, Role.SLAVE);
        verify(sw.channel);
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testCheckFirstPendingRoleRequestXid() {
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            54321, Role.MASTER, 232323);
        replay(sw.channel); // we don't expect any invocations 
        sw.pendingRoleRequests.add(pending);
        assertEquals(true, sw.checkFirstPendingRoleRequestXid(54321));
        assertEquals(false, sw.checkFirstPendingRoleRequestXid(0));
        sw.pendingRoleRequests.clear();
        assertEquals(false, sw.checkFirstPendingRoleRequestXid(54321));
        verify(sw.channel);
    }
    
    @Test
    public void testCheckFirstPendingRoleRequestCookie() {
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            54321, Role.MASTER, 232323);
        replay(sw.channel); // we don't expect any invocations 
        sw.pendingRoleRequests.add(pending);
        assertEquals(true, sw.checkFirstPendingRoleRequestCookie(232323));
        assertEquals(false, sw.checkFirstPendingRoleRequestCookie(0));
        sw.pendingRoleRequests.clear();
        assertEquals(false, sw.checkFirstPendingRoleRequestCookie(232323));
        verify(sw.channel);
    }
    
    @Test
    public void testDeliverRoleRequestNotSupported () {
        // normal case. xid is pending 
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            (int)System.currentTimeMillis(),  // arbitrary xid
                            Role.MASTER,
                            System.nanoTime() // arbitrary cookie
                            );
        sw.role = Role.SLAVE;
        sw.pendingRoleRequests.add(pending);
        replay(sw.channel);
        sw.deliverRoleRequestNotSupported(pending.xid);
        verify(sw.channel);
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(null, sw.role);
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testDeliverRoleRequestNotSupportedNonePending() {
        // nothing pending 
        sw.role = Role.SLAVE;
        expect(sw.channel.close()).andReturn(null);
        replay(sw.channel);
        sw.deliverRoleRequestNotSupported(1);
        verify(sw.channel);
        assertEquals(null, sw.role);
        assertEquals(0, sw.pendingRoleRequests.size());
    }
    
    @Test
    public void testDeliverRoleRequestNotSupportedWrongXid() {
        // wrong xid received 
        PendingRoleRequestEntry pending = new PendingRoleRequestEntry(
                            (int)System.currentTimeMillis(),  // arbitrary xid
                            Role.MASTER,
                            System.nanoTime() // arbitrary cookie
                            );
        sw.role = Role.SLAVE;
        sw.pendingRoleRequests.add(pending);
        expect(sw.channel.close()).andReturn(null);
        replay(sw.channel);
        sw.deliverRoleRequestNotSupported(pending.xid+1);
        verify(sw.channel);
        assertEquals(null, sw.role);
        assertEquals(0, sw.pendingRoleRequests.size());
    }
}
