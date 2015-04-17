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

package net.floodlightcontroller.forwarding;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.MockDebugEventService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.forwarding.Forwarding;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

public class ForwardingTest extends FloodlightTestCase {
    protected FloodlightContext cntx;
    protected MockDeviceManager deviceManager;
    protected IRoutingService routingEngine;
    protected Forwarding forwarding;
    protected ITopologyService topology;
    protected MockThreadPoolService threadPool;
    protected IOFSwitch sw1, sw2;
    protected OFFeaturesReply swFeatures;
    protected IDevice srcDevice, dstDevice1, dstDevice2;
    protected OFPacketIn packetIn;
    protected OFPacketOut packetOut;
    protected OFPacketOut packetOutFlooded;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    protected int expected_wildcards;
    protected Date currentDate;
    private MockSyncService mockSyncService;
    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
    

    @Override
    public void setUp() throws Exception {
        super.setUp();

        cntx = new FloodlightContext();

        // Module loader setup
        /*
        Collection<Class<? extends IFloodlightModule>> mods = new ArrayList<Class<? extends IFloodlightModule>>();
        Collection<IFloodlightService> mockedServices = new ArrayList<IFloodlightService>();
        mods.add(Forwarding.class);
        routingEngine = createMock(IRoutingService.class);
        topology = createMock(ITopologyService.class);
        mockedServices.add(routingEngine);
        mockedServices.add(topology);
        FloodlightTestModuleLoader fml = new FloodlightTestModuleLoader();
        fml.setupModules(mods, mockedServices);
        mockFloodlightProvider =
        		(MockFloodlightProvider) fml.getModuleByName(MockFloodlightProvider.class);
        deviceManager =
        		(MockDeviceManager) fml.getModuleByName(MockDeviceManager.class);
        threadPool =
        		(MockThreadPoolService) fml.getModuleByName(MockThreadPoolService.class);
        forwarding =
        		(Forwarding) fml.getModuleByName(Forwarding.class);
        */
        mockFloodlightProvider = getMockFloodlightProvider();
        forwarding = new Forwarding();
        threadPool = new MockThreadPoolService();
        deviceManager = new MockDeviceManager();
        routingEngine = createMock(IRoutingService.class);
        topology = createMock(ITopologyService.class);
        mockSyncService = new MockSyncService();
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();


        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class,
                       mockFloodlightProvider);
        fmc.addService(IThreadPoolService.class, threadPool);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IRoutingService.class, routingEngine);
        fmc.addService(IDeviceService.class, deviceManager);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(ISyncService.class, mockSyncService);
        fmc.addService(IDebugCounterService.class, new MockDebugCounterService());
        fmc.addService(IDebugEventService.class, new MockDebugEventService());
        fmc.addService(IOFSwitchService.class, getMockSwitchService());

        topology.addListener(anyObject(ITopologyListener.class));
        expectLastCall().anyTimes();
        expect(topology.isIncomingBroadcastAllowed(anyObject(DatapathId.class), anyObject(OFPort.class))).andReturn(true).anyTimes();
        replay(topology);

        threadPool.init(fmc);
        mockSyncService.init(fmc);
        forwarding.init(fmc);
        deviceManager.init(fmc);
        entityClassifier.init(fmc);
        threadPool.startUp(fmc);
        mockSyncService.startUp(fmc);
        deviceManager.startUp(fmc);
        forwarding.startUp(fmc);
        entityClassifier.startUp(fmc);
        verify(topology);

        swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
        // Mock switches
        sw1 = EasyMock.createMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(DatapathId.of(1L)).anyTimes();
        expect(sw1.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw1.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

        sw2 = EasyMock.createMock(IOFSwitch.class);
        expect(sw2.getId()).andReturn(DatapathId.of(2L)).anyTimes();
        expect(sw2.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw2.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();

        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();

        // Load the switch map
        Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
        switches.put(DatapathId.of(1L), sw1);
        switches.put(DatapathId.of(2L), sw2);
        getMockSwitchService().setSwitches(switches);

        // Build test packet
        testPacket = new Ethernet()
            .setDestinationMACAddress("00:11:22:33:44:55")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setEtherType(EthType.IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));



        currentDate = new Date();

        // Mock Packet-in
        testPacketSerialized = testPacket.serialize();
        packetIn = factory.buildPacketIn()
        		.setMatch(factory.buildMatch()
        				.setExact(MatchField.IN_PORT, OFPort.of(1))
        				.setExact(MatchField.ETH_SRC, MacAddress.of("00:44:33:22:11:00"))
        				.setExact(MatchField.ETH_DST, MacAddress.of("00:11:22:33:44:55"))
        				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        				.setExact(MatchField.IPV4_SRC, IPv4Address.of("192.168.1.1"))
        				.setExact(MatchField.IPV4_DST, IPv4Address.of("192.168.1.2"))
        				.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
        				.setExact(MatchField.UDP_SRC, TransportPort.of(5000))
        				.setExact(MatchField.UDP_DST, TransportPort.of(5001))
        				.build())
        		.setBufferId(OFBufferId.NO_BUFFER)
        		.setData(testPacketSerialized)
        		.setReason(OFPacketInReason.NO_MATCH)
                .build();

        // Mock Packet-out
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(factory.actions().output(OFPort.of(3), Integer.MAX_VALUE));
        packetOut = factory.buildPacketOut()
        		.setBufferId(this.packetIn.getBufferId())
        		.setActions(poactions)
        		.setInPort(OFPort.of(1))
        		.setData(testPacketSerialized)
        		.setXid(15)
        		.build();

        // Mock Packet-out with OFPP_FLOOD action
        poactions = new ArrayList<OFAction>();
        poactions.add(factory.actions().output(OFPort.FLOOD, Integer.MAX_VALUE));
        packetOutFlooded = factory.buildPacketOut()
        		.setBufferId(this.packetIn.getBufferId())
        		.setInPort(packetIn.getMatch().get(MatchField.IN_PORT))
        		.setXid(17)
        		.setActions(poactions)
        		.setData(testPacketSerialized)
        		.build();
            
        IFloodlightProviderService.bcStore.
            put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                (Ethernet)testPacket);
    }

    enum DestDeviceToLearn { NONE, DEVICE1 ,DEVICE2 };
    public void learnDevices(DestDeviceToLearn destDeviceToLearn) {
        // Build src and dest devices
        MacAddress dataLayerSource = ((Ethernet)testPacket).getSourceMACAddress();
        MacAddress dataLayerDest =
                ((Ethernet)testPacket).getDestinationMACAddress();
        IPv4Address networkSource =
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getSourceAddress();
        IPv4Address networkDest =
                ((IPv4)((Ethernet)testPacket).getPayload()).
                    getDestinationAddress();

        reset(topology);
        expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(1)))
                                              .andReturn(true)
                                              .anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(2L), OFPort.of(3)))
                                              .andReturn(true)
                                              .anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L), OFPort.of(3)))
                                              .andReturn(true)
                                              .anyTimes();
        replay(topology);

        srcDevice =
                deviceManager.learnEntity(dataLayerSource.getLong(),
                                          null, networkSource.getInt(),
                                          1L, 1);
        IDeviceService.fcStore. put(cntx,
                                    IDeviceService.CONTEXT_SRC_DEVICE,
                                    srcDevice);
        if (destDeviceToLearn == DestDeviceToLearn.DEVICE1) {
            dstDevice1 =
                    deviceManager.learnEntity(dataLayerDest.getLong(),
                                              null, networkDest.getInt(),
                                              2L, 3);
            IDeviceService.fcStore.put(cntx,
                                       IDeviceService.CONTEXT_DST_DEVICE,
                                       dstDevice1);
        }
        if (destDeviceToLearn == DestDeviceToLearn.DEVICE2) {
            dstDevice2 =
                    deviceManager.learnEntity(dataLayerDest.getLong(),
                                              null, networkDest.getInt(),
                                              1L, 3);
            IDeviceService.fcStore.put(cntx,
                                       IDeviceService.CONTEXT_DST_DEVICE,
                                       dstDevice2);
        }
        verify(topology);
    }

    @Test
    public void testForwardMultiSwitchPath() throws Exception {
        learnDevices(DestDeviceToLearn.DEVICE1);

        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<OFMessage> wc2 = new Capture<OFMessage>(CaptureType.ALL);

        Route route = new Route(DatapathId.of(1L), DatapathId.of(2L));
        List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
        nptList.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        nptList.add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
        nptList.add(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
        nptList.add(new NodePortTuple(DatapathId.of(2L), OFPort.of(3)));
        route.setPath(nptList);
        expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(1), DatapathId.of(2L), OFPort.of(3), U64.ZERO)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        Match match = packetIn.getMatch();
        OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = factory.buildFlowAdd()
        		.setIdleTimeout((short)5)
        		.setMatch(match)
        		.setActions(actions)
        		.setOutPort(action.getPort())
        		.setBufferId(OFBufferId.NO_BUFFER)
        		.setCookie(U64.of(2L << 52))
        		.setPriority(1)
        		.build();
        OFFlowMod fm2 = fm1.createBuilder().build();

        sw1.write(capture(wc1));
        expectLastCall().anyTimes();
        sw2.write(capture(wc2));
        expectLastCall().anyTimes();

        reset(topology);
        expect(topology.getL2DomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
        expect(topology.getL2DomainId(DatapathId.of(2L))).andReturn(DatapathId.of(1L)).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(2L),  OFPort.of(3))).andReturn(true).anyTimes();
        expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);

        assertTrue(wc1.hasCaptured());  // wc1 should get packetout + flowmod.
        assertTrue(wc2.hasCaptured());  // wc2 should be a flowmod.

        List<OFMessage> msglist = wc1.getValues();

        for (OFMessage m: msglist) {
            if (m instanceof OFFlowMod)
                assertTrue(OFMessageUtils.equalsIgnoreXid(fm1, m));
            else if (m instanceof OFPacketOut) {
            	assertTrue(OFMessageUtils.equalsIgnoreXid(packetOut, m));
            }
        }

        OFMessage m = wc2.getValue();
        assert (m instanceof OFFlowMod);
        assertTrue(OFMessageUtils.equalsIgnoreXid(m, fm2));
    }

    @Test
    public void testForwardSingleSwitchPath() throws Exception {
        learnDevices(DestDeviceToLearn.DEVICE2);

        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<OFMessage> wc2 = new Capture<OFMessage>(CaptureType.ALL);
        
        Route route = new  Route(DatapathId.of(1L), DatapathId.of(1L));
        route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
        expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3), U64.ZERO)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        Match match = packetIn.getMatch();
        OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = factory.buildFlowAdd()
        	.setIdleTimeout((short)5)
            .setMatch(match)
            .setActions(actions)
            .setOutPort(OFPort.of(3))
            .setBufferId(OFBufferId.NO_BUFFER)
            .setCookie(U64.of(2L<< 52))
            .setPriority(1)
            .build();
                
        // Record expected packet-outs/flow-mods
        sw1.write(capture(wc1));
        expectLastCall().once();
        sw1.write(capture(wc2));
        expectLastCall().once();
        
        reset(topology);
        expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(3))).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);
        
        assertTrue(wc1.hasCaptured());
        assertTrue(wc2.hasCaptured());
        
        assertTrue(OFMessageUtils.equalsIgnoreXid(wc1.getValue(), fm1));
        assertTrue(OFMessageUtils.equalsIgnoreXid(wc2.getValue(), packetOut));
    }

    /*TODO OFMessageDamper broken due to XID variability in OFMessages... need to fix @Test */
    public void testFlowModDampening() throws Exception {
        learnDevices(DestDeviceToLearn.DEVICE2);

        reset(topology);
        expect(topology.isAttachmentPointPort(DatapathId.of(anyLong()), OFPort.of(anyShort())))
        .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
        replay(topology);


        Route route = new  Route(DatapathId.of(1L), DatapathId.of(1L));
        route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
        route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
        expect(routingEngine.getRoute(DatapathId.of(1L), OFPort.of(1), DatapathId.of(1L), OFPort.of(3), U64.ZERO)).andReturn(route).atLeastOnce();

        // Expected Flow-mods
        Match match = packetIn.getMatch();
        OFActionOutput action = factory.actions().output(OFPort.of(3), Integer.MAX_VALUE);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        OFFlowMod fm1 = factory.buildFlowAdd()
        		.setIdleTimeout((short)5)
        		.setMatch(match)
        		.setActions(actions)
        		.setOutPort(OFPort.of(3))
        		.setBufferId(OFBufferId.NO_BUFFER)
        		.setCookie(U64.of(2L << 52))
        		.setXid(anyLong())
        		.build();
        
        // Record expected packet-outs/flow-mods
        // We will inject the packet_in 3 times and expect 1 flow mod and
        // 3 packet outs due to flow mod dampening
        sw1.write(fm1);
        expectLastCall().times(1);
        // Update new expected XID
        sw1.write(packetOut.createBuilder().setXid(anyLong()).build());
        expectLastCall().times(3);

        reset(topology);
        expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
        expect(topology.getL2DomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(3))).andReturn(true).anyTimes();

        // Reset mocks, trigger the packet in, and validate results
        replay(sw1, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        forwarding.receive(sw1, this.packetIn, cntx);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, routingEngine);
    }

    @Test
    public void testForwardNoPath() throws Exception {
        learnDevices(DestDeviceToLearn.NONE);

        // Set no destination attachment point or route
        // expect no Flow-mod but expect the packet to be flooded
        
        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);

        // Reset mocks, trigger the packet in, and validate results
        reset(topology);
        expect(topology.isIncomingBroadcastAllowed(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
        expect(topology.isAttachmentPointPort(DatapathId.of(anyLong()),
                                              OFPort.of(anyShort())))
                                              .andReturn(true)
                                              .anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD))
                .andReturn(true).anyTimes();
        // Reset XID to expected (dependent on prior unit tests)
        sw1.write(capture(wc1));
        expectLastCall().once();
        replay(sw1, sw2, routingEngine, topology);
        forwarding.receive(sw1, this.packetIn, cntx);
        verify(sw1, sw2, routingEngine);
        
        assertTrue(wc1.hasCaptured());
        assertTrue(OFMessageUtils.equalsIgnoreXid(wc1.getValue(), packetOutFlooded));
    }

}
