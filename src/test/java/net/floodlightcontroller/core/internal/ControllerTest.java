/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.*;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IListener;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFMessageFilterManagerService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFMessageFilterManager;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.SwitchSyncRepresentation;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.perfmon.PktInProcessingTime;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.IStoreListener.UpdateType;
import org.sdnplatform.sync.test.MockSyncService;

public class ControllerTest extends FloodlightTestCase {

    private Controller controller;
    private MockThreadPoolService tp;
    private MockSyncService syncService;
    private IStoreClient<Long, SwitchSyncRepresentation> storeClient;
    private IPacket testPacket;
    private OFPacketIn pi;

    @Override
    @Before
    public void setUp() throws Exception {
        doSetUp(Role.MASTER);
    }


    public void doSetUp(Role role) throws Exception {
        super.setUp();
        FloodlightModuleContext fmc = new FloodlightModuleContext();

        FloodlightProvider cm = new FloodlightProvider();
        fmc.addConfigParam(cm, "role", role.toString());
        controller = (Controller)cm.getServiceImpls().get(IFloodlightProviderService.class);
        fmc.addService(IFloodlightProviderService.class, controller);

        MemoryStorageSource memstorage = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, memstorage);

        RestApiServer restApi = new RestApiServer();
        fmc.addService(IRestApiService.class, restApi);

        CounterStore cs = new CounterStore();
        fmc.addService(ICounterStoreService.class, cs);

        PktInProcessingTime ppt = new PktInProcessingTime();
        fmc.addService(IPktInProcessingTimeService.class, ppt);

        tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);

        syncService = new MockSyncService();
        fmc.addService(ISyncService.class, syncService);



        ppt.init(fmc);
        restApi.init(fmc);
        memstorage.init(fmc);
        tp.init(fmc);
        syncService.init(fmc);
        cm.init(fmc);

        ppt.startUp(fmc);
        restApi.startUp(fmc);
        memstorage.startUp(fmc);
        tp.startUp(fmc);
        syncService.startUp(fmc);
        cm.startUp(fmc);

        storeClient =
                syncService.getStoreClient(Controller.SWITCH_SYNC_STORE_NAME,
                                           Long.class,
                                           SwitchSyncRepresentation.class);

        testPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        byte[] testPacketSerialized = testPacket.serialize();

        pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

    }

    @Override
    @After
    public void tearDown() {
        tp.getScheduledExecutor().shutdownNow();
        // Make sure thare are not left over updates in the queue
        controller.processUpdateQueueForTesting();
    }

    public Controller getController() {
        return controller;
    }

    private static OFDescriptionStatistics createOFDescriptionStatistics() {
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        desc.setDatapathDescription("");
        desc.setHardwareDescription("");
        desc.setManufacturerDescription("");
        desc.setSerialNumber("");
        desc.setSoftwareDescription("");
        return desc;
    }

    private static OFFeaturesReply createOFFeaturesReply() {
        OFFeaturesReply fr = new OFFeaturesReply();
        fr.setPorts(Collections.<OFPhysicalPort>emptyList());
        return fr;
    }


    /* Set the mock expectations for sw when sw is passed to addSwitch */
    protected void setupSwitchForAddSwitch(IOFSwitch sw, long dpid,
                                           OFDescriptionStatistics desc,
                                           OFFeaturesReply featuresReply) {
        String dpidString = HexString.toHexString(dpid);

        if (desc == null) {
            desc = createOFDescriptionStatistics();
        }
        if (featuresReply == null) {
            featuresReply = createOFFeaturesReply();
            featuresReply.setDatapathId(dpid);
        }

        expect(sw.getId()).andReturn(dpid).anyTimes();
        expect(sw.getStringId()).andReturn(dpidString).anyTimes();
        expect(sw.getDescriptionStatistics()) .andReturn(desc).atLeastOnce();
        expect(sw.getBuffers())
                .andReturn(featuresReply.getBuffers()).atLeastOnce();
        expect(sw.getTables())
                .andReturn(featuresReply.getTables()).atLeastOnce();
        expect(sw.getCapabilities())
                .andReturn(featuresReply.getCapabilities()).atLeastOnce();
        expect(sw.getActions())
                .andReturn(featuresReply.getActions()).atLeastOnce();
        expect(sw.getPorts())
                .andReturn(featuresReply.getPorts()).atLeastOnce();
    }

    @SuppressWarnings("unchecked")
    private <T> void setupListenerOrdering(IListener<T> listener) {
        listener.isCallbackOrderingPostreq((T)anyObject(),
                                           anyObject(String.class));
        expectLastCall().andReturn(false).anyTimes();

        listener.isCallbackOrderingPrereq((T)anyObject(),
                                          anyObject(String.class));
        expectLastCall().andReturn(false).anyTimes();
    }

    @Test
    public void testHandleMessagesNoListeners() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        replay(sw);
        controller.handleMessage(sw, pi, null);
        verify(sw);
    }

    /**
     * Verify that a listener that throws an exception halts further
     * execution, and verify that the Commands STOP and CONTINUE are honored.
     * @throws Exception
     */
    @Test
    public void testHandleMessages() throws Exception {
        controller.removeOFMessageListeners(OFType.PACKET_IN);

        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").anyTimes();
        setupListenerOrdering(test1);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class))).andReturn(Command.CONTINUE);
        IOFMessageListener test2 = createMock(IOFMessageListener.class);
        expect(test2.getName()).andReturn("test2").anyTimes();
        expect(test2.isCallbackOrderingPrereq((OFType)anyObject(), eq("test1"))).andReturn(false).anyTimes();
        expect(test2.isCallbackOrderingPrereq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(test2.isCallbackOrderingPostreq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(test2.receive(eq(sw), eq(pi), isA(FloodlightContext.class))).andThrow(new RuntimeException("This is NOT an error! We are testing exception catching."));
        // expect no calls to test2.receive() since test1.receive() threw an exception

        replay(test1, test2, sw);
        controller.addOFMessageListener(OFType.PACKET_IN, test1);
        controller.addOFMessageListener(OFType.PACKET_IN, test2);
        boolean exceptionThrown = false;
        try {
            controller.handleMessage(sw, pi, null);
        } catch (RuntimeException e) {
            assertEquals(e.getMessage().startsWith("This is NOT an error!"), true);
            exceptionThrown = true;
        }
        verify(test1, test2, sw);
        assertTrue("Expected exception was not thrown by test2",
                   exceptionThrown);

        // verify STOP works
        reset(test1, test2, sw);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class))).andReturn(Command.STOP);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        replay(test1, test2, sw);
        controller.handleMessage(sw, pi, null);
        verify(test1, test2, sw);
    }

    @Test
    public void testHandleMessagesSlave() throws Exception {
        doSetUp(Role.SLAVE);
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").atLeastOnce();
        expect(test1.isCallbackOrderingPrereq((OFType)anyObject(),
                                              (String)anyObject()))
                .andReturn(false).atLeastOnce();
        expect(test1.isCallbackOrderingPostreq((OFType)anyObject(),
                                               (String)anyObject()))
                .andReturn(false).atLeastOnce();

        replay(test1, sw);
        controller.addOFMessageListener(OFType.PACKET_IN, test1);
        // message should not be dispatched
        controller.handleMessage(sw, pi, null);
        verify(test1);

        //---------------------------------
        // transition to Master
        //--------------------------------
        controller.setRole(Role.MASTER, "FooBar");

        // transitioned but HA listeneres not yet notified.
        // message should not be dispatched
        reset(test1);
        replay(test1);
        controller.handleMessage(sw, pi, null);
        verify(test1);

        // notify HA listeners
        controller.processUpdateQueueForTesting();
        // no message should be dispatched
        reset(test1);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class))).andReturn(Command.STOP);
        replay(test1);
        controller.handleMessage(sw, pi, null);
        verify(test1);

        verify(sw);
    }


    @Test
    public void testHandleMessageWithContext() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").anyTimes();
        expect(test1.isCallbackOrderingPrereq((OFType)anyObject(),
                                              (String)anyObject()))
                .andReturn(false).anyTimes();
        expect(test1.isCallbackOrderingPostreq((OFType)anyObject(),
                                               (String)anyObject()))
                .andReturn(false).anyTimes();
        FloodlightContext cntx = new FloodlightContext();
        expect(test1.receive(same(sw), same(pi) , same(cntx)))
                .andReturn(Command.CONTINUE);

        IOFMessageListener test2 = createMock(IOFMessageListener.class);
        expect(test2.getName()).andReturn("test2").anyTimes();
        expect(test2.isCallbackOrderingPrereq((OFType)anyObject(),
                                              (String)anyObject()))
                .andReturn(false).anyTimes();
        expect(test2.isCallbackOrderingPostreq((OFType)anyObject(),
                                               (String)anyObject()))
                .andReturn(false).anyTimes();
        // test2 will not receive any message!

        replay(test1, test2, sw);
        controller.addOFMessageListener(OFType.PACKET_IN, test1);
        controller.addOFMessageListener(OFType.ERROR, test2);
        controller.handleMessage(sw, pi, cntx);
        verify(test1, test2, sw);

        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        assertArrayEquals(testPacket.serialize(), eth.serialize());
    }


    @Test
    public void testMessageFilterManager() throws Exception {
        class MyOFMessageFilterManager extends OFMessageFilterManager {
            public MyOFMessageFilterManager(int timer_interval) {
                super();
                TIMER_INTERVAL = timer_interval;
            }
        }
        FloodlightModuleContext fmCntx = new FloodlightModuleContext();
        MockFloodlightProvider mfp = new MockFloodlightProvider();
        OFMessageFilterManager mfm = new MyOFMessageFilterManager(100);
        MockThreadPoolService mtp = new MockThreadPoolService();
        fmCntx.addService(IOFMessageFilterManagerService.class, mfm);
        fmCntx.addService(IFloodlightProviderService.class, mfp);
        fmCntx.addService(IThreadPoolService.class, mtp);
        String sid = null;

        mfm.init(fmCntx);
        mfm.startUp(fmCntx);

        ConcurrentHashMap <String, String> filter;
        int i;

        //Adding the filter works -- adds up to the maximum filter size.
        for(i=mfm.getMaxFilterSize(); i > 0; --i) {
            filter = new ConcurrentHashMap<String,String>();
            filter.put("mac", String.format("00:11:22:33:44:%d%d", i,i));
            sid = mfm.setupFilter(null, filter, 60);
            assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize() - i +1);
        }

        // Add one more to see if you can't
        filter = new ConcurrentHashMap<String,String>();
        filter.put("mac", "mac2");
        mfm.setupFilter(null, filter, 10*1000);

        assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize());

        // Deleting the filter works.
        mfm.setupFilter(sid, null, -1);
        assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize()-1);

        // Creating mock switch to which we will send packet out and
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(new Long(0));

        // Mock Packet-in
        IPacket testPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        byte[] testPacketSerialized = testPacket.serialize();

        // Build the PacketIn
        OFPacketIn pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        // Mock Packet-out
        OFPacketOut packetOut =
                (OFPacketOut) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(pi.getBufferId())
        .setInPort(pi.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput(OFPort.OFPP_TABLE.getValue(), (short) 0));
        packetOut.setActions(poactions)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);

        FloodlightContext cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD, (Ethernet) testPacket);


        // Let's check the listeners.
        List <IOFMessageListener> lm;

        // Check to see if all the listeners are active.
        lm = mfp.getListeners().get(OFType.PACKET_OUT);
        assertTrue(lm.size() == 1);
        assertTrue(lm.get(0).equals(mfm));

        lm = mfp.getListeners().get(OFType.FLOW_MOD);
        assertTrue(lm.size() == 1);
        assertTrue(lm.get(0).equals(mfm));

        lm = mfp.getListeners().get(OFType.PACKET_IN);
        assertTrue(lm.size() == 1);
        assertTrue(lm.get(0).equals(mfm));

        HashSet<String> matchedFilters;

        // Send a packet in and check if it matches a filter.
        matchedFilters = mfm.getMatchedFilters(pi, cntx);
        assertTrue(matchedFilters.size() == 1);

        // Send a packet out and check if it matches a filter
        matchedFilters = mfm.getMatchedFilters(packetOut, cntx);
        assertTrue(matchedFilters.size() == 1);

        // Wait for all filters to be timed out.
        Thread.sleep(150);
        assertEquals(0, mfm.getNumberOfFilters());
    }


    @Test
    public void testGetRoleInfoDefault() {
        RoleInfo info = controller.getRoleInfo();
        assertEquals(Role.MASTER.toString(), info.getRole());
        assertNotNull(info.getRoleChangeDescription());
        assertEquals(Role.MASTER, controller.getRole());
        // FIXME: RoleInfo's date. but the format is kinda broken
    }

    /*
     * Test interaction with OFChannelHandler when the current role is
     * master.
     */
    @Test
    public void testChannelHandlerMaster() {
        OFChannelHandler h = createMock(OFChannelHandler.class);

        // Add the handler. The controller should call sendRoleRequest
        h.sendRoleRequest(Role.MASTER);
        expectLastCall().once();
        replay(h);
        controller.addSwitchChannelAndSendInitialRole(h);
        verify(h);

        // Reassert the role.
        reset(h);
        h.sendRoleRequestIfNotPending(Role.MASTER);
        replay(h);
        controller.reassertRole(h, Role.MASTER);
        verify(h);

        // reassert a different role: no-op
        reset(h);
        replay(h);
        controller.reassertRole(h, Role.SLAVE);
        verify(h);
    }

    /*
     * Start as SLAVE then set role to MASTER
     * Tests normal role change transition. Check that connected channels
     * receive a setRole request
     */
    @Test
    public void testSetRole() throws Exception {
        doSetUp(Role.SLAVE);
        RoleInfo info = controller.getRoleInfo();
        assertEquals(Role.SLAVE.toString(), info.getRole());
        assertEquals(Role.SLAVE, controller.getRole());


        OFChannelHandler h = createMock(OFChannelHandler.class);

        // Add the channel handler. The controller should call sendRoleRequest
        h.sendRoleRequest(Role.SLAVE);
        expectLastCall().once();
        replay(h);
        controller.addSwitchChannelAndSendInitialRole(h);
        verify(h);

        // Reassert the role.
        reset(h);
        h.sendRoleRequestIfNotPending(Role.SLAVE);
        replay(h);
        controller.reassertRole(h, Role.SLAVE);
        verify(h);

        // reassert a different role: no-op
        reset(h);
        replay(h);
        controller.reassertRole(h, Role.MASTER);
        verify(h);

        // Change role to MASTER
        reset(h);
        h.sendRoleRequest(Role.MASTER);
        expectLastCall().once();
        IHAListener listener = createMock(IHAListener.class);
        expect(listener.getName()).andReturn("foo").anyTimes();
        setupListenerOrdering(listener);
        listener.transitionToMaster();
        expectLastCall().once();
        replay(listener);
        replay(h);
        controller.addHAListener(listener);
        controller.setRole(Role.MASTER, "FooBar");
        controller.processUpdateQueueForTesting();
        verify(h);
        verify(listener);
        info = controller.getRoleInfo();
        assertEquals(Role.MASTER.toString(), info.getRole());
        assertEquals("FooBar", info.getRoleChangeDescription());
        assertEquals(Role.MASTER, controller.getRole());


    }

    /* Test other setRole cases: re-setting role to the current role,
     * setting role to equal, etc.
     */
    @Test
    public void testSetRoleOthercases() throws Exception {
        doSetUp(Role.SLAVE);

        OFChannelHandler h = createMock(OFChannelHandler.class);

        // Add the channel handler. The controller should call sendRoleRequest
        h.sendRoleRequest(Role.SLAVE);
        expectLastCall().once();
        replay(h);
        controller.addSwitchChannelAndSendInitialRole(h);
        verify(h);

        // remove the channel. Nothing should
        reset(h);
        replay(h);
        controller.removeSwitchChannel(h);

        // Create and add the HA listener
        IHAListener listener = createMock(IHAListener.class);
        expect(listener.getName()).andReturn("foo").anyTimes();
        setupListenerOrdering(listener);
        replay(listener);
        controller.addHAListener(listener);

        // Set role to slave again. Nothing should happen
        controller.setRole(Role.SLAVE, "FooBar");
        controller.processUpdateQueueForTesting();
        verify(listener);

        reset(listener);
        listener.transitionToMaster();
        expectLastCall().once();
        replay(listener);

        // set role to equal. Should set to master internally
        controller.setRole(Role.EQUAL, "ToEqual");
        controller.processUpdateQueueForTesting();
        verify(listener);
        RoleInfo info = controller.getRoleInfo();
        assertEquals(Role.MASTER.toString(), info.getRole());
        assertEquals("ToEqual", info.getRoleChangeDescription());
        assertEquals(Role.MASTER, controller.getRole());


        verify(h); // no calls should have happened on h
    }



    @Test
    public void testSetRoleNPE() {
        try {
            controller.setRole(null, "");
            fail("Should have thrown an Exception");
        }
        catch (NullPointerException e) {
            //exptected
        }
        try {
            controller.setRole(Role.MASTER, null);
            fail("Should have thrown an Exception");
        }
        catch (NullPointerException e) {
            //exptected
        }
    }





    @Test
    /*
     * Test switchActivated for a new switch, i.e., a switch that was not
     * previously known to the controller cluser. We expect that all
     * flow mods are cleared and we expect a switchAdded
     */
    public void testNewSwitchActivated() throws Exception {
        controller.setAlwaysClearFlowsOnSwAdd(true);

        IOFSwitch sw = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(sw, 0L, null, null);
        sw.clearAllFlowMods();
        expectLastCall().once();

        // strict mock. Order of events matters!
        IOFSwitchListener listener = createStrictMock(IOFSwitchListener.class);
        listener.switchAdded(0L);
        expectLastCall().once();
        listener.switchActivated(0L);
        expectLastCall().once();
        replay(listener);
        controller.addOFSwitchListener(listener);

        replay(sw);
        controller.switchActivated(sw);
        verify(sw);
        assertEquals(sw, controller.getSwitch(0L));
        controller.processUpdateQueueForTesting();
        verify(listener);

        SwitchSyncRepresentation storedSwitch = storeClient.getValue(0L);
        assertEquals(createOFFeaturesReply(), storedSwitch.getFeaturesReply());
        assertEquals(createOFDescriptionStatistics(),
                     storedSwitch.getDescription());
    }


    /*
     * Create and activate a new switch with the given dpid, features reply
     * and description. If description and/or features reply are null we'll
     * allocate the default one
     * The mocked switch instance will be returned. It wil be reset.
     */
    public IOFSwitch doActivateNewSwitch(long dpid,
                                         OFDescriptionStatistics desc,
                                         OFFeaturesReply featuresReply)
                                         throws Exception {
        controller.setAlwaysClearFlowsOnSwAdd(true);

        IOFSwitch sw = createMock(IOFSwitch.class);
        if (featuresReply == null) {
            featuresReply = createOFFeaturesReply();
            featuresReply.setDatapathId(dpid);
        }
        if (desc == null) {
            desc = createOFDescriptionStatistics();
        }
        setupSwitchForAddSwitch(sw, dpid, desc, featuresReply);
        sw.clearAllFlowMods();
        expectLastCall().once();

        replay(sw);
        controller.switchActivated(sw);
        verify(sw);
        assertEquals(sw, controller.getSwitch(dpid));
        // drain updates and ignore
        controller.processUpdateQueueForTesting();

        SwitchSyncRepresentation storedSwitch = storeClient.getValue(dpid);
        assertEquals(featuresReply, storedSwitch.getFeaturesReply());
        assertEquals(desc, storedSwitch.getDescription());
        reset(sw);
        return sw;
    }


    /*
     * Create a switch sync representation and add it to the store and
     * notify the store listener.
     * If the description and/or features reply are null, we'll allocate
     * the default one
     */
    public void doAddSwitchToStore(long dpid,
                                   OFDescriptionStatistics desc,
                                   OFFeaturesReply featuresReply)
                                   throws Exception {
        if (featuresReply == null) {
            featuresReply = createOFFeaturesReply();
            featuresReply.setDatapathId(dpid);
        }
        if (desc == null) {
            desc = createOFDescriptionStatistics();
        }

        SwitchSyncRepresentation ssr =
                new SwitchSyncRepresentation(featuresReply, desc);
        storeClient.put(dpid, ssr);

        Iterator<Long> keysToNotify = Collections.singletonList(dpid).iterator();
        controller.getStoreListener().keysModified(keysToNotify,
                                                   UpdateType.REMOTE);
    }


    /* add switch to store while master. no-op */
    @Test
    public void testAddSwitchToStoreMaster() throws Exception {
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);
        replay(listener);

        doAddSwitchToStore(1L, null, null);
        controller.processUpdateQueueForTesting();
        IOFSwitch sw = controller.getSwitch(1L);
        verify(listener);
        assertNull("There shouldn't be a switch", sw);
    }


    /*
     * add switch to store while slave. should get notification and switch
     * should be added
     */
    @Test
    public void testAddSwitchToStoreSlave() throws Exception {
        doSetUp(Role.SLAVE);

        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        listener.switchAdded(1L);
        expectLastCall().once();
        controller.addOFSwitchListener(listener);
        replay(listener);

        OFDescriptionStatistics desc = createOFDescriptionStatistics();
        desc.setDatapathDescription("The Switch");
        doAddSwitchToStore(1L, desc, null);
        controller.processUpdateQueueForTesting();
        verify(listener);

        IOFSwitch sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertEquals(1L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());
        assertEquals("The Switch",
                     sw.getDescriptionStatistics().getDatapathDescription());
    }

    private static OFPhysicalPort createOFPhysicalPort(String name, int number) {
        OFPhysicalPort p = new OFPhysicalPort();
        p.setHardwareAddress(new byte [] { 0, 0, 0, 0, 0, 0 });
        p.setPortNumber((short)number);
        p.setName(name);
        return p;
    }

    /*
     * This test goes through the SLAVE->MASTER program flow. We'll start as
     * SLAVE. Add switches to the store while slave, update these switches
     * then transition to master, make most (but not all switches) "connect"
     * We also check correct behavior of getAllSwitchDpids() and
     * getAllSwitchMap()
     */
    @Test
    public void testSwitchAddWithRoleChange() throws Exception {
        int consolidateStoreDelayMs = 50;
        doSetUp(Role.SLAVE);

        // Add HA Listener
        IHAListener haListener = createMock(IHAListener.class);
        expect(haListener.getName()).andReturn("foo").anyTimes();
        setupListenerOrdering(haListener);
        replay(haListener);
        controller.addHAListener(haListener);
        verify(haListener);
        reset(haListener);

        // Add switch listener
        IOFSwitchListener switchListener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(switchListener);

        //---------------------------------------
        // Initialization
        //---------------------------------------

        // Switch 1
        OFFeaturesReply fr1a = createOFFeaturesReply();
        fr1a.setDatapathId(1L);
        OFPhysicalPort p = createOFPhysicalPort("P1", 1);
        List<OFPhysicalPort> ports1a = Collections.singletonList(p);
        fr1a.setPorts(ports1a);
        // an alternative featuers reply
        OFFeaturesReply fr1b = createOFFeaturesReply();
        fr1b.setDatapathId(1L);
        p = new OFPhysicalPort();
        p = createOFPhysicalPort("P1", 1); // same port as above
        List<OFPhysicalPort> ports1b = new ArrayList<OFPhysicalPort>();
        ports1b.add(p);
        p = createOFPhysicalPort("P2", 42000);
        ports1b.add(p);
        fr1b.setPorts(ports1b);

        // Switch 2
        OFFeaturesReply fr2a = createOFFeaturesReply();
        fr2a.setDatapathId(2L);
        List<OFPhysicalPort> ports2a = new ArrayList<OFPhysicalPort>(ports1a);
        fr2a.setPorts(ports2a);
        // an alternative features reply
        OFFeaturesReply fr2b = createOFFeaturesReply();
        fr2b.setDatapathId(2L);
        p = new OFPhysicalPort();
        p = createOFPhysicalPort("P1", 2); // port number changed
        List<OFPhysicalPort> ports2b = Collections.singletonList(p);
        fr2b.setPorts(ports2b);

        //---------------------------------------
        // Adding switches to store
        //---------------------------------------

        replay(haListener); // nothing should happen to haListener

        // add switch1 with fr1a to store
        reset(switchListener);
        switchListener.switchAdded(1L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(1L, null, fr1a);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        IOFSwitch sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertEquals(1L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());
        assertEquals(new HashSet<OFPhysicalPort>(ports1a),
                     new HashSet<OFPhysicalPort>(sw.getPorts()));

        // add switch 2 with fr2a to store
        reset(switchListener);
        switchListener.switchAdded(2L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(2L, null, fr2a);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        sw = controller.getSwitch(2L);
        assertNotNull("Switch should be present", sw);
        assertEquals(2L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());
        assertEquals(new HashSet<OFPhysicalPort>(ports2a),
                     new HashSet<OFPhysicalPort>(sw.getPorts()));

        // add switch 3 to store
        reset(switchListener);
        switchListener.switchAdded(3L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(3L, null, null);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        sw = controller.getSwitch(3L);
        assertNotNull("Switch should be present", sw);
        assertEquals(3L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        // update switch 1 with fr1b
        reset(switchListener);
        switchListener.switchPortChanged(1L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(1L, null, fr1b);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertEquals(1L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());
        assertEquals(new HashSet<OFPhysicalPort>(ports1b),
                     new HashSet<OFPhysicalPort>(sw.getPorts()));

        // Check getAllSwitchDpids() and getAllSwitchMap()
        Set<Long> expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        expectedDpids.add(3L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        Map<Long, IOFSwitch> expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, controller.getSwitch(1L));
        expectedSwitchMap.put(2L, controller.getSwitch(2L));
        expectedSwitchMap.put(3L, controller.getSwitch(3L));
        assertEquals(expectedSwitchMap, controller.getAllSwitchMap());

        verify(haListener);
        //--------------------------------------
        // Transition to master
        //--------------------------------------
        reset(haListener);
        haListener.transitionToMaster();
        expectLastCall().once();
        replay(haListener);
        controller.setConsolidateStoreTaskDelay(consolidateStoreDelayMs);
        controller.setRole(Role.MASTER, "FooBar");
        controller.processUpdateQueueForTesting();
        verify(haListener);
        reset(haListener);
        replay(haListener);

        //--------------------------------------
        // Activate switches
        //--------------------------------------

        // Activate switch 1
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(sw1, 1L, null, fr1b);
        reset(switchListener);
        switchListener.switchActivated(1L);
        expectLastCall().once();
        replay(sw1);
        replay(switchListener);
        controller.switchActivated(sw1);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        verify(sw1);

        sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw1, sw);   // the mock switch should be returned

        // Activate switch 2 with different features reply
        // should get portChanged
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(sw2, 2L, null, fr2b);
        reset(switchListener);
        switchListener.switchActivated(2L);
        expectLastCall().once();
        switchListener.switchPortChanged(2L);
        expectLastCall().once();
        replay(sw2);
        replay(switchListener);
        controller.switchActivated(sw2);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        verify(sw2);

        sw = controller.getSwitch(2L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw2, sw); // the mock switch should be returned


        // Do not activate switch 3, but it should still be present
        sw = controller.getSwitch(3L);
        IOFSwitch sw3 = sw;
        assertNotNull("Switch should be present", sw);
        assertEquals(3L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        // Check getAllSwitchDpids() and getAllSwitchMap()
        expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        expectedDpids.add(3L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, sw1);
        expectedSwitchMap.put(2L, sw2);
        expectedSwitchMap.put(3L, sw3);
        assertEquals(expectedSwitchMap, controller.getAllSwitchMap());

        //--------------------------------
        // Wait for consolidateStore
        //--------------------------------
        reset(switchListener);
        switchListener.switchRemoved(3L);
        replay(switchListener);
        Thread.sleep(consolidateStoreDelayMs + 5);
        controller.processUpdateQueueForTesting();
        verify(switchListener);

        // Verify the expected switches are all there. no more no less
        sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw1, sw);   // the mock switch should be returned

        sw = controller.getSwitch(2L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw2, sw); // the mock switch should be returned

        // Do not activate switch 3, but it should still be present
        sw = controller.getSwitch(3L);
        assertNull("Switch should NOT be present", sw);

        // Check getAllSwitchDpids() and getAllSwitchMap()
        expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, sw1);
        expectedSwitchMap.put(2L, sw2);
        assertEquals(expectedSwitchMap, controller.getAllSwitchMap());

        verify(haListener);
    }



    /*
     * Disconnect a switch. normal program flow
     */
    @Test
    public void testSwitchDisconnected() throws Exception {
        IOFSwitch sw = doActivateNewSwitch(1L, null, null);
        expect(sw.getId()).andReturn(1L).anyTimes();
        expect(sw.getStringId()).andReturn(HexString.toHexString(1L)).anyTimes();
        sw.cancelAllStatisticsReplies();
        expectLastCall().once();
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        listener.switchRemoved(1L);
        expectLastCall().once();
        controller.addOFSwitchListener(listener);
        replay(sw, listener);
        controller.switchDisconnected(sw);
        controller.processUpdateQueueForTesting();
        verify(sw, listener);

        assertNull(controller.getSwitch(1L));
        assertNull(storeClient.getValue(1L));
    }

    /*
     * Remove a nonexisting switch. should be ignored
     */
    @Test
    public void testNonexistingSwitchDisconnected() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(1L).anyTimes();
        expect(sw.getStringId()).andReturn(HexString.toHexString(1L)).anyTimes();
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);
        replay(sw, listener);
        controller.switchDisconnected(sw);
        controller.processUpdateQueueForTesting();
        verify(sw, listener);

        assertNull(controller.getSwitch(1L));
        assertNull(storeClient.getValue(1L));
    }

    /*
     * Try to remove a switch that's different from what's in the active
     * switch map. Should be ignored
     */
    @Test
    public void testSwitchDisconnectedOther() throws Exception {
        IOFSwitch origSw = doActivateNewSwitch(1L, null, null);
        // create a new mock switch
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(1L).anyTimes();
        expect(sw.getStringId()).andReturn(HexString.toHexString(1L)).anyTimes();
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);
        replay(sw, listener);
        controller.switchDisconnected(sw);
        controller.processUpdateQueueForTesting();
        verify(sw, listener);

        assertSame(origSw, controller.getSwitch(1L));
        assertNotNull(storeClient.getValue(1L));
    }



    /*
     * Try to activate a switch that's already active (which can happen if
     * two different switches have the same DPIP or if a switch reconnects
     * while the old TCP connection is still alive
     */
    @Test
    public void testSwitchActivatedWithAlreadyActiveSwitch() throws Exception {
        OFDescriptionStatistics oldDesc = createOFDescriptionStatistics();
        oldDesc.setDatapathDescription("Ye Olde Switch");
        OFDescriptionStatistics newDesc = createOFDescriptionStatistics();
        newDesc.setDatapathDescription("The new Switch");
        OFFeaturesReply featuresReply = createOFFeaturesReply();


        // Setup: add a switch to the controller
        IOFSwitch oldsw = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(oldsw, 0L, oldDesc, featuresReply);
        oldsw.clearAllFlowMods();
        expectLastCall().once();
        replay(oldsw);
        controller.switchActivated(oldsw);
        verify(oldsw);
        // drain the queue, we don't care what's in it
        controller.processUpdateQueueForTesting();
        assertEquals(oldsw, controller.getSwitch(0L));

        // Now the actual test: add a new switch with the same dpid to
        // the controller
        reset(oldsw);
        expect(oldsw.getId()).andReturn(0L).anyTimes();
        oldsw.cancelAllStatisticsReplies();
        expectLastCall().once();
        oldsw.disconnectOutputStream();
        expectLastCall().once();


        IOFSwitch newsw = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(newsw, 0L, newDesc, featuresReply);
        newsw.clearAllFlowMods();
        expectLastCall().once();

        // Strict mock. We need to get the removed notification before the
        // add notification
        IOFSwitchListener listener = createStrictMock(IOFSwitchListener.class);
        listener.switchRemoved(0L);
        listener.switchAdded(0L);
        listener.switchActivated(0L);
        replay(listener);
        controller.addOFSwitchListener(listener);


        replay(newsw, oldsw);
        controller.switchActivated(newsw);
        verify(newsw, oldsw);

        assertEquals(newsw, controller.getSwitch(0L));
        controller.processUpdateQueueForTesting();
        verify(listener);
    }



    /**
    * Tests that you can't remove a switch from the map returned by
    * getSwitches() (because getSwitches should return an unmodifiable
    * map)
    */
   @Test
   public void testRemoveActiveSwitch() {
       IOFSwitch sw = createNiceMock(IOFSwitch.class);
       setupSwitchForAddSwitch(sw, 1L, null, null);
       replay(sw);
       getController().switchActivated(sw);
       assertEquals(sw, getController().getSwitch(1L));
       getController().getAllSwitchMap().remove(1L);
       assertEquals(sw, getController().getSwitch(1L));
       verify(sw);
   }



    private Map<String,Object> getFakeControllerIPRow(String id, String controllerId,
            String type, int number, String discoveredIP ) {
        HashMap<String, Object> row = new HashMap<String,Object>();
        row.put(Controller.CONTROLLER_INTERFACE_ID, id);
        row.put(Controller.CONTROLLER_INTERFACE_CONTROLLER_ID, controllerId);
        row.put(Controller.CONTROLLER_INTERFACE_TYPE, type);
        row.put(Controller.CONTROLLER_INTERFACE_NUMBER, number);
        row.put(Controller.CONTROLLER_INTERFACE_DISCOVERED_IP, discoveredIP);
        return row;
    }

    /**
     * Test notifications for controller node IP changes. This requires
     * synchronization between the main test thread and another thread
     * that runs Controller's main loop and takes / handles updates. We
     * synchronize with wait(timeout) / notifyAll(). We check for the
     * expected condition after the wait returns. However, if wait returns
     * due to the timeout (or due to spurious awaking) and the check fails we
     * might just not have waited long enough. Using a long enough timeout
     * mitigates this but we cannot get rid of the fundamental "issue".
     *
     * @throws Exception
     */
    @Test
    public void testControllerNodeIPChanges() throws Exception {
        class DummyHAListener implements IHAListener {
            public Map<String, String> curControllerNodeIPs;
            public Map<String, String> addedControllerNodeIPs;
            public Map<String, String> removedControllerNodeIPs;
            public int nCalled;

            public DummyHAListener() {
                this.nCalled = 0;
            }

            @Override
            public synchronized void controllerNodeIPsChanged(
                    Map<String, String> curControllerNodeIPs,
                    Map<String, String> addedControllerNodeIPs,
                    Map<String, String> removedControllerNodeIPs) {
                this.curControllerNodeIPs = curControllerNodeIPs;
                this.addedControllerNodeIPs = addedControllerNodeIPs;
                this.removedControllerNodeIPs = removedControllerNodeIPs;
                this.nCalled++;
                notifyAll();
            }

            public void do_assert(int nCalled,
                    Map<String, String> curControllerNodeIPs,
                    Map<String, String> addedControllerNodeIPs,
                    Map<String, String> removedControllerNodeIPs) {
                assertEquals("nCalled is not as expected", nCalled, this.nCalled);
                assertEquals("curControllerNodeIPs is not as expected",
                        curControllerNodeIPs, this.curControllerNodeIPs);
                assertEquals("addedControllerNodeIPs is not as expected",
                        addedControllerNodeIPs, this.addedControllerNodeIPs);
                assertEquals("removedControllerNodeIPs is not as expected",
                        removedControllerNodeIPs, this.removedControllerNodeIPs);

            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public boolean
                    isCallbackOrderingPrereq(HAListenerTypeMarker type,
                                             String name) {
                return false;
            }

            @Override
            public boolean
                    isCallbackOrderingPostreq(HAListenerTypeMarker type,
                                              String name) {
                return false;
            }

            @Override
            public void transitionToMaster() {
            }
        }
        DummyHAListener listener  = new DummyHAListener();
        HashMap<String,String> expectedCurMap = new HashMap<String, String>();
        HashMap<String,String> expectedAddedMap = new HashMap<String, String>();
        HashMap<String,String> expectedRemovedMap = new HashMap<String, String>();

        controller.addHAListener(listener);

        synchronized(listener) {
            // Insert a first entry
            controller.getStorageSourceService()
                .insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    getFakeControllerIPRow("row1", "c1", "Ethernet", 0, "1.1.1.1"));
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedAddedMap.put("c1", "1.1.1.1");
            controller.processUpdateQueueForTesting();
            listener.do_assert(1, expectedCurMap, expectedAddedMap, expectedRemovedMap);

            // Add an interface that we want to ignore.
            controller.getStorageSourceService()
                .insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    getFakeControllerIPRow("row2", "c1", "Ethernet", 1, "1.1.1.2"));
            // TODO: do a different check. This call will have to wait for the timeout
            controller.processUpdateQueueForTesting();
            assertTrue("controllerNodeIPsChanged() should not have been called here",
                    listener.nCalled == 1);

            // Add another entry
            controller.getStorageSourceService()
                .insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    getFakeControllerIPRow("row3", "c2", "Ethernet", 0, "2.2.2.2"));
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedCurMap.put("c2", "2.2.2.2");
            expectedAddedMap.put("c2", "2.2.2.2");
            controller.processUpdateQueueForTesting();
            listener.do_assert(2, expectedCurMap, expectedAddedMap, expectedRemovedMap);


            // Update an entry
            controller.getStorageSourceService()
                .updateRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    "row3", getFakeControllerIPRow("row3", "c2", "Ethernet", 0, "2.2.2.3"));
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedCurMap.put("c2", "2.2.2.3");
            expectedAddedMap.put("c2", "2.2.2.3");
            expectedRemovedMap.put("c2", "2.2.2.2");
            controller.processUpdateQueueForTesting();
            listener.do_assert(3, expectedCurMap, expectedAddedMap, expectedRemovedMap);

            // Delete an entry
            controller.getStorageSourceService()
                .deleteRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME, "row3");
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedRemovedMap.put("c2", "2.2.2.3");
            controller.processUpdateQueueForTesting();
            listener.do_assert(4, expectedCurMap, expectedAddedMap, expectedRemovedMap);
        }
    }

    @Test
    public void testGetControllerNodeIPs() {
        HashMap<String,String> expectedCurMap = new HashMap<String, String>();

        controller.getStorageSourceService()
            .insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                getFakeControllerIPRow("row1", "c1", "Ethernet", 0, "1.1.1.1"));
        controller.getStorageSourceService()
            .insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                getFakeControllerIPRow("row2", "c1", "Ethernet", 1, "1.1.1.2"));
        controller.getStorageSourceService()
            .insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                getFakeControllerIPRow("row3", "c2", "Ethernet", 0, "2.2.2.2"));
        expectedCurMap.put("c1", "1.1.1.1");
        expectedCurMap.put("c2", "2.2.2.2");
        assertEquals("expectedControllerNodeIPs is not as expected",
                expectedCurMap, controller.getControllerNodeIPs());
    }



//
//    public class TestSwitchClass extends OFSwitchImpl {
//    }
//
//    public class Test11SwitchClass extends OFSwitchImpl {
//    }
//
//    @Test
//    public void testBindSwitchToDriver() {
//        controller.addOFSwitchDriver("test", this);
//
//        OFChannelState state = new OFChannelState();
//        OFChannelHandler chdlr =
//                new OFChannelHandler(controller, state);
//
//        // Swith should be bound of OFSwitchImpl (default)
//        state.hsState = OFChannelState.HandshakeState.HELLO;
//        state.hasDescription = true;
//        state.hasGetConfigReply = true;
//        state.switchBindingDone = false;
//        OFDescriptionStatistics desc = new OFDescriptionStatistics();
//        desc.setManufacturerDescription("test switch");
//        desc.setHardwareDescription("version 0.9");
//        state.description = desc;
//        OFFeaturesReply featuresReply = new OFFeaturesReply();
//        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
//        state.featuresReply = featuresReply;
//
//        chdlr.bindSwitchToDriver();
//        assertTrue(chdlr.sw instanceof OFSwitchImpl);
//        assertTrue(!(chdlr.sw instanceof TestSwitchClass));
//
//        // Switch should be bound to TestSwitchImpl
//        state.switchBindingDone = false;
//        desc.setManufacturerDescription("test1 switch");
//        desc.setHardwareDescription("version 1.0");
//        state.description = desc;
//        state.featuresReply = featuresReply;
//
//        chdlr.bindSwitchToDriver();
//        assertTrue(chdlr.sw instanceof TestSwitchClass);
//
//        // Switch should be bound to Test11SwitchImpl
//        state.switchBindingDone = false;
//        desc.setManufacturerDescription("test11 switch");
//        desc.setHardwareDescription("version 1.1");
//        state.description = desc;
//        state.featuresReply = featuresReply;
//
//        chdlr.bindSwitchToDriver();
//        assertTrue(chdlr.sw instanceof Test11SwitchClass);
//    }
//
//    @Test
//    public void testBindSwitchOrder() {
//        List<String> order = new ArrayList<String>(3);
//        controller.addOFSwitchDriver("", this);
//        controller.addOFSwitchDriver("test switch", this);
//        controller.addOFSwitchDriver("test", this);
//        order.add("test switch");
//        order.add("test");
//        order.add("");
//        test_bind_order = true;
//
//        OFChannelState state = new OFChannelState();
//        OFChannelHandler chdlr =
//                new OFChannelHandler(controller, state);
//        chdlr.sw = null;
//
//        // Swith should be bound of OFSwitchImpl (default)
//        state.hsState = OFChannelState.HandshakeState.HELLO;
//        state.hasDescription = true;
//        state.hasGetConfigReply = true;
//        state.switchBindingDone = false;
//        OFDescriptionStatistics desc = new OFDescriptionStatistics();
//        desc.setManufacturerDescription("test switch");
//        desc.setHardwareDescription("version 0.9");
//        state.description = desc;
//        OFFeaturesReply featuresReply = new OFFeaturesReply();
//        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
//        state.featuresReply = featuresReply;
//
//        chdlr.bindSwitchToDriver();
//        assertTrue(chdlr.sw instanceof OFSwitchImpl);
//        assertTrue(!(chdlr.sw instanceof TestSwitchClass));
//        // Verify bind_order is called as expected
//        assertTrue(order.equals(bind_order));
//        test_bind_order = false;
//        bind_order = null;
//   }
//
//    @Test
//    public void testChannelDisconnected() throws Exception {
//        OFChannelState state = new OFChannelState();
//        state.hsState = OFChannelState.HandshakeState.READY;
//        OFChannelHandler chdlr = new OFChannelHandler(controller, state);
//        chdlr.sw = createMock(IOFSwitch.class);
//
//        // Switch is active
//        expect(chdlr.sw.getId()).andReturn(0L).anyTimes();
//        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:00")
//                    .anyTimes();
//        chdlr.sw.cancelAllStatisticsReplies();
//        chdlr.sw.setConnected(false);
//        expect(chdlr.sw.isConnected()).andReturn(true);
//
//        controller.connectedSwitches.add(chdlr.sw);
//        controller.activeSwitches.put(0L, chdlr.sw);
//
//        replay(chdlr.sw);
//        chdlr.channelDisconnected(null, null);
//        verify(chdlr.sw);
//
//        // Switch is connected but not active
//        reset(chdlr.sw);
//        expect(chdlr.sw.getId()).andReturn(0L).anyTimes();
//        chdlr.sw.setConnected(false);
//        replay(chdlr.sw);
//        chdlr.channelDisconnected(null, null);
//        verify(chdlr.sw);
//
//        // Not in ready state
//        state.hsState = HandshakeState.START;
//        reset(chdlr.sw);
//        replay(chdlr.sw);
//        chdlr.channelDisconnected(null, null);
//        verify(chdlr.sw);
//
//        // Switch is null
//        state.hsState = HandshakeState.READY;
//        chdlr.sw = null;
//        chdlr.channelDisconnected(null, null);
//    }
//
//    /*
//    @Test
//    public void testRoleChangeForSerialFailoverSwitch() throws Exception {
//        OFSwitchImpl newsw = createMock(OFSwitchImpl.class);
//        expect(newsw.getId()).andReturn(0L).anyTimes();
//        expect(newsw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
//        Channel channel2 = createMock(Channel.class);
//        expect(newsw.getChannel()).andReturn(channel2);
//
//        // newsw.role is null because the switch does not support
//        // role request messages
//        expect(newsw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
//                        .andReturn(false);
//        // switch is connected
//        controller.connectedSwitches.add(newsw);
//
//        // the switch should get disconnected when role is changed to SLAVE
//        expect(channel2.close()).andReturn(null);
//
//        replay(newsw, channel2);
//        controller.setRole(Role.SLAVE);
//        verify(newsw,  channel2);
//    }
//    */
//
//    @Test
//    public void testRoleNotSupportedError() throws Exception {
//        int xid = 424242;
//        OFChannelState state = new OFChannelState();
//        state.hsState = HandshakeState.READY;
//        OFChannelHandler chdlr = new OFChannelHandler(controller, state);
//        chdlr.sw = createMock(IOFSwitch.class);
//        Channel ch = createMock(Channel.class);
//
//        // the error returned when role request message is not supported by sw
//        OFError msg = new OFError();
//        msg.setType(OFType.ERROR);
//        msg.setXid(xid);
//        msg.setErrorType(OFErrorType.OFPET_BAD_REQUEST);
//
//        // the switch connection should get disconnected when the controller is
//        // in SLAVE mode and the switch does not support role-request messages
//        controller.role = Role.SLAVE;
//        setupPendingRoleRequest(chdlr.sw, xid, controller.role, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(Role.SLAVE, false);
//        expect(chdlr.sw.getHARole()).andReturn(Role.SLAVE);
//        chdlr.sw.disconnectOutputStream();
//
//        replay(ch, chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(ch, chdlr.sw);
//        assertTrue("activeSwitches must be empty",
//                   controller.activeSwitches.isEmpty());
//        reset(ch, chdlr.sw);
//
//        // We are MASTER, the switch should be added to the list of active
//        // switches.
//        controller.role = Role.MASTER;
//        setupPendingRoleRequest(chdlr.sw, xid, controller.role, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(controller.role, false);
//        setupSwitchForAddSwitch(chdlr.sw, 0L);
//        chdlr.sw.clearAllFlowMods();
//        expect(chdlr.sw.getHARole()).andReturn(null).anyTimes();
//        replay(ch, chdlr.sw);
//
//        chdlr.processOFMessage(msg);
//        verify(ch, chdlr.sw);
//        assertSame("activeSwitches must contain this switch",
//                   chdlr.sw, controller.activeSwitches.get(0L));
//        reset(ch, chdlr.sw);
//
//    }
//
//
//    @Test
//    public void testVendorMessageUnknown() throws Exception {
//        // Check behavior with an unknown vendor id
//        // Ensure that vendor message listeners get called, even for Vendors
//        // unknown to floodlight. It is the responsibility of the listener to
//        // discard unknown vendors.
//        OFChannelState state = new OFChannelState();
//        state.hsState = HandshakeState.READY;
//        OFChannelHandler chdlr = new OFChannelHandler(controller, state);
//        OFVendor msg = new OFVendor();
//        msg.setVendor(0);
//        IOFSwitch sw = createMock(IOFSwitch.class);
//        chdlr.sw = sw;
//        controller.activeSwitches.put(1L, sw);
//
//        // prepare the Vendor Message Listener expectations
//        ListenerDispatcher<OFType, IOFMessageListener> ld =
//                new ListenerDispatcher<OFType, IOFMessageListener>();
//        IOFMessageListener ml = createMock(IOFMessageListener.class);
//        expect(ml.getName()).andReturn("Dummy").anyTimes();
//        expect(ml.isCallbackOrderingPrereq((OFType)anyObject(),
//                (String)anyObject())).andReturn(false).anyTimes();
//        expect(ml.isCallbackOrderingPostreq((OFType)anyObject(),
//                (String)anyObject())).andReturn(false).anyTimes();
//        expect(ml.receive(eq(sw), eq(msg), isA(FloodlightContext.class))).
//                andReturn(Command.CONTINUE).once();
//        controller.messageListeners.put(OFType.VENDOR, ld);
//
//        // prepare the switch and lock expectations
//        Lock lock = createNiceMock(Lock.class);
//        expect(sw.getListenerReadLock()).andReturn(lock).anyTimes();
//        expect(sw.isConnected()).andReturn(true).anyTimes();
//        expect(sw.getHARole()).andReturn(Role.MASTER).anyTimes();
//        expect(sw.getId()).andReturn(1L).anyTimes();
//
//        // test
//        replay(chdlr.sw, lock, ml);
//        ld.addListener(OFType.VENDOR, ml);
//        chdlr.processOFMessage(msg);
//    }
//
//    @Test
//    public void testErrorEPERM() throws Exception {
//        // Check behavior with a BAD_REQUEST/EPERM error
//        // Ensure controller attempts to reset switch role.
//        OFChannelState state = new OFChannelState();
//        state.hsState = HandshakeState.READY;
//        OFChannelHandler chdlr = new OFChannelHandler(controller, state);
//        OFError error = new OFError();
//        error.setErrorType(OFErrorType.OFPET_BAD_REQUEST);
//        error.setErrorCode(OFBadRequestCode.OFPBRC_EPERM);
//        IOFSwitch sw = createMock(IOFSwitch.class);
//        chdlr.sw = sw;
//        controller.activeSwitches.put(1L, sw);
//
//        // prepare the switch and lock expectations
//        Lock lock = createNiceMock(Lock.class);
//        expect(sw.getListenerReadLock()).andReturn(lock).anyTimes();
//        expect(sw.isConnected()).andReturn(true).anyTimes();
//        expect(sw.getHARole()).andReturn(Role.MASTER).anyTimes();
//        expect(sw.getId()).andReturn(1L).anyTimes();
//
//        // Make sure controller attempts to reset switch master
//        expect(sw.getAttribute("supportsNxRole")).andReturn(true).anyTimes();
//        expect(sw.getNextTransactionId()).andReturn(0).anyTimes();
//        sw.write(EasyMock.<List<OFMessage>> anyObject(),
//                 (FloodlightContext)anyObject());
//
//        // test
//        replay(sw, lock);
//        chdlr.processOFMessage(error);
//        DelayQueue<RoleChangeTask> pendingTasks =
//                controller.roleChanger.pendingTasks;
//        synchronized (pendingTasks) {
//            RoleChangeTask t;
//            while ((t = pendingTasks.peek()) == null ||
//                    RoleChanger.RoleChangeTask.Type.TIMEOUT != t.type) {
//                pendingTasks.wait();
//            }
//        }
//        // Now there should be exactly one timeout task pending
//        assertEquals(1, pendingTasks.size());
//   }
//
//    // Helper function.
//    protected OFChannelHandler getChannelHandlerForRoleReplyTest() {
//        OFChannelState state = new OFChannelState();
//        state.hsState = HandshakeState.READY;
//        OFChannelHandler chdlr = new OFChannelHandler(controller, state);
//        chdlr.sw = createMock(IOFSwitch.class);
//        return chdlr;
//    }
//
//    // Helper function
//    protected OFVendor getRoleReplyMsgForRoleReplyTest(int xid, int nicira_role) {
//        OFVendor msg = new OFVendor();
//        msg.setXid(xid);
//        msg.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
//        OFRoleReplyVendorData roleReplyVendorData =
//                new OFRoleReplyVendorData(OFRoleReplyVendorData.NXT_ROLE_REPLY);
//        msg.setVendorData(roleReplyVendorData);
//        roleReplyVendorData.setRole(nicira_role);
//        return msg;
//    }
//
//    // Helper function
//    protected void setupPendingRoleRequest(IOFSwitch sw, int xid, Role role,
//            long cookie) {
//        LinkedList<PendingRoleRequestEntry> pendingList =
//                new LinkedList<PendingRoleRequestEntry>();
//        controller.roleChanger.pendingRequestMap.put(sw, pendingList);
//        PendingRoleRequestEntry entry =
//                new PendingRoleRequestEntry(xid, role, cookie);
//        pendingList.add(entry);
//    }
//
//    /** invalid role in role reply */
//    @Test
//    public void testNiciraRoleReplyInvalidRole()
//                    throws Exception {
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        Channel ch = createMock(Channel.class);
//        chdlr.sw.disconnectOutputStream();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid, 232323);
//        replay(chdlr.sw, ch);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw, ch);
//    }
//
//    /** First role reply message received: transition from slave to master */
//    @Test
//    public void testNiciraRoleReplySlave2MasterFristTime()
//                    throws Exception {
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
//                                       OFRoleReplyVendorData.NX_ROLE_MASTER);
//
//        setupPendingRoleRequest(chdlr.sw, xid, Role.MASTER, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(Role.MASTER, true);
//        setupSwitchForAddSwitch(chdlr.sw, 1L);
//        chdlr.sw.clearAllFlowMods();
//        replay(chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw);
//        assertSame("activeSwitches must contain this switch",
//                   chdlr.sw, controller.activeSwitches.get(1L));
//    }
//
//
//    /** Not first role reply message received: transition from slave to master */
//    @Test
//    public void testNiciraRoleReplySlave2MasterNotFristTime()
//                    throws Exception {
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
//                                       OFRoleReplyVendorData.NX_ROLE_MASTER);
//
//        setupPendingRoleRequest(chdlr.sw, xid, Role.MASTER, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(Role.SLAVE);
//        chdlr.sw.setHARole(Role.MASTER, true);
//        setupSwitchForAddSwitch(chdlr.sw, 1L);
//        // Flow table shouldn't be wipe
//        replay(chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw);
//        assertSame("activeSwitches must contain this switch",
//                   chdlr.sw, controller.activeSwitches.get(1L));
//    }
//
//    /** transition from slave to equal */
//    @Test
//    public void testNiciraRoleReplySlave2Equal()
//                    throws Exception {
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
//                                       OFRoleReplyVendorData.NX_ROLE_OTHER);
//
//        setupPendingRoleRequest(chdlr.sw, xid, Role.EQUAL, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(Role.EQUAL, true);
//        setupSwitchForAddSwitch(chdlr.sw, 1L);
//        chdlr.sw.clearAllFlowMods();
//        replay(chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw);
//        assertSame("activeSwitches must contain this switch",
//                   chdlr.sw, controller.activeSwitches.get(1L));
//    };
//
//    @Test
//    /** Slave2Slave transition ==> no change */
//    public void testNiciraRoleReplySlave2Slave() throws Exception{
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
//                                       OFRoleReplyVendorData.NX_ROLE_SLAVE);
//
//        setupPendingRoleRequest(chdlr.sw, xid, Role.SLAVE, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(Role.SLAVE, true);
//        expect(chdlr.sw.getId()).andReturn(1L).anyTimes();
//        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:01")
//                    .anyTimes();
//        // don't add switch to activeSwitches ==> slave2slave
//        replay(chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw);
//        assertTrue("activeSwitches must be empty",
//                   controller.activeSwitches.isEmpty());
//    }
//
//    @Test
//    /** Equal2Master transition ==> no change */
//    public void testNiciraRoleReplyEqual2Master() throws Exception{
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
//                                       OFRoleReplyVendorData.NX_ROLE_MASTER);
//
//        setupPendingRoleRequest(chdlr.sw, xid, Role.MASTER, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(Role.MASTER, true);
//        expect(chdlr.sw.getId()).andReturn(1L).anyTimes();
//        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:01")
//                    .anyTimes();
//        controller.activeSwitches.put(1L, chdlr.sw);
//        // Must not clear flow mods
//        replay(chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw);
//        assertSame("activeSwitches must contain this switch",
//                   chdlr.sw, controller.activeSwitches.get(1L));
//    }
//
//    @Test
//    public void testNiciraRoleReplyMaster2Slave()
//                    throws Exception {
//        int xid = 424242;
//        OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
//        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
//                                       OFRoleReplyVendorData.NX_ROLE_SLAVE);
//
//        setupPendingRoleRequest(chdlr.sw, xid, Role.SLAVE, 123456);
//        expect(chdlr.sw.getHARole()).andReturn(null);
//        chdlr.sw.setHARole(Role.SLAVE, true);
//        expect(chdlr.sw.getId()).andReturn(1L).anyTimes();
//        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:01")
//                    .anyTimes();
//        controller.activeSwitches.put(1L, chdlr.sw);
//        expect(chdlr.sw.getHARole()).andReturn(Role.SLAVE).anyTimes();
//        expect(chdlr.sw.isConnected()).andReturn(true);
//        chdlr.sw.cancelAllStatisticsReplies();
//        replay(chdlr.sw);
//        chdlr.processOFMessage(msg);
//        verify(chdlr.sw);
//        assertTrue("activeSwitches must be empty",
//                   controller.activeSwitches.isEmpty());
//    }
//
//    /**
//     * Tests that you can't remove a switch from the active
//     * switch list.
//     * @throws Exception
//     */
//    @Test
//    public void testRemoveActiveSwitch() {
//        IOFSwitch sw = createNiceMock(IOFSwitch.class);
//        boolean exceptionThrown = false;
//        expect(sw.getId()).andReturn(1L).anyTimes();
//        replay(sw);
//        getController().activeSwitches.put(sw.getId(), sw);
//        try {
//            getController().getSwitches().remove(1L);
//        } catch (UnsupportedOperationException e) {
//            exceptionThrown = true;
//        }
//        assertTrue(exceptionThrown);
//        verify(sw);
//    }
//
//    public void verifyPortChangedUpdateInQueue(IOFSwitch sw) throws Exception {
//        assertEquals(1, controller.updates.size());
//        IUpdate update = controller.updates.take();
//        assertEquals(true, update instanceof SwitchUpdate);
//        SwitchUpdate swUpdate = (SwitchUpdate)update;
//        assertEquals(sw, swUpdate.sw);
//        assertEquals(SwitchUpdateType.PORTCHANGED, swUpdate.switchUpdateType);
//    }
//
//    /*
//     * Test handlePortStatus()
//     * TODO: test correct updateStorage behavior!
//     */
//    @Test
//    public void testHandlePortStatus() throws Exception {
//        IOFSwitch sw = createMock(IOFSwitch.class);
//        OFPhysicalPort port = new OFPhysicalPort();
//        port.setName("myPortName1");
//        port.setPortNumber((short)42);
//
//        OFPortStatus ofps = new OFPortStatus();
//        ofps.setDesc(port);
//
//        ofps.setReason((byte)OFPortReason.OFPPR_ADD.ordinal());
//        sw.setPort(port);
//        expectLastCall().once();
//        replay(sw);
//        controller.handlePortStatusMessage(sw, ofps);
//        verify(sw);
//        verifyPortChangedUpdateInQueue(sw);
//        reset(sw);
//
//        ofps.setReason((byte)OFPortReason.OFPPR_MODIFY.ordinal());
//        sw.setPort(port);
//        expectLastCall().once();
//        replay(sw);
//        controller.handlePortStatusMessage(sw, ofps);
//        verify(sw);
//        verifyPortChangedUpdateInQueue(sw);
//        reset(sw);
//
//        ofps.setReason((byte)OFPortReason.OFPPR_DELETE.ordinal());
//        sw.deletePort(port.getPortNumber());
//        expectLastCall().once();
//        replay(sw);
//        controller.handlePortStatusMessage(sw, ofps);
//        verify(sw);
//        verifyPortChangedUpdateInQueue(sw);
//        reset(sw);
//    }
//
//    @Override
//    public IOFSwitch getOFSwitchImpl(String regis_desc,
//            OFDescriptionStatistics description) {
//        // If testing bind order, just record registered desc string
//        if (test_bind_order) {
//            if (bind_order == null) {
//                bind_order = new ArrayList<String>();
//            }
//            bind_order.add(regis_desc);
//            return null;
//        }
//        String hw_desc = description.getHardwareDescription();
//        if (hw_desc.equals("version 1.1")) {
//            return new Test11SwitchClass();
//        }
//        if (hw_desc.equals("version 1.0")) {
//            return new TestSwitchClass();
//        }
//        return null;
//    }
//
//    private void setupSwitchForDispatchTest(IOFSwitch sw,
//                                            boolean isConnected,
//                                            Role role) {
//        Lock lock = createNiceMock(Lock.class);
//        expect(sw.getId()).andReturn(1L).anyTimes();
//        expect(sw.getStringId()).andReturn("00:00:00:00:00:01").anyTimes();
//        expect(sw.getListenerReadLock()).andReturn(lock).anyTimes();
//        expect(sw.isConnected()).andReturn(isConnected).anyTimes();
//        expect(sw.getHARole()).andReturn(role).anyTimes();
//        replay(lock);
//
//    }
//
//    @Test
//    public void testMessageDispatch() throws Exception {
//        // Mock a dummy packet in
//        // Build our test packet
//        IPacket testPacket = new Ethernet()
//        .setSourceMACAddress("00:44:33:22:11:00")
//        .setDestinationMACAddress("00:11:22:33:44:55")
//        .setEtherType(Ethernet.TYPE_ARP)
//        .setPayload(
//                new ARP()
//                .setHardwareType(ARP.HW_TYPE_ETHERNET)
//                .setProtocolType(ARP.PROTO_TYPE_IP)
//                .setHardwareAddressLength((byte) 6)
//                .setProtocolAddressLength((byte) 4)
//                .setOpCode(ARP.OP_REPLY)
//                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
//                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
//                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
//                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
//        byte[] testPacketSerialized = testPacket.serialize();
//
//        // Build the PacketIn
//        OFPacketIn pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
//                .setBufferId(-1)
//                .setInPort((short) 1)
//                .setPacketData(testPacketSerialized)
//                .setReason(OFPacketInReason.NO_MATCH)
//                .setTotalLength((short) testPacketSerialized.length);
//
//
//        // Mock switch and add to data structures
//        IOFSwitch sw = createMock(IOFSwitch.class);
//
//        controller.connectedSwitches.add(sw);
//
//        // create a channel handler
//        OFChannelState state = new OFChannelState();
//        state.hsState = HandshakeState.READY;
//        OFChannelHandler chdlr = new OFChannelHandler(controller, state);
//        chdlr.sw = sw;
//
//        // mock role changer
//        RoleChanger roleChanger = createMock(RoleChanger.class);
//        roleChanger.submitRequest(eq(controller.connectedSwitches),
//                                  anyObject(Role.class));
//        expectLastCall().anyTimes();
//        controller.roleChanger = roleChanger;
//
//
//        // Mock message listener and add
//        IOFMessageListener listener = createNiceMock(IOFMessageListener.class);
//        expect(listener.getName()).andReturn("foobar").anyTimes();
//        replay(listener);
//        controller.addOFMessageListener(OFType.PACKET_IN, listener);
//        resetToStrict(listener);
//
//
//        assertEquals("Check that update queue is empty", 0,
//                    controller.updates.size());
//
//
//        replay(roleChanger);
//
//        //-------------------
//        // Test 1: role is master, switch is master and in activeMap
//        // we expect the msg to be dispatched
//        reset(sw);
//        resetToDefault(listener);
//        controller.activeSwitches.put(1L, sw);
//        setupSwitchForDispatchTest(sw, true, Role.MASTER);
//        listener.receive(same(sw), same(pi),
//                         anyObject(FloodlightContext.class));
//        expectLastCall().andReturn(Command.STOP).once();
//        replay(sw, listener);
//        chdlr.processOFMessage(pi);
//        verify(sw, listener);
//        assertEquals(0, controller.updates.size());
//
//
//        //-------------------
//        // Test 1b: role is master, switch is master and in activeMap
//        // but switch is not connected
//        // no message dispatched
//        reset(sw);
//        resetToDefault(listener);
//        controller.activeSwitches.put(1L, sw);
//        setupSwitchForDispatchTest(sw, false, Role.MASTER);
//        replay(sw, listener);
//        chdlr.processOFMessage(pi);
//        verify(sw, listener);
//        assertEquals(0, controller.updates.size());
//
//
//        //-------------------
//        // Test 1c: role is master, switch is slave and in activeMap
//        // no message dispatched
//        reset(sw);
//        resetToDefault(listener);
//        controller.activeSwitches.put(1L, sw);
//        setupSwitchForDispatchTest(sw, true, Role.SLAVE);
//        replay(sw, listener);
//        chdlr.processOFMessage(pi);
//        verify(sw, listener);
//        assertEquals(0, controller.updates.size());
//
//
//        //-------------------
//        // Test 1d: role is master, switch is master but not in activeMap
//        // we expect the msg to be dispatched
//        reset(sw);
//        resetToDefault(listener);
//        controller.activeSwitches.remove(1L);
//        setupSwitchForDispatchTest(sw, true, Role.MASTER);
//        replay(sw, listener);
//        chdlr.processOFMessage(pi);
//        verify(sw, listener);
//        assertEquals(0, controller.updates.size());
//
//
//
//        //-------------------
//        // Test 2: check correct dispatch and HA notification behavior
//        // We set the role to slave but do not notify the clients
//        reset(sw);
//        resetToDefault(listener);
//        controller.activeSwitches.put(1L, sw);
//        setupSwitchForDispatchTest(sw, true, Role.MASTER);
//        listener.receive(same(sw), same(pi),
//                         anyObject(FloodlightContext.class));
//        expectLastCall().andReturn(Command.STOP).once();
//        replay(sw, listener);
//        controller.setRole(Role.SLAVE, "Testing");
//        controller.doSetRole();  // avoid the wait
//        chdlr.processOFMessage(pi);
//        verify(sw, listener);
//        assertEquals(1, controller.updates.size());
//
//        // Now notify listeners
//        Controller.IUpdate upd = controller.updates.poll(1, TimeUnit.NANOSECONDS);
//        assertTrue("Check that update is HARoleUpdate",
//                   upd instanceof Controller.HARoleUpdate);
//        upd.dispatch();
//        resetToDefault(listener);
//        replay(listener);
//        chdlr.processOFMessage(pi);
//        verify(listener);
//        assertEquals(0, controller.updates.size());
//
//        // transition back to master but don't notify yet
//        resetToDefault(listener);
//        replay(listener);
//        controller.setRole(Role.MASTER, "Testing");
//        controller.doSetRole(); // avoid the wait
//        chdlr.processOFMessage(pi);
//        verify(listener);
//        assertEquals(1, controller.updates.size());
//
//        // now notify listeners
//        upd = controller.updates.poll(1, TimeUnit.NANOSECONDS);
//        assertTrue("Check that update is HARoleUpdate",
//                   upd instanceof Controller.HARoleUpdate);
//        upd.dispatch();
//        resetToDefault(listener);
//        listener.receive(same(sw), same(pi),
//                         anyObject(FloodlightContext.class));
//        expectLastCall().andReturn(Command.STOP).once();
//        replay(listener);
//        chdlr.processOFMessage(pi);
//        verify(listener);
//        assertEquals(0, controller.updates.size());
//
//        verify(sw);
//    }
//
//
//    /*
//     * Test correct timing behavior between HA Role notification and dispatching
//     * OFMessages to listeners.
//     * When transitioning to SLAVE: stop dispatching message before sending
//     *    notifications
//     * When transitioning to MASTER: start dispatching messages after sending
//     *     notifications
//     * (This implies that messages should not be dispatched while the
//     * notifications are being sent).
//     *
//     * We encapsulate the logic for this in a class that implements both
//     * IHAListener and IOFMessageListener. Then we inject an OFMessage fom
//     * the IHAListener and check that it gets dropped correctly.
//     */
//    @Test
//    public void testRoleNotifcationAndMessageDispatch() throws Exception {
//        class TestRoleNotificationsAndDispatch implements IHAListener, IOFMessageListener {
//            OFPacketIn pi;
//            OFChannelHandler chdlr;
//            IOFSwitch sw;
//            private boolean haveReceived;
//            private boolean doInjectMessageFromHAListener;
//
//            public TestRoleNotificationsAndDispatch() {
//                IPacket testPacket = new Ethernet()
//                .setSourceMACAddress("00:44:33:22:11:00")
//                .setDestinationMACAddress("00:11:22:33:44:55")
//                .setEtherType(Ethernet.TYPE_ARP)
//                .setPayload(
//                        new ARP()
//                        .setHardwareType(ARP.HW_TYPE_ETHERNET)
//                        .setProtocolType(ARP.PROTO_TYPE_IP)
//                        .setHardwareAddressLength((byte) 6)
//                        .setProtocolAddressLength((byte) 4)
//                        .setOpCode(ARP.OP_REPLY)
//                        .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
//                        .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
//                        .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
//                        .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
//                byte[] testPacketSerialized = testPacket.serialize();
//
//                // Build the PacketIn
//                pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
//                        .setBufferId(-1)
//                        .setInPort((short) 1)
//                        .setPacketData(testPacketSerialized)
//                        .setReason(OFPacketInReason.NO_MATCH)
//                        .setTotalLength((short) testPacketSerialized.length);
//
//                // Mock switch and add to data structures
//                sw = createMock(IOFSwitch.class);
//                controller.connectedSwitches.add(sw);
//                controller.activeSwitches.put(1L, sw);
//                setupSwitchForDispatchTest(sw, true, Role.MASTER);
//                replay(sw);
//
//                // create a channel handler
//                OFChannelState state = new OFChannelState();
//                state.hsState = HandshakeState.READY;
//                chdlr = new OFChannelHandler(controller, state);
//                chdlr.sw = this.sw;
//
//                // add ourself as listeners
//                controller.addOFMessageListener(OFType.PACKET_IN, this);
//                controller.addHAListener(this);
//            }
//
//
//            private void injectMessage(boolean shouldReceive) throws Exception {
//                haveReceived = false;
//                chdlr.processOFMessage(pi);
//                assertEquals(shouldReceive, haveReceived);
//            }
//
//            public void transitionToSlave() throws Exception {
//                IUpdate update;
//
//                // Bring controller into well defined state for MASTER
//                doInjectMessageFromHAListener = false;
//                update = controller.new HARoleUpdate(Role.MASTER, Role.SLAVE);
//                update.dispatch();
//                doInjectMessageFromHAListener = true;
//
//
//                // inject message. Listener called
//                injectMessage(true);
//                // Dispatch update
//                update = controller.new HARoleUpdate(Role.SLAVE, Role.MASTER);
//                update.dispatch();
//                // inject message. Listener not called
//                injectMessage(false);
//            }
//
//            public void transitionToMaster() throws Exception {
//                IUpdate update;
//
//                // Bring controller into well defined state for SLAVE
//                doInjectMessageFromHAListener = false;
//                update = controller.new HARoleUpdate(Role.SLAVE, Role.MASTER);
//                update.dispatch();
//                doInjectMessageFromHAListener = true;
//
//
//                // inject message. Listener not called
//                injectMessage(false);
//                // Dispatch update
//                update = controller.new HARoleUpdate(Role.MASTER, Role.SLAVE);
//                update.dispatch();
//                // inject message. Listener called
//                injectMessage(true);
//            }
//
//            //---------------
//            // IHAListener
//            //---------------
//            @Override
//            public void roleChanged(Role oldRole, Role newRole) {
//                try {
//                    if (doInjectMessageFromHAListener)
//                        injectMessage(false);
//                } catch (Exception e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }
//
//            @Override
//            public
//                    void
//                    controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
//                                             Map<String, String> addedControllerNodeIPs,
//                                             Map<String, String> removedControllerNodeIPs) {
//                // TODO Auto-generated method stub
//            }
//
//            //-------------------------
//            // IOFMessageListener
//            //-------------------------
//            @Override
//            public String getName() {
//                return "FooBar";
//            }
//            @Override
//            public boolean isCallbackOrderingPrereq(OFType type, String name) {
//                return false;
//            }
//            @Override
//            public boolean isCallbackOrderingPostreq(OFType type, String name) {
//                return false;
//            }
//            @Override
//            public Command receive(IOFSwitch sw,
//                                   OFMessage msg,
//                                   FloodlightContext cntx) {
//                haveReceived = true;
//                return Command.STOP;
//            }
//        }
//
//        TestRoleNotificationsAndDispatch x = new TestRoleNotificationsAndDispatch();
//        x.transitionToSlave();
//        x.transitionToMaster();
//
//    }

}
