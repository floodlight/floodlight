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
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IReadyForReconcileListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.OFMessageFilterManager;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.SwitchSyncRepresentation;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.debugcounter.DebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugevent.DebugEvent;
import net.floodlightcontroller.debugevent.IDebugEventService;
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
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMessage;
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

        // TODO: should mock IDebugCounterService and make sure
        // the expected counters are updated.
        DebugCounter debugCounterService = new DebugCounter();
        fmc.addService(IDebugCounterService.class, debugCounterService);

        DebugEvent debugEventService = new DebugEvent();
        fmc.addService(IDebugEventService.class, debugEventService);

        tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);

        syncService = new MockSyncService();
        fmc.addService(ISyncService.class, syncService);



        ppt.init(fmc);
        restApi.init(fmc);
        memstorage.init(fmc);
        tp.init(fmc);
        debugCounterService.init(fmc);
        debugEventService.init(fmc);
        syncService.init(fmc);
        cm.init(fmc);

        ppt.startUp(fmc);
        restApi.startUp(fmc);
        memstorage.startUp(fmc);
        tp.startUp(fmc);
        debugCounterService.startUp(fmc);
        debugEventService.startUp(fmc);
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
        assertTrue("Updates left in controller update queue",
                   controller.isUpdateQueueEmptyForTesting());
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


    /** Set the mock expectations for sw when sw is passed to addSwitch
     * The same expectations can be used when a new SwitchSyncRepresentation
     * is created from the given mocked switch */
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
        List<ImmutablePort> ports =
                ImmutablePort.immutablePortListOf(featuresReply.getPorts());

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
                .andReturn(ports).atLeastOnce();
        expect(sw.attributeEquals(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true))
                .andReturn(false).anyTimes();
        expect(sw.getInetAddress()).andReturn(null).anyTimes();
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
     * Test message dispatching to OFMessageListeners. Test ordering of
     * listeners for different types (we do this implicitly by using
     * STOP and CONTINUE and making sure the processing stops at the right
     * place)
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

        // Setup listener orderings
        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").anyTimes();
        setupListenerOrdering(test1);

        IOFMessageListener test2 = createMock(IOFMessageListener.class);
        expect(test2.getName()).andReturn("test2").anyTimes();
        // using a postreq and a prereq ordering here
        expect(test2.isCallbackOrderingPrereq(OFType.PACKET_IN, "test1"))
                .andReturn(true).atLeastOnce();
        expect(test2.isCallbackOrderingPostreq(OFType.FLOW_MOD, "test1"))
                .andReturn(true).atLeastOnce();
        setupListenerOrdering(test2);


        IOFMessageListener test3 = createMock(IOFMessageListener.class);
        expect(test3.getName()).andReturn("test3").anyTimes();
        expect(test3.isCallbackOrderingPrereq((OFType)anyObject(), eq("test1")))
                .andReturn(true).atLeastOnce();
        expect(test3.isCallbackOrderingPrereq((OFType)anyObject(), eq("test2")))
                .andReturn(true).atLeastOnce();
        setupListenerOrdering(test3);


        // Ordering: PacketIn: test1 -> test2 -> test3
        //           FlowMod:  test2 -> test1
        replay(test1, test2, test3);
        controller.addOFMessageListener(OFType.PACKET_IN, test1);
        controller.addOFMessageListener(OFType.PACKET_IN, test3);
        controller.addOFMessageListener(OFType.PACKET_IN, test2);
        controller.addOFMessageListener(OFType.FLOW_MOD, test1);
        controller.addOFMessageListener(OFType.FLOW_MOD, test2);
        verify(test1);
        verify(test2);
        verify(test3);


        replay(sw);


        //------------------
        // Test PacketIn handling: all listeners return CONTINUE
        reset(test1, test2, test3);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        expect(test2.receive(eq(sw), eq(pi), isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        expect(test3.receive(eq(sw), eq(pi), isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        replay(test1, test2, test3);
        controller.handleMessage(sw, pi, null);
        verify(test1);
        verify(test2);
        verify(test3);

        //------------------
        // Test PacketIn handling: with a thrown exception.
        reset(test1, test2, test3);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        expect(test2.receive(eq(sw), eq(pi), isA(FloodlightContext.class)))
                .andThrow(new RuntimeException("This is NOT an error! We " +
                                           "are testing exception catching."));
        // expect no calls to test3.receive() since test2.receive throws
        // an exception
        replay(test1, test2, test3);
        try {
            controller.handleMessage(sw, pi, null);
            fail("Expected exception was not thrown!");
        } catch (RuntimeException e) {
            assertTrue("The caught exception was not the expected one",
                       e.getMessage().startsWith("This is NOT an error!"));
        }
        verify(test1);
        verify(test2);
        verify(test3);


        //------------------
        // Test PacketIn handling: test1 return Command.STOP
        reset(test1, test2, test3);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class)))
                .andReturn(Command.STOP);
        // expect no calls to test3.receive() and test2.receive since
        // test1.receive returns STOP
        replay(test1, test2, test3);
        controller.handleMessage(sw, pi, null);
        verify(test1);
        verify(test2);
        verify(test3);

        OFFlowMod fm = (OFFlowMod)
                BasicFactory.getInstance().getMessage(OFType.FLOW_MOD);

        //------------------
        // Test FlowMod handling: all listeners return CONTINUE
        reset(test1, test2, test3);
        expect(test1.receive(eq(sw), eq(fm), isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        expect(test2.receive(eq(sw), eq(fm), isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        // test3 is not a listener for FlowMod
        replay(test1, test2, test3);
        controller.handleMessage(sw, fm, null);
        verify(test1);
        verify(test2);
        verify(test3);

        //------------------
        // Test FlowMod handling: test2 (first listener) return STOP
        reset(test1, test2, test3);
        expect(test2.receive(eq(sw), eq(fm), isA(FloodlightContext.class)))
                .andReturn(Command.STOP);
        // test2 will not be called
        // test3 is not a listener for FlowMod
        replay(test1, test2, test3);
        controller.handleMessage(sw, fm, null);
        verify(test1);
        verify(test2);
        verify(test3);

        verify(sw);
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


    /**
     * Test injectMessage and also do some more tests for listener ordering
     * and handling of Command.STOP
     * @throws Exception
     */
    @Test
    public void testInjectMessage() throws Exception {
        FloodlightContext cntx = new FloodlightContext();
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        // Add listeners
        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").anyTimes();
        setupListenerOrdering(test1);

        IOFMessageListener test2 = createMock(IOFMessageListener.class);
        expect(test2.getName()).andReturn("test2").anyTimes();
        test2.isCallbackOrderingPostreq(OFType.PACKET_IN, "test1");
        expectLastCall().andReturn(true).atLeastOnce();
        setupListenerOrdering(test2);
        replay(test1, test2);
        controller.addOFMessageListener(OFType.PACKET_IN, test1);
        controller.addOFMessageListener(OFType.PACKET_IN, test2);
        verify(test1);
        verify(test2);

        // Test inject with null switch and no message. Should not work.
        reset(test1, test2);
        replay(test1, test2, sw);
        try {
            controller.injectOfMessage(null, pi);
            fail("InjectOfMessage should have thrown a NPE");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            controller.injectOfMessage(null, pi, cntx);
            fail("InjectOfMessage should have thrown a NPE");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            controller.injectOfMessage(sw, null);
            fail("InjectOfMessage should have thrown a NPE");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            controller.injectOfMessage(sw, null, cntx);
            fail("InjectOfMessage should have thrown a NPE");
        } catch (NullPointerException e) {
            // expected
        }
        verify(test1);
        verify(test2);
        verify(sw);

        //
        // Test inject with inActive switch. Should not work.
        reset(test1, test2, sw);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(sw.isActive()).andReturn(false).atLeastOnce();
        replay(test1, test2, sw);
        assertFalse("Inject should have failed",
                    controller.injectOfMessage(sw, pi));
        assertFalse("Inject should have failed",
                    controller.injectOfMessage(sw, pi, cntx));
        verify(test1);
        verify(test2);
        verify(sw);


        // Test inject in the "normal" case without context
        reset(test1, test2, sw);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(sw.isActive()).andReturn(true).atLeastOnce();
        expect(test2.receive(same(sw), same(pi) , isA(FloodlightContext.class)))
                .andReturn(Command.STOP);
        // test1 will not receive any message!
        replay(test1, test2, sw);
        assertTrue("Inject should have worked",
                    controller.injectOfMessage(sw, pi));
        verify(test1);
        verify(test2);
        verify(sw);

        // Test inject in the "normal" case with context
        reset(test1, test2, sw);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(sw.isActive()).andReturn(true).atLeastOnce();
        expect(test2.receive(same(sw), same(pi) , same(cntx)))
                .andReturn(Command.STOP);
        // test1 will not receive any message!
        replay(test1, test2, sw);
        assertTrue("Inject should have worked",
                    controller.injectOfMessage(sw, pi, cntx));
        verify(test1);
        verify(test2);
        verify(sw);

        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        assertArrayEquals(testPacket.serialize(), eth.serialize());
    }


    /**
     * Test handleOutgoingMessage and also test listener ordering
     * @throws Exception
     */
    @Test
    public void testHandleOutgoingMessage() throws Exception {
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.ECHO_REQUEST);
        FloodlightContext cntx = new FloodlightContext();
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        // Add listeners
        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").anyTimes();
        setupListenerOrdering(test1);

        IOFMessageListener test2 = createMock(IOFMessageListener.class);
        expect(test2.getName()).andReturn("test2").anyTimes();
        test2.isCallbackOrderingPostreq(OFType.ECHO_REQUEST, "test1");
        expectLastCall().andReturn(true).atLeastOnce();
        setupListenerOrdering(test2);

        IOFMessageListener test3 = createMock(IOFMessageListener.class);
        expect(test3.getName()).andReturn("test3").anyTimes();
        test3.isCallbackOrderingPostreq(OFType.ECHO_REQUEST, "test2");
        expectLastCall().andReturn(true).atLeastOnce();
        setupListenerOrdering(test3);

        // expected ordering is test3, test2, test1

        replay(test1, test2, test3);
        controller.addOFMessageListener(OFType.ECHO_REQUEST, test1);
        controller.addOFMessageListener(OFType.ECHO_REQUEST, test3);
        controller.addOFMessageListener(OFType.ECHO_REQUEST, test2);
        verify(test1);
        verify(test2);
        verify(test3);

        // Test inject with null switch and no message. Should not work.
        reset(test1, test2, test3);
        replay(test1, test2, test3, sw);
        try {
            controller.handleOutgoingMessage(null, pi, cntx);
            fail("handleOutgoindMessage should have thrown a NPE");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            controller.handleOutgoingMessage(sw, null, cntx);
            fail("handleOutgoingMessage should have thrown a NPE");
        } catch (NullPointerException e) {
            // expected
        }
        verify(test1);
        verify(test2);
        verify(test3);
        verify(sw);

        // Test the handleOutgoingMessage
        reset(test1, test2, test3, sw);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(test2.receive(same(sw), same(m) , same(cntx)))
                .andReturn(Command.STOP);
        expect(test3.receive(same(sw), same(m) , same(cntx)))
                .andReturn(Command.CONTINUE);
        // test1 will not receive any message!
        replay(test1, test2, test3, sw);
        controller.handleOutgoingMessage(sw, m, cntx);
        verify(test1);
        verify(test2);
        verify(test3);
        verify(sw);

        // Test the handleOutgoingMessage with null context
        reset(test1, test2, test3, sw);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(test2.receive(same(sw), same(m) , isA(FloodlightContext.class)))
                .andReturn(Command.STOP);
        expect(test3.receive(same(sw), same(m) , isA(FloodlightContext.class)))
                .andReturn(Command.CONTINUE);
        // test1 will not receive any message!
        replay(test1, test2, test3, sw);
        controller.handleOutgoingMessage(sw, m, null);
        verify(test1);
        verify(test2);
        verify(test3);
        verify(sw);

        // Test for message without listeners
        reset(test1, test2, test3, sw);
        replay(test1, test2, test3, sw);
        m = BasicFactory.getInstance().getMessage(OFType.ECHO_REPLY);
        controller.handleOutgoingMessage(sw, m, cntx);
        verify(test1);
        verify(test2);
        verify(test3);
        verify(sw);
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

    /**
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

    /**
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

    /** Test other setRole cases: re-setting role to the current role,
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
    /**
     * Test switchActivated for a new switch, i.e., a switch that was not
     * previously known to the controller cluser. We expect that all
     * flow mods are cleared and we expect a switchAdded
     */
    public void testNewSwitchActivated() throws Exception {
        // We set AlwaysClearFlowsOnSwActivate to false but we still
        // expect a clearAllFlowMods() because the AlwaysClearFlowsOnSwActivate
        // is only relevant if a switch that was previously known is activated!!
        controller.setAlwaysClearFlowsOnSwActivate(false);

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

    /**
     * Test switchActivated for a new switch while in slave: a no-op
     */
    @Test
    public void testNewSwitchActivatedWhileSlave() throws Exception {
        doSetUp(Role.SLAVE);
        IOFSwitch sw = createMock(IOFSwitch.class);

        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);

        replay(sw, listener); // nothing recorded
        controller.switchActivated(sw);
        verify(sw);
        verify(listener);
    }


    /**
     * Create and activate a switch, either completely new or reconnected
     * The mocked switch instance will be returned. It wil be reset.
     */
    private IOFSwitch doActivateSwitchInt(long dpid,
                                          OFDescriptionStatistics desc,
                                          OFFeaturesReply featuresReply,
                                          boolean clearFlows)
                                          throws Exception {
        controller.setAlwaysClearFlowsOnSwActivate(true);

        IOFSwitch sw = createMock(IOFSwitch.class);
        if (featuresReply == null) {
            featuresReply = createOFFeaturesReply();
            featuresReply.setDatapathId(dpid);
        }
        if (desc == null) {
            desc = createOFDescriptionStatistics();
        }
        setupSwitchForAddSwitch(sw, dpid, desc, featuresReply);
        if (clearFlows) {
            sw.clearAllFlowMods();
            expectLastCall().once();
        }

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

    /**
     * Create and activate a new switch with the given dpid, features reply
     * and description. If description and/or features reply are null we'll
     * allocate the default one
     * The mocked switch instance will be returned. It wil be reset.
     */
    private IOFSwitch doActivateNewSwitch(long dpid,
                                          OFDescriptionStatistics desc,
                                          OFFeaturesReply featuresReply)
                                          throws Exception {
        return doActivateSwitchInt(dpid, desc, featuresReply, true);
    }

    /**
     * Create and activate a switch that's just been disconnected.
     * The mocked switch instance will be returned. It wil be reset.
     */
    private IOFSwitch doActivateOldSwitch(long dpid,
                                          OFDescriptionStatistics desc,
                                          OFFeaturesReply featuresReply)
                                          throws Exception {
        return doActivateSwitchInt(dpid, desc, featuresReply, false);
    }


    /**
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

    /**
     * Remove a switch from the sync store and
     * notify the store listener.
     */
    public void doRemoveSwitchFromStore(long dpid) throws Exception {
        storeClient.delete(dpid);

        Iterator<Long> keysToNotify = Collections.singletonList(dpid).iterator();
        controller.getStoreListener().keysModified(keysToNotify,
                                                   UpdateType.REMOTE);
    }


    /** (remotely) add switch to store and then remove while master. no-op */
    @Test
    public void testAddSwitchToStoreMaster() throws Exception {
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);
        replay(listener);

        //--------------
        // add switch
        doAddSwitchToStore(1L, null, null);
        controller.processUpdateQueueForTesting();
        IOFSwitch sw = controller.getSwitch(1L);
        assertNull("There shouldn't be a switch", sw);
        verify(listener);

        //--------------
        // add a real switch
        controller.setAlwaysClearFlowsOnSwActivate(true);
        sw = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(sw, 1L, null, null);
        sw.clearAllFlowMods();
        expectLastCall().once();
        reset(listener);
        listener.switchAdded(1L);
        expectLastCall().once();
        listener.switchActivated(1L);
        expectLastCall().once();
        replay(listener);
        replay(sw);
        controller.switchActivated(sw);
        verify(sw);
        assertEquals(sw, controller.getSwitch(1L));
        controller.processUpdateQueueForTesting();
        verify(listener);

        //-----------
        // remove switch from store.
        reset(listener);
        replay(listener);
        doRemoveSwitchFromStore(1L);
        controller.processUpdateQueueForTesting();
        verify(listener);
        assertEquals(sw, controller.getSwitch(1L));
    }


    /**
     * add switch to store then remove it again while slave.
     * should get notification and switch should be added and then removed
     */
    @Test
    public void testAddSwitchRemoveSwitchStoreSlave() throws Exception {
        doSetUp(Role.SLAVE);

        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);

        //------
        // Add switch
        listener.switchAdded(1L);
        expectLastCall().once();
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

        //------
        // remove switch
        reset(listener);
        listener.switchRemoved(1L);
        replay(listener);
        doRemoveSwitchFromStore(1L);
        controller.processUpdateQueueForTesting();
        verify(listener);
        assertNull("Switch should not exist anymore", controller.getSwitch(1L));
    }

    /** Add switch to store with inconsistent DPID
     * @throws Exception
     */
    @Test
    public void testInconsistentStoreDpid() throws Exception {
        doSetUp(Role.SLAVE);

        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        controller.addOFSwitchListener(listener);
        replay(listener);


        OFFeaturesReply featuresReply = createOFFeaturesReply();
        featuresReply.setDatapathId(42L);
        OFDescriptionStatistics desc = createOFDescriptionStatistics();
        SwitchSyncRepresentation ssr =
                new SwitchSyncRepresentation(featuresReply, desc);
        storeClient.put(1L, ssr);

        Iterator<Long> keysToNotify = Collections.singletonList(1L).iterator();
        controller.getStoreListener().keysModified(keysToNotify,
                                                   UpdateType.REMOTE);
        controller.processUpdateQueueForTesting();
        verify(listener);

        assertNull("Switch should not have been added",
                   controller.getSwitch(1L));
        assertNull("Switch should not have been added",
                   controller.getSwitch(42L));
    }


    /**
     * This test goes through the SLAVE->MASTER program flow. We'll start as
     * SLAVE. Add switches to the store while slave, update these switches
     * then transition to master, make most (but not all switches) "connect"
     * We also check correct behavior of getAllSwitchDpids() and
     * getAllSwitchMap()
     *
     * We also change ports to verify that we receive port changed notifications
     * if ports are changes in the sync store or when we transition from
     * inactive to active
     */
    @Test
    public void testSwitchAddWithRoleChangeSomeReconnect() throws Exception {
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

        // Add readyForReconcile listener
        IReadyForReconcileListener readyForReconcileListener =
                createMock(IReadyForReconcileListener.class);
        controller.addReadyForReconcileListener(readyForReconcileListener);

        //---------------------------------------
        // Initialization
        //---------------------------------------

        // Switch 1
        // no actual IOFSwitch here because we simply add features reply
        // and desc stats to store
        OFFeaturesReply fr1a = createOFFeaturesReply();
        fr1a.setDatapathId(1L);
        OFPhysicalPort p = createOFPhysicalPort("P1", 1);
        ImmutablePort sw1p1 = ImmutablePort.fromOFPhysicalPort(p);
        List<OFPhysicalPort> ports1a = Collections.singletonList(p);
        fr1a.setPorts(ports1a);
        List<ImmutablePort> ports1aImmutable =
                ImmutablePort.immutablePortListOf(ports1a);
        // an alternative featuers reply
        OFFeaturesReply fr1b = createOFFeaturesReply();
        fr1b.setDatapathId(1L);
        p = new OFPhysicalPort();
        p = createOFPhysicalPort("P1", 1); // same port as above
        List<OFPhysicalPort> ports1b = new ArrayList<OFPhysicalPort>();
        ports1b.add(p);
        p = createOFPhysicalPort("P2", 42000);
        ImmutablePort sw1p2 = ImmutablePort.fromOFPhysicalPort(p);
        ports1b.add(p);
        fr1b.setPorts(ports1b);
        List<ImmutablePort> ports1bImmutable =
                ImmutablePort.immutablePortListOf(ports1b);

        // Switch 2
        // no actual IOFSwitch here because we simply add features reply
        // and desc stats to store
        OFFeaturesReply fr2a = createOFFeaturesReply();
        fr2a.setDatapathId(2L);
        ImmutablePort sw2p1 = sw1p1;
        List<OFPhysicalPort> ports2a = new ArrayList<OFPhysicalPort>(ports1a);
        fr2a.setPorts(ports2a);
        List<ImmutablePort> ports2aImmutable =
                ImmutablePort.immutablePortListOf(ports2a);
        // an alternative features reply
        OFFeaturesReply fr2b = createOFFeaturesReply();
        fr2b.setDatapathId(2L);
        p = new OFPhysicalPort();
        p = createOFPhysicalPort("P1", 2); // port number changed
        ImmutablePort sw2p1Changed = ImmutablePort.fromOFPhysicalPort(p);
        List<OFPhysicalPort> ports2b = Collections.singletonList(p);
        fr2b.setPorts(ports2b);

        // Switches 3 and 4 are create with default features reply and desc
        // so nothing to do here

        //---------------------------------------
        // Adding switches to store
        //---------------------------------------

        replay(haListener); // nothing should happen to haListener
        replay(readyForReconcileListener); // nothing should happen to
                                           // readyForReconcileListener

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
        assertEquals(new HashSet<ImmutablePort>(ports1aImmutable),
                     new HashSet<ImmutablePort>(sw.getPorts()));

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
        assertEquals(new HashSet<ImmutablePort>(ports2aImmutable),
                     new HashSet<ImmutablePort>(sw.getPorts()));

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

        // add switch 4 to store
        reset(switchListener);
        switchListener.switchAdded(4L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(4L, null, null);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        sw = controller.getSwitch(4L);
        assertNotNull("Switch should be present", sw);
        assertEquals(4L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        // update switch 1 with fr1b
        reset(switchListener);
        switchListener.switchPortChanged(1L, sw1p2, PortChangeType.ADD);
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
        assertEquals(new HashSet<ImmutablePort>(ports1bImmutable),
                     new HashSet<ImmutablePort>(sw.getPorts()));

        // Check getAllSwitchDpids() and getAllSwitchMap()
        Set<Long> expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        expectedDpids.add(3L);
        expectedDpids.add(4L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        Map<Long, IOFSwitch> expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, controller.getSwitch(1L));
        expectedSwitchMap.put(2L, controller.getSwitch(2L));
        expectedSwitchMap.put(3L, controller.getSwitch(3L));
        expectedSwitchMap.put(4L, controller.getSwitch(4L));
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
        // also set alwaysClearFlorModOnSwAcitvate to true;
        controller.setAlwaysClearFlowsOnSwActivate(true);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(sw2, 2L, null, fr2b);
        sw2.clearAllFlowMods();
        expectLastCall().once();
        reset(switchListener);
        switchListener.switchActivated(2L);
        expectLastCall().once();
        switchListener.switchPortChanged(2L, sw2p1, PortChangeType.DELETE);
        switchListener.switchPortChanged(2L, sw2p1Changed, PortChangeType.ADD);
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

        // Do not activate switch 4, but it should still be present
        sw = controller.getSwitch(4L);
        IOFSwitch sw4 = sw;
        assertNotNull("Switch should be present", sw);
        assertEquals(4L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        // Check getAllSwitchDpids() and getAllSwitchMap()
        expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        expectedDpids.add(3L);
        expectedDpids.add(4L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, sw1);
        expectedSwitchMap.put(2L, sw2);
        expectedSwitchMap.put(3L, sw3);
        expectedSwitchMap.put(4L, sw4);
        assertEquals(expectedSwitchMap, controller.getAllSwitchMap());

        // silently remove switch 4 from the store and notify the
        // store listener. Since the controller is MASTER it will ignore
        // this notification.
        reset(switchListener);
        replay(switchListener);
        doRemoveSwitchFromStore(4L);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        // Switch should still be queryable
        sw = controller.getSwitch(4L);
        assertNotNull("Switch should be present", sw);
        assertEquals(4L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        //--------------------------------
        // Wait for consolidateStore
        //--------------------------------
        verify(readyForReconcileListener);
        reset(readyForReconcileListener);
        readyForReconcileListener.readyForReconcile();
        replay(readyForReconcileListener);
        reset(switchListener);
        switchListener.switchRemoved(3L);
        switchListener.switchRemoved(4L);
        replay(switchListener);
        Thread.sleep(2*consolidateStoreDelayMs);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        verify(readyForReconcileListener);
        reset(readyForReconcileListener);
        replay(readyForReconcileListener);

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
        verify(readyForReconcileListener);
    }

    /**
     * This test goes through the SLAVE->MASTER program flow. We'll start as
     * SLAVE. Add switches to the store while slave, update these switches
     * then transition to master, make all "connect"
     *
     * Supplements testSwitchAddWithRoleChangeSomeReconnect() and thus does
     * less extensive testing. We are really only interested in verifying
     * that we get the readyForReconciliation event before
     * consolidateStore runs.
     */
    @Test
    public void testSwitchAddWithRoleChangeAllReconnect() throws Exception {
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

        // Add readyForReconcile listener
        IReadyForReconcileListener readyForReconcileListener =
                createMock(IReadyForReconcileListener.class);
        controller.addReadyForReconcileListener(readyForReconcileListener);

        //---------------------------------------
        // Adding switches to store
        //---------------------------------------

        replay(haListener); // nothing should happen to haListener
        replay(readyForReconcileListener); // nothing should happen to
                                           // readyForReconcileListener

        // add switch 1 to store
        reset(switchListener);
        switchListener.switchAdded(1L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(1L, null, null);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        IOFSwitch sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertEquals(1L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        // add switch 2 to store
        reset(switchListener);
        switchListener.switchAdded(2L);
        expectLastCall().once();
        replay(switchListener);
        doAddSwitchToStore(2L, null, null);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        reset(switchListener);

        sw = controller.getSwitch(2L);
        assertNotNull("Switch should be present", sw);
        assertEquals(2L, sw.getId());
        assertFalse("Switch should be inactive", sw.isActive());

        // Check getAllSwitchDpids() and getAllSwitchMap()
        Set<Long> expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        Map<Long, IOFSwitch> expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, controller.getSwitch(1L));
        expectedSwitchMap.put(2L, controller.getSwitch(2L));
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
        setupSwitchForAddSwitch(sw1, 1L, null, null);
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

        // Activate switch 2
        // Since this is the last inactive switch to activate we should
        // get the readyForReconcile notifiction
        verify(readyForReconcileListener);
        reset(readyForReconcileListener);
        readyForReconcileListener.readyForReconcile();
        replay(readyForReconcileListener);
        controller.setAlwaysClearFlowsOnSwActivate(true);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        setupSwitchForAddSwitch(sw2, 2L, null, null);
        sw2.clearAllFlowMods();
        expectLastCall().once();
        reset(switchListener);
        switchListener.switchActivated(2L);
        expectLastCall().once();
        replay(sw2);
        replay(switchListener);
        controller.switchActivated(sw2);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        verify(sw2);
        verify(readyForReconcileListener);


        sw = controller.getSwitch(2L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw2, sw); // the mock switch should be returned


        // Check getAllSwitchDpids() and getAllSwitchMap()
        expectedDpids = new HashSet<Long>();
        expectedDpids.add(1L);
        expectedDpids.add(2L);
        assertEquals(expectedDpids, controller.getAllSwitchDpids());
        expectedSwitchMap = new HashMap<Long, IOFSwitch>();
        expectedSwitchMap.put(1L, sw1);
        expectedSwitchMap.put(2L, sw2);
        assertEquals(expectedSwitchMap, controller.getAllSwitchMap());

        //--------------------------------
        // Wait for consolidateStore: a no-op
        //--------------------------------
        reset(switchListener);
        replay(switchListener);
        reset(readyForReconcileListener);
        replay(readyForReconcileListener);
        Thread.sleep(2*consolidateStoreDelayMs);
        controller.processUpdateQueueForTesting();
        verify(switchListener);
        verify(readyForReconcileListener);

        // Verify the expected switches are all there. no more no less
        sw = controller.getSwitch(1L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw1, sw);   // the mock switch should be returned

        sw = controller.getSwitch(2L);
        assertNotNull("Switch should be present", sw);
        assertSame(sw2, sw); // the mock switch should be returned

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

    /**
     * Disconnect a switch. normal program flow
     */
    @Test
    private void doTestSwitchConnectReconnect(boolean reconnect)
            throws Exception {
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
        if (reconnect) {
            controller.removeOFSwitchListener(listener);
            sw = doActivateOldSwitch(1L, null, null);
        }
    }

    @Test
    public void testSwitchDisconnected() throws Exception {
        doTestSwitchConnectReconnect(false);
    }

    /**
     * Disconnect a switch and reconnect, verify no clearAllFlowmods()
     */
    @Test
    public void testSwitchReconnect() throws Exception {
        doTestSwitchConnectReconnect(true);
    }

    /**
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

    /**
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



    /**
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
       // we don't care for updates. drain queue.
       controller.processUpdateQueueForTesting();
   }



   /**
    * Test that notifyPortChanged() results in an IOFSwitchListener
    * update and that its arguments are passed through to
    * the listener call
    */
   @Test
   public void testNotifySwitchPoArtChanged() throws Exception {
       long dpid = 42L;

       OFFeaturesReply fr1 = createOFFeaturesReply();
       fr1.setDatapathId(dpid);
       OFPhysicalPort p1 = createOFPhysicalPort("Port1", 1);
       fr1.setPorts(Collections.singletonList(p1));

       OFFeaturesReply fr2 = createOFFeaturesReply();
       fr1.setDatapathId(dpid);
       OFPhysicalPort p2 = createOFPhysicalPort("Port1", 1);
       p2.setAdvertisedFeatures(0x2); // just some bogus values
       fr2.setPorts(Collections.singletonList(p2));

       OFDescriptionStatistics desc = createOFDescriptionStatistics();

       // activate switch
       IOFSwitch sw = doActivateNewSwitch(dpid, desc, fr1);

       // check the store
       SwitchSyncRepresentation ssr = storeClient.getValue(dpid);
       assertNotNull(ssr);
       assertEquals(dpid, ssr.getDpid());
       assertEquals(1, ssr.getPorts().size());
       assertEquals(p1, ssr.getPorts().get(0).toOFPhysicalPort());

       IOFSwitchListener listener = createMock(IOFSwitchListener.class);
       controller.addOFSwitchListener(listener);
       // setup switch with the new, second features reply (and thus ports)
       setupSwitchForAddSwitch(sw, dpid, desc, fr2);
       listener.switchPortChanged(dpid, ImmutablePort.fromOFPhysicalPort(p2),
                                  PortChangeType.OTHER_UPDATE);
       expectLastCall().once();
       replay(listener);
       replay(sw);
       controller.notifyPortChanged(sw, ImmutablePort.fromOFPhysicalPort(p2),
                                    PortChangeType.OTHER_UPDATE);
       controller.processUpdateQueueForTesting();
       verify(listener);
       verify(sw);

       // check the store
       ssr = storeClient.getValue(dpid);
       assertNotNull(ssr);
       assertEquals(dpid, ssr.getDpid());
       assertEquals(1, ssr.getPorts().size());
       assertEquals(p2, ssr.getPorts().get(0).toOFPhysicalPort());
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
        // we don't care for updates. drain update queue
        controller.processUpdateQueueForTesting();
    }


    /**
     * Test the driver registry: test the bind order
     */
    @Test
    public void testSwitchDriverRegistryBindOrder() {
        IOFSwitchDriver driver1 = createMock(IOFSwitchDriver.class);
        IOFSwitchDriver driver2 = createMock(IOFSwitchDriver.class);
        IOFSwitchDriver driver3 = createMock(IOFSwitchDriver.class);
        IOFSwitch returnedSwitch = null;
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        controller.addOFSwitchDriver("", driver3);
        controller.addOFSwitchDriver("test switch", driver1);
        controller.addOFSwitchDriver("test", driver2);

        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);

        OFDescriptionStatistics desc = createOFDescriptionStatistics();
        desc.setManufacturerDescription("test switch");
        desc.setHardwareDescription("version 0.9");
        reset(driver1);
        reset(driver2);
        reset(driver3);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(desc);
        expectLastCall().once();
        expect(driver1.getOFSwitchImpl(desc)).andReturn(mockSwitch).once();
        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);
        returnedSwitch = controller.getOFSwitchInstance(desc);
        assertSame(mockSwitch, returnedSwitch);
        verify(driver1);
        verify(driver2);
        verify(driver3);
        verify(mockSwitch);

        desc = createOFDescriptionStatistics();
        desc.setManufacturerDescription("testFooBar");
        desc.setHardwareDescription("version 0.9");
        reset(driver1);
        reset(driver2);
        reset(driver3);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(desc);
        expectLastCall().once();
        expect(driver2.getOFSwitchImpl(desc)).andReturn(mockSwitch).once();
        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);
        returnedSwitch = controller.getOFSwitchInstance(desc);
        assertSame(mockSwitch, returnedSwitch);
        verify(driver1);
        verify(driver2);
        verify(driver3);
        verify(mockSwitch);

        desc = createOFDescriptionStatistics();
        desc.setManufacturerDescription("FooBar");
        desc.setHardwareDescription("version 0.9");
        reset(driver1);
        reset(driver2);
        reset(driver3);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(desc);
        expectLastCall().once();
        expect(driver3.getOFSwitchImpl(desc)).andReturn(mockSwitch).once();
        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);
        returnedSwitch = controller.getOFSwitchInstance(desc);
        assertSame(mockSwitch, returnedSwitch);
        verify(driver1);
        verify(driver2);
        verify(driver3);
        verify(mockSwitch);
    }

    /**
     * Test SwitchDriverRegistry
     * Test fallback to default if no switch driver is registered for a
     * particular prefix
     */
    @Test
    public void testSwitchDriverRegistryNoDriver() {
        IOFSwitchDriver driver = createMock(IOFSwitchDriver.class);
        IOFSwitch returnedSwitch = null;
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        controller.addOFSwitchDriver("test switch", driver);

        replay(driver);
        replay(mockSwitch);

        OFDescriptionStatistics desc = createOFDescriptionStatistics();
        desc.setManufacturerDescription("test switch");
        desc.setHardwareDescription("version 0.9");
        reset(driver);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(desc);
        expectLastCall().once();
        expect(driver.getOFSwitchImpl(desc)).andReturn(mockSwitch).once();
        replay(driver);
        replay(mockSwitch);
        returnedSwitch = controller.getOFSwitchInstance(desc);
        assertSame(mockSwitch, returnedSwitch);
        verify(driver);
        verify(mockSwitch);


        desc = createOFDescriptionStatistics();
        desc.setManufacturerDescription("Foo Bar test switch");
        desc.setHardwareDescription("version 0.9");
        reset(driver);
        reset(mockSwitch);
        replay(driver);
        replay(mockSwitch);
        returnedSwitch = controller.getOFSwitchInstance(desc);
        assertNotNull(returnedSwitch);
        assertTrue("Returned switch should be OFSwitchImpl",
                   returnedSwitch instanceof OFSwitchImpl);
        assertEquals(desc, returnedSwitch.getDescriptionStatistics());
        verify(driver);
        verify(mockSwitch);
    }

    /**
     *
     */
    @Test
    public void testDriverRegistryExceptions() {
        IOFSwitchDriver driver = createMock(IOFSwitchDriver.class);
        IOFSwitchDriver driver2 = createMock(IOFSwitchDriver.class);
        replay(driver, driver2); // no calls expected on driver

        //---------------
        // Test exception handling when registering driver
        try {
            controller.addOFSwitchDriver("foobar", null);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }

        try {
            controller.addOFSwitchDriver(null, driver);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }

        // test that we can register each prefix only once!
        controller.addOFSwitchDriver("foobar",  driver);
        try {
            controller.addOFSwitchDriver("foobar",  driver);
            fail("Expected IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            //expected
        }

        try {
            controller.addOFSwitchDriver("foobar",  driver2);
            fail("Expected IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            //expected
        }

        OFDescriptionStatistics desc = createOFDescriptionStatistics();

        desc.setDatapathDescription(null);
        try {
            controller.getOFSwitchInstance(desc);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        desc.setHardwareDescription(null);
        try {
            controller.getOFSwitchInstance(desc);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        desc.setManufacturerDescription(null);
        try {
            controller.getOFSwitchInstance(desc);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        desc.setSerialNumber(null);
        try {
            controller.getOFSwitchInstance(desc);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        desc.setSoftwareDescription(null);
        try {
            controller.getOFSwitchInstance(desc);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        verify(driver, driver2);
    }

}
