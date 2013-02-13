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

package net.floodlightcontroller.devicemanager.internal;


import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyShort;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.or;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.SwitchPort.ErrorStatus;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl.ClassState;
import net.floodlightcontroller.devicemanager.test.MockEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockEntityClassifierMac;
import net.floodlightcontroller.devicemanager.test.MockFlexEntityClassifier;
import net.floodlightcontroller.flowcache.FlowReconcileManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceManagerImplTest extends FloodlightTestCase {

    protected static Logger logger =
            LoggerFactory.getLogger(DeviceManagerImplTest.class);

    protected OFPacketIn packetIn_1, packetIn_2, packetIn_3;
    protected IPacket testARPReplyPacket_1, testARPReplyPacket_2,
    testARPReplyPacket_3;
    protected IPacket testARPReqPacket_1, testARPReqPacket_2;
    protected byte[] testARPReplyPacket_1_Srld, testARPReplyPacket_2_Srld;
    private byte[] testARPReplyPacket_3_Serialized;
    DeviceManagerImpl deviceManager;
    MemoryStorageSource storageSource;
    FlowReconcileManager flowReconcileMgr;

    private IOFSwitch makeSwitchMock(long id) {
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        expect(mockSwitch.getStringId()).
        andReturn(HexString.toHexString(id, 6)).anyTimes();
        expect(mockSwitch.getPort(anyShort())).
        andReturn(new OFPhysicalPort()).anyTimes();
        expect(mockSwitch.portEnabled(isA(OFPhysicalPort.class))).
        andReturn(true).anyTimes();
        return mockSwitch;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        ITopologyService topology = createMock(ITopologyService.class);
        fmc.addService(IThreadPoolService.class, tp);
        mockFloodlightProvider = getMockFloodlightProvider();
        deviceManager = new DeviceManagerImpl();
        flowReconcileMgr = new FlowReconcileManager();
        DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
        fmc.addService(IDeviceService.class, deviceManager);
        storageSource = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(ITopologyService.class, topology);
        tp.init(fmc);
        restApi.init(fmc);
        storageSource.init(fmc);
        deviceManager.init(fmc);
        flowReconcileMgr.init(fmc);
        entityClassifier.init(fmc);
        storageSource.startUp(fmc);
        deviceManager.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);

        reset(topology);
        topology.addListener(deviceManager);
        expectLastCall().anyTimes();
        replay(topology);

        IOFSwitch mockSwitch1 = makeSwitchMock(1L);
        IOFSwitch mockSwitch10 = makeSwitchMock(10L);
        IOFSwitch mockSwitch5 = makeSwitchMock(5L);
        IOFSwitch mockSwitch50 = makeSwitchMock(50L);
        Map<Long, IOFSwitch> switches = new HashMap<Long,IOFSwitch>();
        switches.put(1L, mockSwitch1);
        switches.put(10L, mockSwitch10);
        switches.put(5L, mockSwitch5);
        switches.put(50L, mockSwitch50);
        mockFloodlightProvider.setSwitches(switches);

        replay(mockSwitch1, mockSwitch5, mockSwitch10, mockSwitch50);

        // Build our test packet
        this.testARPReplyPacket_1 = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:01")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setVlanID((short)5)
        .setPayload(
                    new ARP()
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareAddressLength((byte) 6)
                    .setProtocolAddressLength((byte) 4)
                    .setOpCode(ARP.OP_REPLY)
                    .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:01"))
                    .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                    .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                    .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        this.testARPReplyPacket_1_Srld = testARPReplyPacket_1.serialize();

        // Another test packet with a different source IP
        this.testARPReplyPacket_2 = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:01")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                    new ARP()
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareAddressLength((byte) 6)
                    .setProtocolAddressLength((byte) 4)
                    .setOpCode(ARP.OP_REPLY)
                    .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:01"))
                    .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                    .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                    .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        this.testARPReplyPacket_2_Srld = testARPReplyPacket_2.serialize();

        this.testARPReplyPacket_3 = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:01")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                    new ARP()
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareAddressLength((byte) 6)
                    .setProtocolAddressLength((byte) 4)
                    .setOpCode(ARP.OP_REPLY)
                    .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:01"))
                    .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.3"))
                    .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                    .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        this.testARPReplyPacket_3_Serialized = testARPReplyPacket_3.serialize();
        
        // Build the PacketIn
        this.packetIn_1 = ((OFPacketIn) mockFloodlightProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(this.testARPReplyPacket_1_Srld)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) this.testARPReplyPacket_1_Srld.length);

        // Build the PacketIn
        this.packetIn_2 = ((OFPacketIn) mockFloodlightProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(this.testARPReplyPacket_2_Srld)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) this.testARPReplyPacket_2_Srld.length);

        // Build the PacketIn
        this.packetIn_3 = ((OFPacketIn) mockFloodlightProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(this.testARPReplyPacket_3_Serialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) this.testARPReplyPacket_3_Serialized.length);
    }




    
    @Test
    public void testLastSeen() throws Exception {
        Calendar c = Calendar.getInstance();
        Date d1 = c.getTime();
        Entity entity1 = new Entity(1L, null, null, null, null, d1);
        c.add(Calendar.SECOND, 1);
        Entity entity2 = new Entity(1L, null, 1, null, null, c.getTime());

        IDevice d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(c.getTime(), d.getLastSeen());
        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(c.getTime(), d.getLastSeen());

        deviceManager.startUp(null);
        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(d1, d.getLastSeen());
        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(c.getTime(), d.getLastSeen());
    }

    @Test
    public void testEntityLearning() throws Exception {
        IDeviceListener mockListener =
                createMock(IDeviceListener.class);
        expect(mockListener.getName()).andReturn("mockListener").atLeastOnce();
        expect(mockListener.isCallbackOrderingPostreq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        expect(mockListener.isCallbackOrderingPrereq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();

        replay(mockListener);
        deviceManager.addListener(mockListener);
        verify(mockListener);
        reset(mockListener);
        deviceManager.entityClassifier= new MockEntityClassifier();
        deviceManager.startUp(null);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(anyLong())).
        andReturn(1L).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(), anyShort())).
        andReturn(false).anyTimes();

        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(10L, (short)1, 10L, (short)1)).
        andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 1L, (short)1)).
        andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(50L, (short)3, 50L, (short)3)).
        andReturn(true).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        deviceManager.topology = mockTopology;

        Entity entity1 = new Entity(1L, null, null, 1L, 1, new Date());
        Entity entity2 = new Entity(1L, null, null, 10L, 1, new Date());
        Entity entity3 = new Entity(1L, null, 1, 10L, 1, new Date());
        Entity entity4 = new Entity(1L, null, 1, 1L, 1, new Date());
        Entity entity5 = new Entity(2L, (short)4, 1, 5L, 2, new Date());
        Entity entity6 = new Entity(2L, (short)4, 1, 50L, 3, new Date());
        Entity entity7 = new Entity(2L, (short)4, 2, 50L, 3, new Date());

        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener, mockTopology);

        Device d1 = deviceManager.learnDeviceByEntity(entity1);
        assertSame(d1, deviceManager.learnDeviceByEntity(entity1));
        assertSame(d1, deviceManager.findDeviceByEntity(entity1));
        assertEquals(DefaultEntityClassifier.entityClass ,
                          d1.entityClass);
        assertArrayEquals(new Short[] { -1 }, d1.getVlanId());
        assertArrayEquals(new Integer[] { }, d1.getIPv4Addresses());

        assertEquals(1, deviceManager.getAllDevices().size());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener);

        Device d2 = deviceManager.learnDeviceByEntity(entity2);
        assertFalse(d1.equals(d2));
        assertNotSame(d1, d2);
        assertNotSame(d1.getDeviceKey(), d2.getDeviceKey());
        assertEquals(MockEntityClassifier.testEC, d2.entityClass);
        assertArrayEquals(new Short[] { -1 }, d2.getVlanId());
        assertArrayEquals(new Integer[] { }, d2.getIPv4Addresses());

        assertEquals(2, deviceManager.getAllDevices().size());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        replay(mockListener);

        Device d3 = deviceManager.learnDeviceByEntity(entity3);
        assertNotSame(d2, d3);
        assertEquals(d2.getDeviceKey(), d3.getDeviceKey());
        assertEquals(MockEntityClassifier.testEC, d3.entityClass);
        assertArrayEquals(new Integer[] { 1 },
                          d3.getIPv4Addresses());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1) },
                          d3.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1) },
                          d3.getAttachmentPoints(true));
        assertArrayEquals(new Short[] { -1 },
                          d3.getVlanId());

        assertEquals(2, deviceManager.getAllDevices().size());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        replay(mockListener);

        Device d4 = deviceManager.learnDeviceByEntity(entity4);
        assertNotSame(d1, d4);
        assertEquals(d1.getDeviceKey(), d4.getDeviceKey());
        assertEquals(DefaultEntityClassifier.entityClass, d4.entityClass);
        assertArrayEquals(new Integer[] { 1 },
                          d4.getIPv4Addresses());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) },
                          d4.getAttachmentPoints());
        assertArrayEquals(new Short[] { -1 },
                          d4.getVlanId());

        assertEquals(2, deviceManager.getAllDevices().size());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceAdded((isA(IDevice.class)));
        replay(mockListener);

        Device d5 = deviceManager.learnDeviceByEntity(entity5);
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 2) },
                          d5.getAttachmentPoints());
        assertArrayEquals(new Short[] { (short) 4 },
                          d5.getVlanId());
        assertEquals(2L, d5.getMACAddress());
        assertEquals("00:00:00:00:00:02", d5.getMACAddressString());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener);

        Device d6 = deviceManager.learnDeviceByEntity(entity6);
        assertArrayEquals(new SwitchPort[] { new SwitchPort(50L, 3) },
                          d6.getAttachmentPoints());
        assertArrayEquals(new Short[] { (short) 4 },
                          d6.getVlanId());

        assertEquals(4, deviceManager.getAllDevices().size());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        replay(mockListener);

        Device d7 = deviceManager.learnDeviceByEntity(entity7);
        assertNotSame(d6, d7);
        assertEquals(d6.getDeviceKey(), d7.getDeviceKey());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(50L, 3) },
                          d7.getAttachmentPoints());
        assertArrayEquals(new Short[] { (short) 4 },
                          d7.getVlanId());

        assertEquals(4, deviceManager.getAllDevices().size());
        verify(mockListener);
        
        
        reset(mockListener);
        replay(mockListener);
        
        reset(deviceManager.topology);
        deviceManager.topology.addListener(deviceManager);
        expectLastCall().times(1);
        replay(deviceManager.topology);

        deviceManager.entityClassifier = new MockEntityClassifierMac();
        deviceManager.startUp(null);
        Entity entityNoClass = new Entity(5L, (short)1, 5, -1L, 1, new Date());
        assertEquals(null, deviceManager.learnDeviceByEntity(entityNoClass));
        
        verify(mockListener);
    }
    
    
    private void doTestEntityOrdering(boolean computeInsertionPoint) throws Exception {
        Entity e = new Entity(10L, null, null, null, null, null);
        IEntityClass ec = createNiceMock(IEntityClass.class);
        Device d = new Device(deviceManager, 1L, e, ec);
        
        int expectedLength = 1;
        Long[] macs = new Long[] {  5L,  // new first element
                                   15L,  // new last element
                                    7L,  // insert in middle
                                   12L,  // insert in middle
                                    6L,  // insert at idx 1
                                   14L,  // insert at idx length-2
                                    1L,
                                   20L
                                  };
        
        for (Long mac: macs) {
            e = new Entity(mac, null, null, null, null, null);
            int insertionPoint;
            if (computeInsertionPoint) {
                insertionPoint = -(Arrays.binarySearch(d.entities, e)+1);
            } else {
                insertionPoint = -1;
            }
            d = deviceManager.allocateDevice(d, e, insertionPoint);
            expectedLength++;
            assertEquals(expectedLength, d.entities.length);
            for (int i = 0; i < d.entities.length-1; i++)
                assertEquals(-1, d.entities[i].compareTo(d.entities[i+1]));
        }
    }
    
    @Test
    public void testEntityOrderingExternal() throws Exception {
        doTestEntityOrdering(true);
    }
    
    @Test
    public void testEntityOrderingInternal() throws Exception {
        doTestEntityOrdering(false);
    }

    @Test
    public void testAttachmentPointLearning() throws Exception {
        IDeviceListener mockListener =
                createMock(IDeviceListener.class);
        expect(mockListener.getName()).andReturn("mockListener").atLeastOnce();
        expect(mockListener.isCallbackOrderingPostreq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        expect(mockListener.isCallbackOrderingPrereq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();

        replay(mockListener);
        deviceManager.addListener(mockListener);
        verify(mockListener);
        reset(mockListener);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(1L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(10L)).
        andReturn(10L).anyTimes();
        expect(mockTopology.getL2DomainId(50L)).
        andReturn(10L).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(), anyShort())).
        andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                    anyLong(), anyShort())).andReturn(false).anyTimes();

        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 5L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(5L, (short)1, 10L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(10L, (short)1, 50L, (short)1)).
        andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);

        deviceManager.topology = mockTopology;

        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        Entity entity0 = new Entity(1L, null, null, null, null, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity2 = new Entity(1L, null, null, 5L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity3 = new Entity(1L, null, null, 10L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity4 = new Entity(1L, null, null, 50L, 1, c.getTime());

        IDevice d;
        SwitchPort[] aps;
        Integer[] ips;

        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener);

        deviceManager.learnDeviceByEntity(entity1);
        d = deviceManager.learnDeviceByEntity(entity0);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();

        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1) }, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] {new SwitchPort(5L, 1), new SwitchPort(10L, 1)}, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity4);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1),
                                             new SwitchPort(50L, 1) }, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);
    }
    
    private void verifyEntityArray(Entity[] expected, Device d) {
        Arrays.sort(expected);
        assertArrayEquals(expected, d.entities);
    }
    
    @Test
    public void testNoLearningOnInternalPorts() throws Exception {
        IDeviceListener mockListener =
                createMock(IDeviceListener.class);

        expect(mockListener.getName()).andReturn("mockListener").anyTimes();
        expect(mockListener.isCallbackOrderingPostreq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        expect(mockListener.isCallbackOrderingPrereq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();

        replay(mockListener);
        deviceManager.addListener(mockListener);
        verify(mockListener);
        reset(mockListener);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(1L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(2L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(3L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(4L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                    anyLong(), anyShort()))
                .andReturn(false).anyTimes();

        expect(mockTopology.isAttachmentPointPort(or(eq(1L), eq(3L)), anyShort()))
                .andReturn(true).anyTimes();
        // Switches 2 and 4 have only internal ports
        expect(mockTopology.isAttachmentPointPort(or(eq(2L), eq(4L)), anyShort()))
                .andReturn(false).anyTimes();
        
        expect(mockTopology.isConsistent(1L, (short)1, 3L, (short)1))
                .andReturn(false).once();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);

        deviceManager.topology = mockTopology;

        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity2 = new Entity(1L, null, 2, 2L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity3 = new Entity(1L, null, 3, 3L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity4 = new Entity(1L, null, 4, 4L, 1, c.getTime());

        IDevice d;
        SwitchPort[] aps;
        Integer[] ips;

        mockListener.deviceAdded(isA(IDevice.class));
        expectLastCall().once();
        replay(mockListener);
        
        // cannot learn device internal ports
        d = deviceManager.learnDeviceByEntity(entity2);
        assertNull(d);
        d = deviceManager.learnDeviceByEntity(entity4);
        assertNull(d);
        
        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity1 } , (Device)d);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);
        
        reset(mockListener);
        replay(mockListener);
        
        // don't learn
        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity1 } , (Device)d);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);
        
        reset(mockListener);
        mockListener.deviceMoved(isA(IDevice.class));
        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        replay(mockListener);
        
        // learn
        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(3L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity1, entity3 } , (Device)d);
        ips = d.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 1, 3 }, ips);
        verify(mockListener);
        
        reset(mockListener);
        replay(mockListener);
        
        // don't learn
        d = deviceManager.learnDeviceByEntity(entity4);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(3L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity1, entity3 } , (Device)d);
        ips = d.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 1, 3 }, ips);
        verify(mockListener);
    }
    
    @Test
    public void testAttachmentPointSuppression() throws Exception {
        IDeviceListener mockListener =
                createMock(IDeviceListener.class);

        expect(mockListener.getName()).andReturn("mockListener").anyTimes();
        expect(mockListener.isCallbackOrderingPostreq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        expect(mockListener.isCallbackOrderingPrereq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();

        replay(mockListener);
        deviceManager.addListener(mockListener);
        verify(mockListener);
        reset(mockListener);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(1L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(10L)).
        andReturn(10L).anyTimes();
        expect(mockTopology.getL2DomainId(50L)).
        andReturn(10L).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                    anyLong(), anyShort()))
                .andReturn(false).anyTimes();

        expect(mockTopology.isAttachmentPointPort(anyLong(), anyShort()))
                .andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(5L, (short)1, 50L, (short)1))
                .andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);

        deviceManager.topology = mockTopology;
        // suppress (1L, 1) and (10L, 1)
        deviceManager.addSuppressAPs(1L, (short)1);
        deviceManager.addSuppressAPs(10L, (short)1);

        Calendar c = Calendar.getInstance();
        Entity entity0 = new Entity(1L, null, null, null, null, c.getTime());
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity2 = new Entity(1L, null, 1, 5L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity3 = new Entity(1L, null, null, 10L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity4 = new Entity(1L, null, null, 50L, 1, c.getTime());

        IDevice d;
        SwitchPort[] aps;
        Integer[] ips;

        mockListener.deviceAdded(isA(IDevice.class));
        mockListener.deviceIPV4AddrChanged((isA(IDevice.class)));
        replay(mockListener);
        
        // TODO: we currently do learn entities on suppressed APs
        // // cannot learn device on suppressed AP
        // d = deviceManager.learnDeviceByEntity(entity1);
        // assertNull(d);

        deviceManager.learnDeviceByEntity(entity0);
        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertEquals(aps.length, 0);
        verifyEntityArray(new Entity[] { entity0, entity1} , (Device)d);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        //mockListener.deviceIPV4AddrChanged((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity0, entity1, entity2 } , (Device)d);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity0, entity1, entity2, entity3 } , (Device)d);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity4);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1),
                                             new SwitchPort(50L, 1) }, aps);
        verifyEntityArray(new Entity[] { entity0, entity1, entity2, entity3, entity4} , (Device)d);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);
    }

    @Test
    public void testBDAttachmentPointLearning() throws Exception {
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(anyLong())).
        andReturn(1L).anyTimes();
        expect(mockTopology.isAttachmentPointPort(anyLong(), anyShort())).
        andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(1L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(1L, (short)2)).
        andReturn(true).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(1L, (short)1,
                                                    1L, (short)2)).andReturn(true).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(1L, (short)2,
                                                    1L, (short)1)).andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(anyLong(), anyShort(), anyLong(), anyShort())).andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);

        deviceManager.topology = mockTopology;

        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        c.add(Calendar.MILLISECOND,
              (int)AttachmentPoint.OPENFLOW_TO_EXTERNAL_TIMEOUT/ 2);
        Entity entity2 = new Entity(1L, null, null, 1L, 2, c.getTime());
        c.add(Calendar.MILLISECOND,
              (int)AttachmentPoint.OPENFLOW_TO_EXTERNAL_TIMEOUT / 2 + 1);
        Entity entity3 = new Entity(1L, null, null, 1L, 2, c.getTime());

        IDevice d;
        SwitchPort[] aps;

        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);

        // this timestamp is too soon; don't switch
        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);

        // it should switch when we learn with a timestamp after the
        // timeout
        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 2) }, aps);
    }

    /**
     * This test verifies that the learning behavior on OFPP_LOCAL ports.
     * Once a host is learned on OFPP_LOCAL, it is allowed to move only from
     * one OFPP_LOCAL to another OFPP_LOCAL port.
     * @throws Exception
     */
    @Test
    public void testLOCALAttachmentPointLearning() throws Exception {
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(anyLong())).
        andReturn(1L).anyTimes();
        expect(mockTopology.isAttachmentPointPort(anyLong(), anyShort())).
        andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(1L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(1L, OFPort.OFPP_LOCAL.getValue())).
        andReturn(false).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(1L, (short)2)).
        andReturn(true).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(1L, (short)1,
                                                    1L, OFPort.OFPP_LOCAL.getValue())).andReturn(true).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(1L, OFPort.OFPP_LOCAL.getValue(),
                                                    1L, (short)2)).andReturn(true).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(1L, (short)2,
                                                    1L, OFPort.OFPP_LOCAL.getValue())).andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(anyLong(), anyShort(), anyLong(), anyShort())).andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);

        deviceManager.topology = mockTopology;

        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        c.add(Calendar.MILLISECOND,
              (int)AttachmentPoint.OPENFLOW_TO_EXTERNAL_TIMEOUT/ 2);
        Entity entity2 = new Entity(1L, null, null, 1L, (int)OFPort.OFPP_LOCAL.getValue(), c.getTime());
        c.add(Calendar.MILLISECOND,
              (int)AttachmentPoint.OPENFLOW_TO_EXTERNAL_TIMEOUT + 1);
        Entity entity3 = new Entity(1L, null, null, 1L, 2, c.getTime());

        IDevice d;
        SwitchPort[] aps;

        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);

        // Ensure that the attachment point changes to OFPP_LOCAL
        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, OFPort.OFPP_LOCAL.getValue()) }, aps);

        // Even though the new attachment point is consistent with old
        // and the time has elapsed, OFPP_LOCAL attachment point should
        // be maintained.
        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, OFPort.OFPP_LOCAL.getValue()) }, aps);
    }


    @Test
    public void testPacketIn() throws Exception {
        byte[] dataLayerSource =
                ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

        // Mock up our expected behavior
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(EasyMock.anyLong(),
                                                  EasyMock.anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(EasyMock.anyLong(),
                                         EasyMock.anyShort(),
                                         EasyMock.anyLong(),
                                         EasyMock.anyShort())).andReturn(false).
                                         anyTimes();
        expect(mockTopology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        replay(mockTopology);

        Date currentDate = new Date();

        // build our expected Device
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        Device device =
                new Device(deviceManager,
                           new Long(deviceManager.deviceKeyCounter),
                           new Entity(Ethernet.toLong(dataLayerSource),
                                      (short)5,
                                      ipaddr,
                                      1L,
                                      1,
                                      currentDate),
                                      DefaultEntityClassifier.entityClass);




        // Get the listener and trigger the packet in
        IOFSwitch switch1 = mockFloodlightProvider.getSwitches().get(1L);
        mockFloodlightProvider.dispatchMessage(switch1, this.packetIn_1);

        // Verify the replay matched our expectations
        // verify(mockTopology);

        // Verify the device
        Device rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(dataLayerSource),
                                         (short)5, null, null, null);

        assertEquals(device, rdevice);
        assertEquals(new Short((short)5), rdevice.getVlanId()[0]);

        Device result = null;
        Iterator<? extends IDevice> dstiter =
                deviceManager.queryClassDevices(device.getEntityClass(),
                                                null, null, ipaddr,
                                                null, null);
        if (dstiter.hasNext()) {
            result = (Device)dstiter.next();
        }

        assertEquals(device, result);

        device =
                new Device(device,
                           new Entity(Ethernet.toLong(dataLayerSource),
                                      (short)5,
                                      ipaddr,
                                      5L,
                                      2,
                                      currentDate),
                                      -1);

        reset(mockTopology);
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).
                                                  anyTimes();
        expect(mockTopology.isConsistent(EasyMock.anyLong(),
                                         EasyMock.anyShort(),
                                         EasyMock.anyLong(),
                                         EasyMock.anyShort())).andReturn(false).
                                         anyTimes();
        expect(mockTopology.isBroadcastDomainPort(EasyMock.anyLong(),
                                                  EasyMock.anyShort()))
                                                  .andReturn(false)
                                                  .anyTimes();
        expect(mockTopology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).andReturn(1L).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(1L, (short)1, 5L, (short)2)).
               andReturn(false).anyTimes();

        // Start recording the replay on the mocks
        replay(mockTopology);
        // Get the listener and trigger the packet in
        IOFSwitch switch5 = mockFloodlightProvider.getSwitches().get(5L);
        mockFloodlightProvider.
        dispatchMessage(switch5, this.packetIn_1.setInPort((short)2));

        // Verify the replay matched our expectations
        verify(mockTopology);

        // Verify the device
        rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(dataLayerSource),
                                         (short)5, null, null, null);
        assertEquals(device, rdevice);
    }

    
    /**
     * Note: Entity expiration does not result in device moved notification.
     * @throws Exception
     */
    public void doTestEntityExpiration() throws Exception {
        IDeviceListener mockListener =
                createMock(IDeviceListener.class);
        expect(mockListener.getName()).andReturn("mockListener").anyTimes();
        expect(mockListener.isCallbackOrderingPostreq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        expect(mockListener.isCallbackOrderingPrereq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();

        expect(mockTopology.isBroadcastDomainPort(1L, (short)1)).andReturn(false).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(5L, (short)1)).andReturn(false).anyTimes();
        expect(mockTopology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).andReturn(5L).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 5L, (short)1)).
        andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);
        deviceManager.topology = mockTopology;

        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, 2, 1L, 1, c.getTime());
        c.add(Calendar.MILLISECOND, -DeviceManagerImpl.ENTITY_TIMEOUT-1);
        Entity entity2 = new Entity(1L, null, 1, 5L, 1, c.getTime());

        deviceManager.learnDeviceByEntity(entity1);
        IDevice d = deviceManager.learnDeviceByEntity(entity2);
        assertArrayEquals(new Integer[] { 1, 2 }, d.getIPv4Addresses());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 1)},
                                             d.getAttachmentPoints());
        Iterator<? extends IDevice> diter =
                deviceManager.queryClassDevices(d.getEntityClass(),
                                                null, null, 1, null, null);
        assertTrue(diter.hasNext());
        assertEquals(d.getDeviceKey(), diter.next().getDeviceKey());
        diter = deviceManager.queryClassDevices(d.getEntityClass(),
                                                null, null, 2, null, null);
        assertTrue(diter.hasNext());
        assertEquals(d.getDeviceKey(), diter.next().getDeviceKey());

        replay(mockListener);
        deviceManager.addListener(mockListener);
        verify(mockListener);
        reset(mockListener);

        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        replay(mockListener);
        deviceManager.entityCleanupTask.reschedule(0, null);

        d = deviceManager.getDevice(d.getDeviceKey());
        assertArrayEquals(new Integer[] { 2 }, d.getIPv4Addresses());

        // Attachment points are not removed, previous ones are still valid.
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 1) },
                          d.getAttachmentPoints());
        diter = deviceManager.queryClassDevices(d.getEntityClass(),
                                                null, null, 2, null, null);
        assertTrue(diter.hasNext());
        assertEquals(d.getDeviceKey(), diter.next().getDeviceKey());
        diter = deviceManager.queryClassDevices(d.getEntityClass(),
                                                null, null, 1, null, null);
        assertFalse(diter.hasNext());

        d = deviceManager.findDevice(1L, null, null, null, null);
        assertArrayEquals(new Integer[] { 2 }, d.getIPv4Addresses());

        // Attachment points are not removed, previous ones are still valid.
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 1) },
                          d.getAttachmentPoints());

        verify(mockListener);
    }

    public void doTestDeviceExpiration() throws Exception {
        IDeviceListener mockListener =
                createMock(IDeviceListener.class);
        expect(mockListener.getName()).andReturn("mockListener").anyTimes();
        expect(mockListener.isCallbackOrderingPostreq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        expect(mockListener.isCallbackOrderingPrereq((String)anyObject(), (String)anyObject()))
        .andReturn(false).atLeastOnce();
        
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MILLISECOND, -DeviceManagerImpl.ENTITY_TIMEOUT-1);
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        Entity entity2 = new Entity(1L, null, 2, 5L, 1, c.getTime());
        
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;

        expect(mockTopology.isAttachmentPointPort(EasyMock.anyLong(),
                                           EasyMock.anyShort())).
                                           andReturn(true).
                                           anyTimes();
        expect(mockTopology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).andReturn(1L).anyTimes();
        expect(mockTopology.isConsistent(EasyMock.anyLong(),
                                         EasyMock.anyShort(),
                                         EasyMock.anyLong(),
                                         EasyMock.anyShort())).andReturn(false).
                                         anyTimes();
        expect(mockTopology.isBroadcastDomainPort(EasyMock.anyLong(),
                                                  EasyMock.anyShort())).
                                                  andReturn(false).anyTimes();
        replay(mockTopology);

        IDevice d = deviceManager.learnDeviceByEntity(entity2);
        d = deviceManager.learnDeviceByEntity(entity1);
        assertArrayEquals(new Integer[] { 1, 2 }, d.getIPv4Addresses());

        replay(mockListener);
        deviceManager.addListener(mockListener);
        verify(mockListener);
        reset(mockListener);

        mockListener.deviceRemoved(isA(IDevice.class));
        replay(mockListener);
        deviceManager.entityCleanupTask.reschedule(0, null);

        IDevice r = deviceManager.getDevice(d.getDeviceKey());
        assertNull(r);
        Iterator<? extends IDevice> diter =
                deviceManager.queryClassDevices(d.getEntityClass(),
                                                null, null, 1, null, null);
        assertFalse(diter.hasNext());

        r = deviceManager.findDevice(1L, null, null, null, null);
        assertNull(r);
        
        verify(mockListener);
    }
    
    /*
     * A ConcurrentHashMap for devices (deviceMap) that can be used to test
     * code that specially handles concurrent modification situations. In
     * particular, we overwrite values() and will replace / remove all the
     * elements returned by values.
     * 
     * The remove flag in the constructor specifies if devices returned by
     * values() should be removed or replaced.
     */
    protected static class ConcurrentlyModifiedDeviceMap
                            extends ConcurrentHashMap<Long, Device> {
        private static final long serialVersionUID = 7784938535441180562L;
        protected boolean remove;
        public ConcurrentlyModifiedDeviceMap(boolean remove) {
            super();
            this.remove = remove;
        }
        
        @Override
        public Collection<Device> values() {
            // Get the values from the real map and copy them since
            // the collection returned by values can reflect changed
            Collection<Device> devs = new ArrayList<Device>(super.values());
            for (Device d: devs) {
                if (remove) {
                    // We remove the device from the underlying map
                    super.remove(d.getDeviceKey());
                } else {
                    super.remove(d.getDeviceKey());
                    // We add a different Device instance with the same
                    // key to the map. We'll do some hackery so the device
                    // is different enough to compare differently in equals
                    // but otherwise looks the same.
                    // It's ugly but it works.
                    Entity[] curEntities = new Entity[d.getEntities().length];
                    int i = 0;
                    // clone entities
                    for (Entity e: d.getEntities()) {
                        curEntities[i] = new Entity (e.macAddress,
                                                     e.vlan,
                                                     e.ipv4Address,
                                                     e.switchDPID,
                                                     e.switchPort,
                                                     e.lastSeenTimestamp);
                        if (e.vlan == null)
                            curEntities[i].vlan = (short)1;
                        else
                            curEntities[i].vlan = (short)((e.vlan + 1 % 4095)+1);
                        i++;
                    }
                    Device newDevice = new Device(d, curEntities[0], -1);
                    newDevice.entities = curEntities;
                    assertEquals(false, newDevice.equals(d));
                    super.put(newDevice.getDeviceKey(), newDevice);
                }
            }
            return devs;
        }
    }
   
    @Test
    public void testEntityExpiration() throws Exception {
        doTestEntityExpiration();
    }
    
    @Test
    public void testDeviceExpiration() throws Exception {
        doTestDeviceExpiration();
    }
    
    /* Test correct entity cleanup behavior when a concurrent modification
     * occurs.
     */
    @Test
    public void testEntityExpirationConcurrentModification() throws Exception {
        deviceManager.deviceMap = new ConcurrentlyModifiedDeviceMap(false);
        doTestEntityExpiration();
    }
    
    /* Test correct entity cleanup behavior when a concurrent remove
     * occurs.
     */
    @Test
    public void testDeviceExpirationConcurrentRemove() throws Exception {
        deviceManager.deviceMap = new ConcurrentlyModifiedDeviceMap(true);
        doTestDeviceExpiration();
    }
    
    /* Test correct entity cleanup behavior when a concurrent modification
     * occurs.
     */
    @Test
    public void testDeviceExpirationConcurrentModification() throws Exception {
        deviceManager.deviceMap = new ConcurrentlyModifiedDeviceMap(false);
        doTestDeviceExpiration();
    }
    

    @Test
    public void testAttachmentPointFlapping() throws Exception {
        Calendar c = Calendar.getInstance();

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                    anyLong(), anyShort())).andReturn(false).anyTimes();
        expect(mockTopology.getL2DomainId(anyLong())).
        andReturn(1L).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 1L, (short)1)).
        andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 5L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 10L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(5L, (short)1, 10L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(10L, (short)1, 1L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(5L, (short)1, 1L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(10L, (short)1, 5L, (short)1)).
        andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();


        replay(mockTopology);
        deviceManager.topology = mockTopology;

        Entity entity1 = new Entity(1L, null, null, 1L, 1, c.getTime());
        Entity entity1a = new Entity(1L, null, 1, 1L, 1, c.getTime());
        Entity entity2 = new Entity(1L, null, null, 5L, 1, c.getTime());
        Entity entity3 = new Entity(1L, null, null, 10L, 1, c.getTime());
        entity1.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/2);
        entity1a.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, 1);
        entity2.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, 1);
        entity3.setLastSeenTimestamp(c.getTime());



        IDevice d;
        d = deviceManager.learnDeviceByEntity(entity1);
        d = deviceManager.learnDeviceByEntity(entity1a);
        d = deviceManager.learnDeviceByEntity(entity2);
        d = deviceManager.learnDeviceByEntity(entity3);

        // all entities are active, so entity3 should win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1) },
                          d.getAttachmentPoints());

        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1),},
                              d.getAttachmentPoints(true));

        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/4);
        entity1.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity1);

        // all are still active; entity3 should still win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) },
                          d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 1,
                                                            ErrorStatus.DUPLICATE_DEVICE),
                                                            new SwitchPort(10L, 1,
                                                                           ErrorStatus.DUPLICATE_DEVICE) },
                                                                           d.getAttachmentPoints(true));

        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT+2000);
        entity1.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity1);

        assertEquals(entity1.getActiveSince(), entity1.getLastSeenTimestamp());
        // entity1 should now be the only active entity
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) },
                          d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) },
                          d.getAttachmentPoints(true));
    }


    @Test
    public void testAttachmentPointFlappingTwoCluster() throws Exception {
        Calendar c = Calendar.getInstance();

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                    anyLong(), anyShort())).andReturn(false).anyTimes();
        expect(mockTopology.getL2DomainId(1L)).
        andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).
        andReturn(5L).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 1L, (short)2)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)2, 5L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(5L, (short)1, 5L, (short)2)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)2, 1L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 5L, (short)1)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 5L, (short)2)).
        andReturn(false).anyTimes();
        expect(mockTopology.isConsistent(5L, (short)2, 5L, (short)1)).
        andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);
        deviceManager.topology = mockTopology;

        Entity entity1 = new Entity(1L, null, null, 1L, 1, c.getTime());
        Entity entity2 = new Entity(1L, null, null, 1L, 2, c.getTime());
        Entity entity3 = new Entity(1L, null, null, 5L, 1, c.getTime());
        Entity entity4 = new Entity(1L, null, null, 5L, 2, c.getTime());
        entity1.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/2);
        c.add(Calendar.MILLISECOND, 1);
        entity2.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, 1);
        entity3.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, 1);
        entity4.setLastSeenTimestamp(c.getTime());

        deviceManager.learnDeviceByEntity(entity1);
        deviceManager.learnDeviceByEntity(entity2);
        deviceManager.learnDeviceByEntity(entity3);
        IDevice d = deviceManager.learnDeviceByEntity(entity4);

        // all entities are active, so entities 2,4 should win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 2),
                                             new SwitchPort(5L, 2) },
                                             d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 2),
                                             new SwitchPort(5L, 2)},
                                             d.getAttachmentPoints(true));

        c.add(Calendar.MILLISECOND, 1);
        entity1.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity1);

        // all entities are active, so entities 2,4 should win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 2) },
                                             d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 2),
                                             new SwitchPort(1L, 2, ErrorStatus.DUPLICATE_DEVICE)},
                                             d.getAttachmentPoints(true));

        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT+1);
        entity1.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity1);

        // entities 3,4 are still in conflict, but 1 should be resolved
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 2) },
                                             d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 2)},
                                             d.getAttachmentPoints(true));

        entity3.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity3);

        // no conflicts, 1 and 3 will win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 1) },
                                             d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 1) },
                                             d.getAttachmentPoints(true));

    }

    protected void doTestDeviceQuery() throws Exception {
        Entity entity1 = new Entity(1L, (short)1, 1, 1L, 1, new Date());
        Entity entity2 = new Entity(2L, (short)2, 2, 1L, 2, new Date());
        Entity entity3 = new Entity(3L, (short)3, 3, 5L, 1, new Date());
        Entity entity4 = new Entity(4L, (short)4, 3, 5L, 2, new Date());
        Entity entity5 = new Entity(1L, (short)4, 3, 5L, 2, new Date());

        deviceManager.learnDeviceByEntity(entity1);
        deviceManager.learnDeviceByEntity(entity2);
        deviceManager.learnDeviceByEntity(entity3);
        deviceManager.learnDeviceByEntity(entity4);

        Iterator<? extends IDevice> iter =
                deviceManager.queryDevices(null, (short)1, 1, null, null);
        int count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(1, count);

        iter = deviceManager.queryDevices(null, (short)3, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(1, count);

        iter = deviceManager.queryDevices(null, (short)1, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(0, count);

        deviceManager.learnDeviceByEntity(entity5);
        iter = deviceManager.queryDevices(null, (short)4, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(2, count);
    }

    @Test
    public void testDeviceIndex() throws Exception {
        EnumSet<IDeviceService.DeviceField> indexFields =
                EnumSet.noneOf(IDeviceService.DeviceField.class);
        indexFields.add(IDeviceService.DeviceField.IPV4);
        indexFields.add(IDeviceService.DeviceField.VLAN);
        deviceManager.addIndex(false, indexFields);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        replay(mockTopology);
        doTestDeviceQuery();
    }

    @Test
    public void testDeviceQuery() throws Exception {
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        replay(mockTopology);
        
        doTestDeviceQuery();
    }

    protected void doTestDeviceClassQuery() throws Exception {
        Entity entity1 = new Entity(1L, (short)1, 1, 1L, 1, new Date());
        Entity entity2 = new Entity(2L, (short)2, 2, 1L, 2, new Date());
        Entity entity3 = new Entity(3L, (short)3, 3, 5L, 1, new Date());
        Entity entity4 = new Entity(4L, (short)4, 3, 5L, 2, new Date());
        Entity entity5 = new Entity(1L, (short)4, 3, 5L, 2, new Date());

        IDevice d = deviceManager.learnDeviceByEntity(entity1);
        deviceManager.learnDeviceByEntity(entity2);
        deviceManager.learnDeviceByEntity(entity3);
        deviceManager.learnDeviceByEntity(entity4);

        Iterator<? extends IDevice> iter =
                deviceManager.queryClassDevices(d.getEntityClass(), null,
                                                (short)1, 1, null, null);
        int count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(1, count);

        iter = deviceManager.queryClassDevices(d.getEntityClass(), null,
                                               (short)3, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(1, count);

        iter = deviceManager.queryClassDevices(d.getEntityClass(), null,
                                               (short)1, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(0, count);

        deviceManager.learnDeviceByEntity(entity5);
        iter = deviceManager.queryClassDevices(d.getEntityClass(), null,
                                               (short)4, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(2, count);
    }

    @Test
    public void testDeviceClassIndex() throws Exception {
        EnumSet<IDeviceService.DeviceField> indexFields =
                EnumSet.noneOf(IDeviceService.DeviceField.class);
        indexFields.add(IDeviceService.DeviceField.IPV4);
        indexFields.add(IDeviceService.DeviceField.VLAN);
        deviceManager.addIndex(true, indexFields);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        replay(mockTopology);

        doTestDeviceClassQuery();
    }

    @Test
    public void testDeviceClassQuery() throws Exception {
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        replay(mockTopology);

        doTestDeviceClassQuery();
    }

    @Test
    public void testFindDevice() {
        boolean exceptionCaught;
        deviceManager.entityClassifier= new MockEntityClassifierMac();
        deviceManager.startUp(null);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(EasyMock.anyLong())).andReturn(1L).anyTimes();
        replay(mockTopology);

        Entity entity1 = new Entity(1L, (short)1, 1, 1L, 1, new Date());
        Entity entity2 = new Entity(2L, (short)2, 2, 1L, 2, new Date());
        Entity entity2b = new Entity(22L, (short)2, 2, 1L, 2, new Date());
        
        Entity entity3 = new Entity(3L, (short)1, 3, 2L, 1, new Date());
        Entity entity4 = new Entity(4L, (short)2, 4, 2L, 2, new Date());
        
        Entity entity5 = new Entity(5L, (short)1, 5, 3L, 1, new Date());
        

        IDevice d1 = deviceManager.learnDeviceByEntity(entity1);
        IDevice d2 = deviceManager.learnDeviceByEntity(entity2);
        IDevice d3 = deviceManager.learnDeviceByEntity(entity3);
        IDevice d4 = deviceManager.learnDeviceByEntity(entity4);
        IDevice d5 = deviceManager.learnDeviceByEntity(entity5);
        
        // Make sure the entity classifier worked as expected
        assertEquals(MockEntityClassifierMac.testECMac1, d1.getEntityClass());
        assertEquals(MockEntityClassifierMac.testECMac1, d2.getEntityClass());
        assertEquals(MockEntityClassifierMac.testECMac2, d3.getEntityClass());
        assertEquals(MockEntityClassifierMac.testECMac2, d4.getEntityClass());
        assertEquals(DefaultEntityClassifier.entityClass,
                     d5.getEntityClass());
        
        // Look up the device using findDevice() which uses only the primary
        // index
        assertEquals(d1, deviceManager.findDevice(entity1.getMacAddress(),
                                                  entity1.getVlan(),
                                                  entity1.getIpv4Address(),
                                                  entity1.getSwitchDPID(),
                                                  entity1.getSwitchPort()));
        // port changed. Device will be found through class index
        assertEquals(d1, deviceManager.findDevice(entity1.getMacAddress(),
                                                  entity1.getVlan(),
                                                  entity1.getIpv4Address(),
                                                  entity1.getSwitchDPID(),
                                                  entity1.getSwitchPort()+1));
        // VLAN changed. No device matches
        assertEquals(null, deviceManager.findDevice(entity1.getMacAddress(),
                                                  (short)42,
                                                  entity1.getIpv4Address(),
                                                  entity1.getSwitchDPID(),
                                                  entity1.getSwitchPort()));
        assertEquals(null, deviceManager.findDevice(entity1.getMacAddress(),
                                                  null,
                                                  entity1.getIpv4Address(),
                                                  entity1.getSwitchDPID(),
                                                  entity1.getSwitchPort()));
        assertEquals(d2, deviceManager.findDeviceByEntity(entity2));
        assertEquals(null, deviceManager.findDeviceByEntity(entity2b));
        assertEquals(d3, deviceManager.findDevice(entity3.getMacAddress(),
                                                  entity3.getVlan(),
                                                  entity3.getIpv4Address(),
                                                  entity3.getSwitchDPID(),
                                                  entity3.getSwitchPort()));
        // switch and port not set. throws exception
        exceptionCaught = false;
        try {
            assertEquals(null, deviceManager.findDevice(entity3.getMacAddress(),
                                                        entity3.getVlan(),
                                                        entity3.getIpv4Address(),
                                                        null,
                                                        null));
        }
        catch (IllegalArgumentException e) {
            exceptionCaught = true;
        }
        if (!exceptionCaught)
            fail("findDevice() did not throw IllegalArgumentException");
        assertEquals(d4, deviceManager.findDeviceByEntity(entity4));
        assertEquals(d5, deviceManager.findDevice(entity5.getMacAddress(),
                                                  entity5.getVlan(),
                                                  entity5.getIpv4Address(),
                                                  entity5.getSwitchDPID(),
                                                  entity5.getSwitchPort()));
        // switch and port not set. throws exception (swith/port are key
        // fields of IEntityClassifier but not d5.entityClass
        exceptionCaught = false;
        try {
            assertEquals(d5, deviceManager.findDevice(entity5.getMacAddress(),
                                                      entity5.getVlan(),
                                                      entity5.getIpv4Address(),
                                                      null,
                                                      null));
        }
        catch (IllegalArgumentException e) {
            exceptionCaught = true;
        }
        if (!exceptionCaught)
            fail("findDevice() did not throw IllegalArgumentException");
        
        
        Entity entityNoClass = new Entity(5L, (short)1, 5, -1L, 1, new Date());
        assertEquals(null, deviceManager.findDeviceByEntity(entityNoClass));
        
        
        // Now look up destination devices
        assertEquals(d1, deviceManager.findClassDevice(d2.getEntityClass(),
                                                  entity1.getMacAddress(),
                                                  entity1.getVlan(),
                                                  entity1.getIpv4Address()));
        assertEquals(d1, deviceManager.findClassDevice(d2.getEntityClass(),
                                                  entity1.getMacAddress(),
                                                  entity1.getVlan(),
                                                  null));
        assertEquals(null, deviceManager.findClassDevice(d2.getEntityClass(),
                                                  entity1.getMacAddress(),
                                                  (short) -1,
                                                  0));
    }
    
    @Test
    public void testGetIPv4Addresses() {
        // Looks like Date is only 1s granularity
        
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(mockTopology.isConsistent(EasyMock.anyLong(),
                                         EasyMock.anyShort(),
                                         EasyMock.anyLong(),
                                         EasyMock.anyShort()))
                                         .andReturn(false)
                                         .anyTimes();
        expect(mockTopology.isBroadcastDomainPort(EasyMock.anyLong(),
                                                  EasyMock.anyShort()))
                                                  .andReturn(false)
                                                  .anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(EasyMock.anyLong(),
                                                    EasyMock.anyShort(),
                                                    EasyMock.anyLong(),
                                                    EasyMock.anyShort())).
                                                    andReturn(false).anyTimes();
        replay(mockTopology);

        Entity e1 = new Entity(1L, (short)1, null, null, null, new Date(2000));
        Device d1 = deviceManager.learnDeviceByEntity(e1);
        assertArrayEquals(new Integer[0], d1.getIPv4Addresses());
        
        
        Entity e2 = new Entity(2L, (short)2, 2, null, null, new Date(2000));
        Device d2 = deviceManager.learnDeviceByEntity(e2);
        d2 = deviceManager.learnDeviceByEntity(e2);
        assertArrayEquals(new Integer[] { 2 }, d2.getIPv4Addresses());
        // More than one entity
        Entity e2b = new Entity(2L, (short)2, null, 2L, 2, new Date(3000));
        d2 = deviceManager.learnDeviceByEntity(e2b);
        assertEquals(2, d2.entities.length);
        assertArrayEquals(new Integer[] { 2 }, d2.getIPv4Addresses());
        // and now add an entity with an IP
        Entity e2c = new Entity(2L, (short)2, 2, 2L, 3, new Date(3000));
        d2 = deviceManager.learnDeviceByEntity(e2c);
        assertArrayEquals(new Integer[] { 2 }, d2.getIPv4Addresses());
        assertEquals(3, d2.entities.length);
        
        // Other devices with different IPs shouldn't interfere
        Entity e3 = new Entity(3L, (short)3, 3, null, null, new Date(4000));
        Entity e3b = new Entity(3L, (short)3, 3, 3L, 3, new Date(4400));
        Device d3 = deviceManager.learnDeviceByEntity(e3);
        d3 = deviceManager.learnDeviceByEntity(e3b);
        assertArrayEquals(new Integer[] { 2 }, d2.getIPv4Addresses());
        assertArrayEquals(new Integer[] { 3 }, d3.getIPv4Addresses());
        
        // Add another IP to d3
        Entity e3c = new Entity(3L, (short)3, 33, 3L, 3, new Date(4400));
        d3 = deviceManager.learnDeviceByEntity(e3c);
        Integer[] ips = d3.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 3, 33 }, ips);
        
        // Add another device that also claims IP2 but is older than e2
        Entity e4 = new Entity(4L, (short)4, 2, null, null, new Date(1000));
        Entity e4b = new Entity(4L, (short)4, null, 4L, 4, new Date(1000));
        Device d4 = deviceManager.learnDeviceByEntity(e4);
        assertArrayEquals(new Integer[] { 2 }, d2.getIPv4Addresses());
        assertArrayEquals(new Integer[0],  d4.getIPv4Addresses());
        // add another entity to d4
        d4 = deviceManager.learnDeviceByEntity(e4b);
        assertArrayEquals(new Integer[0], d4.getIPv4Addresses());
        
        // Make e4 and e4a newer
        Entity e4c = new Entity(4L, (short)4, 2, null, null, new Date(5000));
        Entity e4d = new Entity(4L, (short)4, null, 4L, 5, new Date(5000));
        d4 = deviceManager.learnDeviceByEntity(e4c);
        d4 = deviceManager.learnDeviceByEntity(e4d);
        assertArrayEquals(new Integer[0], d2.getIPv4Addresses());
        // FIXME: d4 should not return IP4
        assertArrayEquals(new Integer[] { 2 }, d4.getIPv4Addresses());
        
        // Add another newer entity to d2 but with different IP
        Entity e2d = new Entity(2L, (short)2, 22, 4L, 6, new Date(6000));
        d2 = deviceManager.learnDeviceByEntity(e2d);
        assertArrayEquals(new Integer[] { 22 }, d2.getIPv4Addresses());
        assertArrayEquals(new Integer[] { 2 }, d4.getIPv4Addresses());

        // new IP for d2,d4 but with same timestamp. Both devices get the IP
        Entity e2e = new Entity(2L, (short)2, 42, 2L, 4, new Date(7000));
        d2 = deviceManager.learnDeviceByEntity(e2e);
        ips= d2.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 22, 42 }, ips);
        Entity e4e = new Entity(4L, (short)4, 42, 4L, 7, new Date(7000));
        d4 = deviceManager.learnDeviceByEntity(e4e);
        ips= d4.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 2, 42 }, ips);
        
        // add a couple more IPs
        Entity e2f = new Entity(2L, (short)2, 4242, 2L, 5, new Date(8000));
        d2 = deviceManager.learnDeviceByEntity(e2f);
        ips= d2.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 22, 42, 4242 }, ips);
        Entity e4f = new Entity(4L, (short)4, 4242, 4L, 8, new Date(9000));
        d4 = deviceManager.learnDeviceByEntity(e4f);
        ips= d4.getIPv4Addresses();
        Arrays.sort(ips);
        assertArrayEquals(new Integer[] { 2, 42, 4242 }, ips);
    }
    
    // TODO: this test should really go into a separate class that collects
    // unit tests for Device
    @Test
    public void testGetSwitchPortVlanId() {
            Entity entity1 = new Entity(1L, (short)1, null, 10L, 1, new Date());
            Entity entity2 = new Entity(1L, null, null, 10L, 1, new Date());
            Entity entity3 = new Entity(1L, (short)3, null,  1L, 1, new Date());
            Entity entity4 = new Entity(1L, (short)42, null,  1L, 1, new Date());
            Entity[] entities = new Entity[] { entity1, entity2,
                                               entity3, entity4
                                             };
            Device d = new Device(null,1L, null, null, null,
                                  Arrays.asList(entities), null);
            SwitchPort swp1x1 = new SwitchPort(1L, 1);
            SwitchPort swp1x2 = new SwitchPort(1L, 2);
            SwitchPort swp2x1 = new SwitchPort(2L, 1);
            SwitchPort swp10x1 = new SwitchPort(10L, 1);
            assertArrayEquals(new Short[] { -1, 1},
                              d.getSwitchPortVlanIds(swp10x1));
            assertArrayEquals(new Short[] { 3, 42},
                              d.getSwitchPortVlanIds(swp1x1));
            assertArrayEquals(new Short[0],
                              d.getSwitchPortVlanIds(swp1x2));
            assertArrayEquals(new Short[0],
                              d.getSwitchPortVlanIds(swp2x1));
    }
    
    @Test
    public void testReclassifyDevice() {
        MockFlexEntityClassifier flexClassifier =
                new MockFlexEntityClassifier();
        deviceManager.entityClassifier= flexClassifier;
        deviceManager.startUp(null);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).
                                                  andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(mockTopology.isConsistent(EasyMock.anyLong(),
                                         EasyMock.anyShort(),
                                         EasyMock.anyLong(),
                                         EasyMock.anyShort()))
                                         .andReturn(false)
                                         .anyTimes();
        expect(mockTopology.isBroadcastDomainPort(EasyMock.anyLong(),
                                                  EasyMock.anyShort()))
                                                  .andReturn(false)
                                                  .anyTimes();
        replay(mockTopology);
        
        //flexClassifier.createTestEntityClass("Class1");
        
        Entity entity1 = new Entity(1L, (short)1, 1, 1L, 1, new Date());
        Entity entity1b = new Entity(1L, (short)2, 1, 1L, 1, new Date());
        Entity entity2 = new Entity(2L, (short)1, 2, 2L, 2, new Date());
        Entity entity2b = new Entity(2L, (short)2, 2, 2L, 2, new Date());
        
        
        Device d1 = deviceManager.learnDeviceByEntity(entity1);
        Device d2 = deviceManager.learnDeviceByEntity(entity2);
        Device d1b = deviceManager.learnDeviceByEntity(entity1b);
        Device d2b = deviceManager.learnDeviceByEntity(entity2b);
        
        d1 = deviceManager.getDeviceIteratorForQuery(entity1.getMacAddress(),
                        entity1.getVlan(), entity1.getIpv4Address(),
                        entity1.getSwitchDPID(), entity1.getSwitchPort())
                        .next();
        d1b = deviceManager.getDeviceIteratorForQuery(entity1b.getMacAddress(),
                entity1b.getVlan(), entity1b.getIpv4Address(),
                entity1b.getSwitchDPID(), entity1b.getSwitchPort()).next();
        
        assertEquals(d1, d1b);
        
        d2 = deviceManager.getDeviceIteratorForQuery(entity2.getMacAddress(),
                entity2.getVlan(), entity2.getIpv4Address(),
                entity2.getSwitchDPID(), entity2.getSwitchPort()).next();
        d2b = deviceManager.getDeviceIteratorForQuery(entity2b.getMacAddress(),
                entity2b.getVlan(), entity2b.getIpv4Address(),
                entity2b.getSwitchDPID(), entity2b.getSwitchPort()).next();
        assertEquals(d2, d2b);
        
        IEntityClass eC1 = flexClassifier.createTestEntityClass("C1");
        IEntityClass eC2 = flexClassifier.createTestEntityClass("C2");
        
        flexClassifier.addVlanEntities((short)1, eC1);
        flexClassifier.addVlanEntities((short)2, eC1);
        
        deviceManager.reclassifyDevice(d1);
        deviceManager.reclassifyDevice(d2);
        
        d1 = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity1));
        d1b = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity1b));
        
        assertEquals(d1, d1b);
        
        d2 = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity2));
        d2b = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity2b));
        
        assertEquals(d2, d2b);
                        
        flexClassifier.addVlanEntities((short)1, eC2);
        
        deviceManager.reclassifyDevice(d1);
        deviceManager.reclassifyDevice(d2);
        d1 = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity1));
        d1b = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity1b));
        d2 = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity2));
        d2b = deviceManager.deviceMap.get(
                deviceManager.primaryIndex.findByEntity(entity2b));
        
        assertNotSame(d1, d1b);
       
        assertNotSame(d2, d2b);
        
        flexClassifier.addVlanEntities((short)1, eC1);
        deviceManager.reclassifyDevice(d1);
        deviceManager.reclassifyDevice(d2);
        ClassState classState = deviceManager.classStateMap.get(eC1.getName());

        Long deviceKey1 = null;
        Long deviceKey1b = null;
        Long deviceKey2 = null;
        Long deviceKey2b = null;

        deviceKey1 =
                classState.classIndex.findByEntity(entity1);
        deviceKey1b =
                classState.classIndex.findByEntity(entity1b);
        deviceKey2 =
                classState.classIndex.findByEntity(entity2);
        deviceKey2b =
                classState.classIndex.findByEntity(entity2b);

        assertEquals(deviceKey1, deviceKey1b);
        
        assertEquals(deviceKey2, deviceKey2b);


    }
}
