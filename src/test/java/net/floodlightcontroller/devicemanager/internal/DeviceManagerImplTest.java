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
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.SwitchPort.ErrorStatus;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl.ClassState;
import net.floodlightcontroller.devicemanager.internal.DeviceSyncRepresentation.SyncEntity;
import net.floodlightcontroller.devicemanager.test.MockEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockEntityClassifierMac;
import net.floodlightcontroller.devicemanager.test.MockFlexEntityClassifier;
import net.floodlightcontroller.flowcache.FlowReconcileManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
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
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.test.MockSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceManagerImplTest extends FloodlightTestCase {

    protected static Logger logger =
            LoggerFactory.getLogger(DeviceManagerImplTest.class);

    protected OFPacketIn testARPReplyPacketIn_1, testARPReplyPacketIn_2;
    protected OFPacketIn testUDPPacketIn;
    protected IPacket testARPReplyPacket_1, testARPReplyPacket_2;
    protected Ethernet testUDPPacket;
    protected byte[] testARPReplyPacket_1_Srld, testARPReplyPacket_2_Srld;
    protected byte[] testUDPPacketSrld;
    private MockSyncService syncService;
    private IStoreClient<String, DeviceSyncRepresentation> storeClient;

    DeviceManagerImpl deviceManager;
    MemoryStorageSource storageSource;
    FlowReconcileManager flowReconcileMgr;

    private IOFSwitch makeSwitchMock(long id) {
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        ImmutablePort port = ImmutablePort.create("p1", (short)1);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        expect(mockSwitch.getStringId())
                .andReturn(HexString.toHexString(id, 6)).anyTimes();
        expect(mockSwitch.getPort(anyShort()))
                .andReturn(port).anyTimes();
        return mockSwitch;
    }

    /*
     * return an EasyMock ITopologyService that's setup so that it will
     * answer all questions a device or device manager will ask
     * (isAttachmentPointPort, etc.) in a way so that every port is a
     * non-BD, attachment point port.
     * The returned mock is still in record mode
     */
    private ITopologyService makeMockTopologyAllPortsAp() {
        ITopologyService mockTopology = createMock(ITopologyService.class);
        mockTopology.isAttachmentPointPort(anyLong(), anyShort());
        expectLastCall().andReturn(true).anyTimes();
        mockTopology.getL2DomainId(anyLong());
        expectLastCall().andReturn(1L).anyTimes();
        mockTopology.isBroadcastDomainPort(anyLong(), anyShort());
        expectLastCall().andReturn(false).anyTimes();
        mockTopology.isConsistent(anyLong(), anyShort(), anyLong(), anyShort());
        expectLastCall().andReturn(false).anyTimes();
        mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                             anyLong(), anyShort());
        expectLastCall().andReturn(false).anyTimes();
        return mockTopology;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        doSetUp(Role.MASTER);
    }

    public void doSetUp(Role initialRole) throws Exception {
        super.setUp();

        this.syncService = new MockSyncService();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        ITopologyService topology = createMock(ITopologyService.class);
        fmc.addService(IThreadPoolService.class, tp);
        mockFloodlightProvider = getMockFloodlightProvider();
        mockFloodlightProvider.setRole(initialRole, "");

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
        fmc.addService(ISyncService.class, syncService);
        tp.init(fmc);
        restApi.init(fmc);
        storageSource.init(fmc);
        deviceManager.init(fmc);
        flowReconcileMgr.init(fmc);
        entityClassifier.init(fmc);
        syncService.init(fmc);
        storageSource.startUp(fmc);
        deviceManager.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        tp.startUp(fmc);
        entityClassifier.startUp(fmc);
        syncService.startUp(fmc);

        this.storeClient =
                this.syncService.getStoreClient(DeviceManagerImpl.DEVICE_SYNC_STORE_NAME,
                            String.class, DeviceSyncRepresentation.class);

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

        // Another test packet with the same ARP payload as packet 1 but with
        // a different source MAC. (i.e., sender MAC and source MAC differ)
        this.testARPReplyPacket_2 = new Ethernet()
        .setSourceMACAddress("00:99:88:77:66:55")
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
        this.testARPReplyPacket_2_Srld = testARPReplyPacket_2.serialize();

        // This packet reverses the MACs and IP from testARPReplyPacket_1
        this.testUDPPacket = (Ethernet) new Ethernet()
        .setSourceMACAddress("00:11:22:33:44:55")
        .setDestinationMACAddress("00:44:33:22:11:01")
        .setEtherType(Ethernet.TYPE_IPv4)
        .setVlanID((short)5)
        .setPayload(
                    new IPv4()
                    .setTtl((byte) 128)
                    .setSourceAddress("192.168.1.2")
                    .setDestinationAddress("192.168.1.1")
                    .setPayload(new UDP()
                    .setSourcePort((short) 5000)
                    .setDestinationPort((short) 5001)
                    .setPayload(new Data(new byte[] {0x01}))));
        updateUDPPacketIn();

        // Build the PacketIn
        this.testARPReplyPacketIn_1 = ((OFPacketIn) mockFloodlightProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(this.testARPReplyPacket_1_Srld)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) this.testARPReplyPacket_1_Srld.length);

        // Build the PacketIn
        this.testARPReplyPacketIn_2 = ((OFPacketIn) mockFloodlightProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(this.testARPReplyPacket_2_Srld)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) this.testARPReplyPacket_2_Srld.length);


    }

    /**
     * Updates testUDPPacketIn and testUDPPacketSrld from testUDPPacket
     * To be called after testUDPPacket has been mangled.
     */
    private void updateUDPPacketIn() {
        this.testUDPPacketSrld = this.testUDPPacket.serialize();
        // Build the PacketIn
        this.testUDPPacketIn = ((OFPacketIn) mockFloodlightProvider.
                getOFMessageFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 3)
                .setPacketData(this.testUDPPacketSrld)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) this.testUDPPacketSrld.length);
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
                          d1.getEntityClass());
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
        assertEquals(MockEntityClassifier.testEC, d2.getEntityClass());
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
        assertEquals(MockEntityClassifier.testEC, d3.getEntityClass());
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
        assertEquals(DefaultEntityClassifier.entityClass, d4.getEntityClass());
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

    /**
     * In this test, a device is moved from attachment point (1,1) to (5,1)
     * and then moved back to (1,1) within 30 seconds.  Both the moves should
     * generate device moved notification.
     * @throws Exception
     */
    @Test
    public void testAttachmentPointMovingBack() throws Exception {
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
        expect(mockTopology.isBroadcastDomainPort(anyLong(), anyShort())).
        andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                    anyLong(), anyShort()))
                                                    .andReturn(false).anyTimes();

        expect(mockTopology.isAttachmentPointPort(anyLong(),
                                                  anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.isConsistent(1L, (short)1, 5L, (short)1)).
        andReturn(false).anyTimes();

        Date topologyUpdateTime = new Date();
        expect(mockTopology.getLastUpdateTime()).andReturn(topologyUpdateTime).
        anyTimes();

        replay(mockTopology);

        deviceManager.topology = mockTopology;

        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, null, 1L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity2 = new Entity(1L, null, null, 5L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity3 = new Entity(1L, null, null, 1L, 1, c.getTime());
        c.add(Calendar.SECOND, 1);
        Entity entity4 = new Entity(1L, null, null, 5L, 1, c.getTime());

        IDevice d;
        SwitchPort[] aps;

        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity1);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, aps);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity2);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints();

        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1) }, aps);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                new SwitchPort(5L, 1, ErrorStatus.DUPLICATE_DEVICE)},
                                              d.getAttachmentPoints(true));
        verify(mockListener);

        // Generate a packet-in again from 5,1 and ensure that it doesn't
        // create a device moved event.
        reset(mockListener);
        replay(mockListener);
        d = deviceManager.learnDeviceByEntity(entity4);
        assertEquals(1, deviceManager.getAllDevices().size());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                new SwitchPort(5L, 1, ErrorStatus.DUPLICATE_DEVICE)},
                                              d.getAttachmentPoints(true));
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
        // No attachment point should be learnt on 1L, 1
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

    private static void
            mockTopologyForPacketInTests(ITopologyService mockTopology) {
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
        expect(mockTopology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain(anyLong(),
                                                    anyShort(),
                                                    anyLong(),
                                                    anyShort()))
                .andReturn(false).anyTimes();

    }

    private Command dispatchPacketIn(long swId, OFPacketIn pi,
                                     FloodlightContext cntx) {
        IOFSwitch sw = mockFloodlightProvider.getSwitch(swId);
        Ethernet eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        return deviceManager.receive(sw, pi, cntx);
    }

    /**
     * Verify that the given device exactly matches the given fields. E.g.,
     * if ip is not null we expect the device to have exactly one IP address.
     * swId and port are the attachment point port.
     * Vlan and ip are optional all other fields must be specified.
     * @return
     */
    private static void verifyDevice(IDevice d, long mac, Short vlan, Integer ip,
                                long swId, int port) {
        assertNotNull(d);
        assertEquals(mac, d.getMACAddress());
        if (vlan == null)
            assertArrayEquals(new Short[0], d.getVlanId());
        else
            assertArrayEquals(new Short[] { vlan }, d.getVlanId());

        if (ip == null)
            assertArrayEquals(new Integer[0], d.getIPv4Addresses());
        else
            assertArrayEquals(new Integer[] { ip }, d.getIPv4Addresses());

        SwitchPort expectedAp = new SwitchPort(swId, port);
        assertArrayEquals(new SwitchPort[] { expectedAp },
                          d.getAttachmentPoints());
    }


    @Test
    public void testPacketInBasic() throws Exception {
        byte[] deviceMac =
                ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();
        OFPacketIn packetIn = testARPReplyPacketIn_1;
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");

        // Mock up our expected behavior
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        mockTopologyForPacketInTests(mockTopology);
        replay(mockTopology);

        FloodlightContext cntx = new FloodlightContext();
        Command cmd = dispatchPacketIn(1L, packetIn, cntx);
        verify(mockTopology);
        assertEquals(Command.CONTINUE, cmd);
        // Verify the device
        Device rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(deviceMac),
                        (short)5, null, null, null);
        verifyDevice(rdevice, Ethernet.toLong(deviceMac),
                     (short)5, ipaddr, 1L, 1);
        IDevice cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertEquals(rdevice, cntxSrcDev);
        IDevice cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);

        Device result = null;
        Iterator<? extends IDevice> dstiter =
                deviceManager.queryDevices(null, null, ipaddr,
                        null, null);
        if (dstiter.hasNext()) {
            result = (Device)dstiter.next();
        }
        assertFalse("There shouldn't be more than 1 device", dstiter.hasNext());
        assertEquals(rdevice, result);


        //-----------------
        // Test packetIn again with a different source port. Should be
        // the same device
        reset(mockTopology);
        mockTopologyForPacketInTests(mockTopology);
        replay(mockTopology);

        // trigger the packet in
        cntx = new FloodlightContext();
        packetIn.setInPort((short)2);
        cmd = dispatchPacketIn(5L, packetIn, cntx);
        verify(mockTopology);
        // Verify the replay matched our expectations
        assertEquals(Command.CONTINUE, cmd);

        // Verify the device
        rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(deviceMac),
                        (short)5, null, null, null);
        verifyDevice(rdevice, Ethernet.toLong(deviceMac),
                     (short)5, ipaddr, 5L, 2);
        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertEquals(rdevice, cntxSrcDev);
        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);
        // There can be only one device
        assertEquals(1, deviceManager.getAllDevices().size());

        //----------------------------
        // Test packetIn with a different packet going the reverse direction.
        // We should now get source and dest device in the context
        //==> The destination device in this step has been learned just before
        long srcMac = Ethernet.toLong(testUDPPacket.getSourceMACAddress());
        long dstMac = Ethernet.toLong(deviceMac);
        reset(mockTopology);
        mockTopologyForPacketInTests(mockTopology);
        replay(mockTopology);
        // trigger the packet in
        cntx = new FloodlightContext();
        cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        verify(mockTopology);

        assertEquals(Command.CONTINUE, cmd);
        IDevice srcDev =
                deviceManager.findDevice(srcMac, (short)5, null, null, null);
        verifyDevice(srcDev, srcMac, (short)5, null,
                     1L, testUDPPacketIn.getInPort());

        IDevice dstDev =
                deviceManager.findDevice(dstMac, (short)5, null, null, null);
        verifyDevice(dstDev, dstMac, (short)5, ipaddr, 5L, 2);

        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertEquals(srcDev, cntxSrcDev);

        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(dstDev, cntxDstDev);

        assertEquals(2, deviceManager.getAllDevices().size());
    }

    /**
     * This test ensures the device manager learns the source device
     * corresponding to the senderHardwareAddress and senderProtocolAddress
     * in an ARP response whenever the senderHardwareAddress is different
     * from the source MAC address of the Ethernet frame.
     *
     * @throws Exception
     */
    @Test
    public void testDeviceLearningFromArpResponseData() throws Exception {
        ARP arp = (ARP)((Ethernet)this.testARPReplyPacket_2).getPayload();
        long senderMac = Ethernet.toLong(arp.getSenderHardwareAddress());
        long sourceMac =
                Ethernet.toLong(((Ethernet)this.testARPReplyPacket_2)
                                .getSourceMACAddress());
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        OFPacketIn packetIn = testARPReplyPacketIn_2;

        // Mock up our expected behavior
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        mockTopologyForPacketInTests(mockTopology);
        replay(mockTopology);


        FloodlightContext cntx = new FloodlightContext();
        Command cmd = dispatchPacketIn(1L, packetIn, cntx);
        verify(mockTopology);
        assertEquals(Command.CONTINUE, cmd);
        // Verify the device for the sender HW address
        Device senderDev = (Device)
                deviceManager.findDevice(senderMac, (short)5, null, null, null);
        verifyDevice(senderDev, senderMac, (short)5, ipaddr, 1L, 1);

        Device result = null;
        Iterator<? extends IDevice> dstiter =
                deviceManager.queryDevices(null, null, ipaddr,
                        null, null);
        if (dstiter.hasNext()) {
            result = (Device)dstiter.next();
        }
        assertFalse("There shouldn't be more than 1 device", dstiter.hasNext());
        assertEquals(senderDev, result);



        // Verify the device for the source MAC
        Device srcDev = (Device)
                deviceManager.findDevice(sourceMac, (short)5, null, null, null);
        // must NOT learn IP on this device
        verifyDevice(srcDev, sourceMac, (short)5, null, 1L, 1);
        assertFalse("Device must differ", srcDev.equals(senderDev));
        // Context is annotated with this device, not the device associated
        // with ARP sender address
        IDevice cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertEquals(srcDev, cntxSrcDev);

        assertEquals(2, deviceManager.getAllDevices().size());
    }


    @Test
    public void testPacketInInvalidSrcMac() throws Exception {
        // Mock up our expected behavior
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        mockTopologyForPacketInTests(mockTopology);
        replay(mockTopology);
        FloodlightContext cntx = new FloodlightContext();

        testUDPPacket.setSourceMACAddress(Ethernet.toByteArray(0L));
        updateUDPPacketIn();
        Command cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.STOP, cmd);
        IDevice cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertNull(cntxSrcDev);
        IDevice cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);

        testUDPPacket.setSourceMACAddress(Ethernet.toByteArray(-1L));
        updateUDPPacketIn();
        cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.STOP, cmd);
        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertNull(cntxSrcDev);
        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);

        // MAC with only the multicast bit set
        testUDPPacket.setSourceMACAddress(new byte[] { 1, 0, 0, 0, 0, 0 });
        updateUDPPacketIn();
        cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.STOP, cmd);
        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertNull(cntxSrcDev);
        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);

        // Now use a real MAC. We should get a src device
        testUDPPacket.setSourceMACAddress(Ethernet.toByteArray(1L));
        updateUDPPacketIn();
        cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.CONTINUE, cmd);
        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        verifyDevice(cntxSrcDev, 1L, (short)5, null,
                     1L, testUDPPacketIn.getInPort());

        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);
        verify(mockTopology);
    }


    @Test
    public void testPacketInInvalidDstMac() throws Exception {
        // Mock up our expected behavior
        ITopologyService mockTopology = createMock(ITopologyService.class);
        deviceManager.topology = mockTopology;
        mockTopologyForPacketInTests(mockTopology);
        replay(mockTopology);
        FloodlightContext cntx = new FloodlightContext();

        long srcMac = Ethernet.toLong(testUDPPacket.getSourceMACAddress());
        long dstMac = Ethernet.toLong(testUDPPacket.getDestinationMACAddress());

        // Prime device manager with the source device
        Command cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.CONTINUE, cmd);
        IDevice cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        verifyDevice(cntxSrcDev, srcMac, (short)5, null,
                     1L, testUDPPacketIn.getInPort());
        IDevice cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);
        IDevice expectedSrcDev = cntxSrcDev;

        // Create a device for the destination. We can use testARPPacketIn_1
        // for that.
        cntx = new FloodlightContext();
        // Prime device manager with the source device
        cmd = dispatchPacketIn(1L, testARPReplyPacketIn_1, cntx);
        assertEquals(Command.CONTINUE, cmd);
        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        // yes: we check that cntxSrcDev matched dstMAC because we are
        // just adding the dest device
        int ip = IPv4.toIPv4Address("192.168.1.1");
        verifyDevice(cntxSrcDev, dstMac, (short)5, ip,
                     1L, testARPReplyPacketIn_1.getInPort());
        // yes: we set the expected dst device to the current srcDev
        IDevice expectedDstDev = cntxSrcDev;

        //-------------------------------
        // Let the real tests begin

        cntx = new FloodlightContext();
        testUDPPacket.setDestinationMACAddress(Ethernet.toByteArray(0L));
        updateUDPPacketIn();
        cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.STOP, cmd);
        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertNull(cntxDstDev);

        // use a real dest mac
        cntx = new FloodlightContext();
        testUDPPacket.setDestinationMACAddress(Ethernet.toByteArray(dstMac));
        updateUDPPacketIn();
        cmd = dispatchPacketIn(1L, testUDPPacketIn, cntx);
        assertEquals(Command.CONTINUE, cmd);
        cntxSrcDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_SRC_DEVICE);
        assertEquals(expectedSrcDev, cntxSrcDev);
        cntxDstDev = IDeviceService.fcStore.get(cntx,
                IDeviceService.CONTEXT_DST_DEVICE);
        assertEquals(expectedDstDev, cntxDstDev);

        verify(mockTopology);
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
                    // clone entities
                    Device newDevice = d;
                    for (Entity e: d.getEntities()) {
                        Entity newEntity = new Entity (e.macAddress,
                                                       e.vlan,
                                                       e.ipv4Address,
                                                       e.switchDPID,
                                                       e.switchPort,
                                                       e.lastSeenTimestamp);
                        if (e.vlan == null)
                            newEntity.vlan = (short)1;
                        else
                             newEntity.vlan = (short)((e.vlan + 1 % 4095)+1);
                        newDevice = new Device(newDevice, newEntity, -1);
                    }
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

        Device d1 = deviceManager.learnDeviceByEntity(entity1);
        deviceManager.learnDeviceByEntity(entity2);
        Device d3 = deviceManager.learnDeviceByEntity(entity3);
        Device d4 = deviceManager.learnDeviceByEntity(entity4);

        IDevice d;

        Iterator<? extends IDevice> iter =
                deviceManager.queryDevices(null, (short)1, 1, null, null);
        int count = 0;
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            assertEquals(d1.getDeviceKey(), d.getDeviceKey());
        }
        assertEquals(1, count);

        iter = deviceManager.queryDevices(null, (short)3, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            assertEquals(d3.getDeviceKey(), d.getDeviceKey());
        }
        assertEquals(1, count);

        iter = deviceManager.queryDevices(null, (short)1, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(0, count);

        Device d5 = deviceManager.learnDeviceByEntity(entity5);
        iter = deviceManager.queryDevices(null, (short)4, 3, null, null);
        count = 0;
        Set<Long> deviceKeysFromIterator = new HashSet<Long>();
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            deviceKeysFromIterator.add(d.getDeviceKey());
        }
        Set<Long> expectedDeviceKeys = new HashSet<Long>();
        expectedDeviceKeys.add(d4.getDeviceKey());
        expectedDeviceKeys.add(d5.getDeviceKey());
        assertEquals(expectedDeviceKeys, deviceKeysFromIterator);
        assertEquals(2, count);


        iter = deviceManager.queryDevices(1L, null, null, null, null);
        count = 0;
        deviceKeysFromIterator = new HashSet<Long>();
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            deviceKeysFromIterator.add(d.getDeviceKey());
        }
        expectedDeviceKeys = new HashSet<Long>();
        expectedDeviceKeys.add(d1.getDeviceKey());
        expectedDeviceKeys.add(d5.getDeviceKey());
        assertEquals(expectedDeviceKeys, deviceKeysFromIterator);
        assertEquals(2, count);
    }

    @Test
    public void testDeviceIndex() throws Exception {
        EnumSet<IDeviceService.DeviceField> indexFields =
                EnumSet.noneOf(IDeviceService.DeviceField.class);
        indexFields.add(IDeviceService.DeviceField.IPV4);
        indexFields.add(IDeviceService.DeviceField.VLAN);
        deviceManager.addIndex(false, indexFields);

        indexFields = EnumSet.noneOf(IDeviceService.DeviceField.class);
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

        IDevice d1 = deviceManager.learnDeviceByEntity(entity1);
        IDevice d2 = deviceManager.learnDeviceByEntity(entity2);
        IDevice d3 = deviceManager.learnDeviceByEntity(entity3);
        IDevice d4 = deviceManager.learnDeviceByEntity(entity4);
        assertEquals(d1.getEntityClass(), d2.getEntityClass());
        assertEquals(d1.getEntityClass(), d3.getEntityClass());
        assertEquals(d1.getEntityClass(), d4.getEntityClass());

        IDevice d;

        Iterator<? extends IDevice> iter =
                deviceManager.queryClassDevices(d1.getEntityClass(), null,
                                                (short)1, 1, null, null);
        int count = 0;
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            assertEquals(d1.getDeviceKey(), d.getDeviceKey());
        }
        assertEquals(1, count);

        iter = deviceManager.queryClassDevices(d1.getEntityClass(), null,
                                               (short)3, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            assertEquals(d3.getDeviceKey(), d.getDeviceKey());

        }
        assertEquals(1, count);

        iter = deviceManager.queryClassDevices(d1.getEntityClass(), null,
                                               (short)1, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(0, count);

        IDevice d5 = deviceManager.learnDeviceByEntity(entity5);
        assertEquals(d1.getEntityClass(), d5.getEntityClass());
        iter = deviceManager.queryClassDevices(d1.getEntityClass(), null,
                                               (short)4, 3, null, null);
        count = 0;
        Set<Long> deviceKeysFromIterator = new HashSet<Long>();
        while (iter.hasNext()) {
            count += 1;
            d = iter.next();
            deviceKeysFromIterator.add(d.getDeviceKey());
        }
        Set<Long> expectedDeviceKeys = new HashSet<Long>();
        expectedDeviceKeys.add(d4.getDeviceKey());
        expectedDeviceKeys.add(d5.getDeviceKey());
        assertEquals(expectedDeviceKeys, deviceKeysFromIterator);
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
    public void testFindDevice() throws FloodlightModuleException {
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
    public void testReclassifyDevice() throws FloodlightModuleException {
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

    @Test
    public void testSyncEntity() {
        Date d1 = new Date();
        Date d2 = new Date(0);
        Entity e1 = new Entity(1L, (short)2, 3, 4L, 5, d1);
        e1.setActiveSince(d2);
        SyncEntity se1 = new SyncEntity(e1);
        assertEntityEquals(e1, se1);
        assertEquals(1L, se1.macAddress);
        assertEquals(Short.valueOf((short)2), se1.vlan);
        assertEquals(Integer.valueOf(3), se1.ipv4Address);
        assertEquals(Long.valueOf(4L), se1.switchDPID);
        assertEquals(Integer.valueOf(5), se1.switchPort);
        assertEquals(d1, se1.lastSeenTimestamp);
        assertEquals(d2, se1.activeSince);
        assertNotSame(d1, se1.lastSeenTimestamp);
        assertNotSame(d2, se1.activeSince);

        Entity e2 = new Entity(42L, null, null, null, null, null);
        SyncEntity se2 = new SyncEntity(e2);
        assertEntityEquals(e2, se2);

        SyncEntity se3 = new SyncEntity();
        SyncEntity se4 = new SyncEntity();
        se3.lastSeenTimestamp = new Date(1000);
        se4.lastSeenTimestamp = new Date(2000);
        assertTrue("", se3.compareTo(se4) < 0);
        assertTrue("", se4.compareTo(se3) > 0);
        se4.lastSeenTimestamp = new Date(1000);
        assertTrue("", se3.compareTo(se4) == 0);
        assertTrue("", se4.compareTo(se3) == 0);
        se4.lastSeenTimestamp = new Date(500);
        assertTrue("", se3.compareTo(se4) > 0);
        assertTrue("", se4.compareTo(se3) < 0);
    }

    /* Test basic DeviceSyncRepresentation behavior */
    @Test
    public void testDeviceSyncRepresentationBasics() {
        DeviceSyncRepresentation dsr = new DeviceSyncRepresentation();
        assertNull(dsr.getKey());
        assertNull(dsr.getEntities());
        dsr.setKey("MyKey");
        assertEquals("MyKey", dsr.getKey());
        assertEquals("MyKey", dsr.toString());

        List<SyncEntity> entities = new ArrayList<SyncEntity>();
        Entity e1a = new Entity(1L, (short)2, 3, 4L, 5, new Date(1000));
        Entity e1b = new Entity(1L, (short)2, null, 4L, 5, new Date(0));
        entities.add(new SyncEntity(e1a));
        entities.add(new SyncEntity(e1b));
        // e1b comes before e1 (lastSeen) but we add it after it to test
        // sorting
        dsr.setEntities(entities);

        assertEquals(2, dsr.getEntities().size());
        // e1b has earlier time
        assertEquals(e1b, dsr.getEntities().get(0).asEntity());
        assertEquals(e1a, dsr.getEntities().get(1).asEntity());

        dsr.setKey(null);
        dsr.setEntities(null);
        assertNull(dsr.getKey());
        assertNull(dsr.getEntities());
    }

    @Test
    public void testDeviceSyncRepresentationFromDevice() {
        ITopologyService mockTopology = makeMockTopologyAllPortsAp();
        replay(mockTopology);
        deviceManager.topology = mockTopology;

        deviceManager.entityClassifier = new MockEntityClassifier();

        //**************************************
        // Test 1: a single entity
        Entity e1 = new Entity(1L, (short)2, 3, 4L, 5, new Date(1000));
        Device d1 = deviceManager.learnDeviceByEntity(e1);
        assertEquals("Sanity check failed. Device doesn't have the expected " +
                     "entity class. Something with the test setup is strange",
                     "DefaultEntityClass", d1.getEntityClass().getName());
        assertEquals("Sanity check failed. Device doesn't have the expected " +
                     "entity class. Something with the test setup is strange",
                     EnumSet.of(DeviceField.MAC, DeviceField.VLAN),
                     d1.getEntityClass().getKeyFields());

        Long deviceKey = d1.getDeviceKey();
        DeviceSyncRepresentation dsr1 = new DeviceSyncRepresentation(d1);
        assertEquals("DefaultEntityClass::00:00:00:00:00:01::[2]::",
                     dsr1.getKey());
        assertEquals(1, dsr1.getEntities().size());
        assertEquals(e1, dsr1.getEntities().get(0).asEntity());

        //**************************************
        // Test 1b: same device, now with a second entity (no IP).
        // this second entity has a lastSeen time that is earlier than the
        // first entity
        Entity e1b = new Entity(1L, (short)2, null, 4L, 5, new Date(0));
        d1 = deviceManager.learnDeviceByEntity(e1b);
        assertEquals("Sanity check failed. Should still be same device but " +
                     "deviceKeys differs", deviceKey, d1.getDeviceKey());
        dsr1 = new DeviceSyncRepresentation(d1);
        assertEquals("DefaultEntityClass::00:00:00:00:00:01::[2]::",
                     dsr1.getKey());
        assertEquals(2, dsr1.getEntities().size());
        // Entities are ordered by their lastSeen time. e1b should come
        // before e1.
        assertEquals(e1, dsr1.getEntities().get(1).asEntity());
        assertEquals(e1b, dsr1.getEntities().get(0).asEntity());

        //**************************************
        // Test 1c: same device with a third entity that does not have a
        // switch port. It should be added to the DeviceSyncRepresentation
        Entity e1c = new Entity(1L, (short)2, 33, null, null, new Date(2000));
        d1 = deviceManager.learnDeviceByEntity(e1c);
        assertEquals("Sanity check failed. Should still be same device but " +
                     "deviceKeys differs", deviceKey, d1.getDeviceKey());
        dsr1 = new DeviceSyncRepresentation(d1);
        assertEquals("DefaultEntityClass::00:00:00:00:00:01::[2]::",
                     dsr1.getKey());
        assertEquals(3, dsr1.getEntities().size());
        // Entities are ordered by their lastSeen time
        assertEquals(e1c, dsr1.getEntities().get(2).asEntity());
        assertEquals(e1, dsr1.getEntities().get(1).asEntity());
        assertEquals(e1b, dsr1.getEntities().get(0).asEntity());

        //**************************************
        // Test 1d: same device with a fourth entity that has a different
        // attachment point and that is newer. Device should move and
        // non-attachment point entities should be removed (e1b). Although
        // e1 is non-attachment point it will remain because it has an IP
        Entity e1d = new Entity(1L, (short)2, 33, 4L, 6, new Date(3000));
        d1 = deviceManager.learnDeviceByEntity(e1d);
        assertEquals("Sanity check failed. Should still be same device but " +
                     "deviceKeys differs", deviceKey, d1.getDeviceKey());
        dsr1 = new DeviceSyncRepresentation(d1);
        assertEquals("DefaultEntityClass::00:00:00:00:00:01::[2]::",
                     dsr1.getKey());
        assertEquals(3, dsr1.getEntities().size());
        assertEquals(e1, dsr1.getEntities().get(0).asEntity());
        assertEquals(e1c, dsr1.getEntities().get(1).asEntity());
        assertEquals(e1d, dsr1.getEntities().get(2).asEntity());

        d1 = null;


        //**************************************
        // Test 2: a second device with a different entity class. The
        // mock entity classifier will return an entity class where all
        // fields are keys if the DPID is > 10L
        Entity e2 = new Entity(2L, (short)23, 24, 11L, 1, new Date(0));
        Device d2 = deviceManager.learnDeviceByEntity(e2);
        DeviceSyncRepresentation dsr2 = new DeviceSyncRepresentation(d2);
        assertEquals("Sanity check failed. Device doesn't have the expected " +
                     "entity class. Something with the test setup is strange",
                     "TestEntityClass", d2.getEntityClass().getName());
        assertEquals("Sanity check failed. Device doesn't have the expected " +
                     "entity class. Something with the test setup is strange",
                     EnumSet.of(DeviceField.MAC, DeviceField.VLAN,
                                DeviceField.SWITCH, DeviceField.PORT),
                     d2.getEntityClass().getKeyFields());
        SwitchPort swp = new SwitchPort(11L, 1, null);
        assertEquals("TestEntityClass::00:00:00:00:00:02::[23]::[" +
                     swp.toString() + "]::",
                     dsr2.getKey());
    }

    /* interate through all entries in the sync store and return them as
     * list. We don't return the key from the store however, we assert
     * that the key from the store matches the key in the representation.
     * If we have a null value (tombstone) we simply add the null value to
     * the list to return.
     */
    private List<DeviceSyncRepresentation> getEntriesFromStore()
            throws Exception {
        List<DeviceSyncRepresentation> entries =
                new ArrayList<DeviceSyncRepresentation>();
        IClosableIterator<Entry<String, Versioned<DeviceSyncRepresentation>>> iter =
                storeClient.entries();
        try {
            while(iter.hasNext()) {
                Entry<String, Versioned<DeviceSyncRepresentation>> entry =
                        iter.next();
                DeviceSyncRepresentation dsr = entry.getValue().getValue();
                if (dsr != null)
                    assertEquals(entry.getKey(), dsr.getKey());
                entries.add(dsr);
            }
        } finally {
            if (iter != null)
                iter.close();
        }
        return entries;
    }

    /*
     * assert whether the given Entity expected is equals to the given
     * SyncEntity actual. This method also compares the times (lastSeen,
     * activeSince). Entity.equals will not do that!
     */
    private static void assertEntityEquals(Entity expected, SyncEntity actual) {
        assertNotNull(actual);
        assertNotNull(expected);
        Entity actualEntity = actual.asEntity();
        assertEquals("entityFields", expected, actualEntity);
        assertEquals("lastSeenTimestamp",
                     expected.getLastSeenTimestamp(),
                     actualEntity.getLastSeenTimestamp());
        assertEquals("activeSince",
                     expected.getActiveSince(), actualEntity.getActiveSince());
    }

    /* This test tests the normal operation as master when we write to the sync
     * store or delete from the store.
     */
    @Test
    public void testWriteToSyncStore() throws Exception {
        int syncStoreIntervalMs = 50;
        ITopologyService mockTopology = makeMockTopologyAllPortsAp();
        replay(mockTopology);
        deviceManager.topology = mockTopology;
        deviceManager.setSyncStoreWriteInterval(syncStoreIntervalMs);

        Entity e1a = new Entity(1L, (short)2, 3, 4L, 5, new Date(1000));
        e1a.setActiveSince(new Date(0));
        deviceManager.learnDeviceByEntity(e1a);

        //storeClient.put("FooBar", new DeviceSyncRepresentation());

        List<DeviceSyncRepresentation> entries = getEntriesFromStore();
        assertEquals(1, entries.size());
        DeviceSyncRepresentation dsr1 = entries.get(0);
        assertEquals(1, dsr1.getEntities().size());
        assertEntityEquals(e1a, dsr1.getEntities().get(0));

        // Same entity but newer timestamp. Since the device hasn't changed,
        // only the timestamp is updated and the write should be throttled.
        Entity e1b = new Entity(1L, (short)2, 3, 4L, 5, new Date(2000));
        e1b.setActiveSince(new Date(0));
        deviceManager.learnDeviceByEntity(e1a);
        entries = getEntriesFromStore();
        assertEquals(1, entries.size());
        dsr1 = entries.get(0);
        assertEquals(1, dsr1.getEntities().size());
        assertEntityEquals(e1a, dsr1.getEntities().get(0)); //e1a not e1b !!!

        // Wait for the write interval to expire then write again.
        Thread.sleep(syncStoreIntervalMs+5);
        Entity e1c = new Entity(1L, (short)2, 3, 4L, 5, new Date(3000));
        e1c.setActiveSince(new Date(0));
        deviceManager.learnDeviceByEntity(e1c);
        entries = getEntriesFromStore();
        assertEquals(1, entries.size());
        dsr1 = entries.get(0);
        assertEquals(1, dsr1.getEntities().size());
        assertEntityEquals(e1c, dsr1.getEntities().get(0)); // e1c !!

        // Entity for same device but with different IP. should be added
        // immediately
        Entity e1d = new Entity(1L, (short)2, 33, 4L, 5, new Date(4000));
        e1d.setActiveSince(new Date(0));
        deviceManager.learnDeviceByEntity(e1d);
        entries = getEntriesFromStore();
        assertEquals(1, entries.size());
        dsr1 = entries.get(0);
        assertEquals(2, dsr1.getEntities().size());
        assertEntityEquals(e1c, dsr1.getEntities().get(0)); // e1c !!
        assertEntityEquals(e1d, dsr1.getEntities().get(1)); // e1d !!

        // Entity for same device with new switch port ==> moved ==> write
        // update immediately without throttle.
        // Note: the previous entities will still be there because they have
        // IPs (even though they aren't for the current attachment point)
        Entity e1e = new Entity(1L, (short)2, 33, 4L, 6, new Date(5000));
        e1e.setActiveSince(new Date(0));
        deviceManager.learnDeviceByEntity(e1e);
        entries = getEntriesFromStore();
        assertEquals(1, entries.size());
        dsr1 = entries.get(0);
        assertEquals(3, dsr1.getEntities().size());
        assertEntityEquals(e1c, dsr1.getEntities().get(0));
        assertEntityEquals(e1d, dsr1.getEntities().get(1));
        assertEntityEquals(e1e, dsr1.getEntities().get(2));

        // Add a second device
        Entity e2 = new Entity(2L, null, null, 5L, 5, new Date());
        deviceManager.learnDeviceByEntity(e2);
        entries = getEntriesFromStore();
        assertEquals(2, entries.size());
        for (DeviceSyncRepresentation dsr: entries) {
            // This is a kinda ugly way to ensure we have the two
            // devices we need..... but it will work for now
            if (dsr.getKey().contains("::00:00:00:00:00:01::")) {
                assertEquals(3, dsr.getEntities().size());
                assertEntityEquals(e1c, dsr.getEntities().get(0));
                assertEntityEquals(e1d, dsr.getEntities().get(1));
                assertEntityEquals(e1e, dsr.getEntities().get(2));
            } else if (dsr.getKey().contains("::00:00:00:00:00:02::")) {
                assertEquals(1, dsr.getEntities().size());
                assertEntityEquals(e2, dsr.getEntities().get(0));
            } else {
                fail("Unknown entry in store: " + dsr);
            }
        }


        // Run entity cleanup. Since we've used phony time stamps for
        // device 1 its entities should be cleared and the device should be
        // removed from the store. Device 2 should remain in the store.
        deviceManager.cleanupEntities();
        entries = getEntriesFromStore();
        assertEquals(2, entries.size());
        for (DeviceSyncRepresentation dsr: entries) {
            if (dsr == null) {
                // pass
            } else if (dsr.getKey().contains("::00:00:00:00:00:02::")) {
                assertEquals(1, dsr.getEntities().size());
                assertEntityEquals(e2, dsr.getEntities().get(0));
            } else {
                fail("Unknown entry in store: " + dsr);
            }
        }
    }


    private void assertDeviceIps(Integer[] expected, IDevice d) {
        List<Integer> expectedList = Arrays.asList(expected);
        Collections.sort(expectedList);
        List<Integer> actualList = Arrays.asList(d.getIPv4Addresses());
        Collections.sort(actualList);
        assertEquals(expectedList, actualList);
    }

    private IDevice getSingleDeviceFromDeviceManager(long mac) {
        Iterator<? extends IDevice> diter =
                deviceManager.queryDevices(mac, null, null, null, null);
        assertTrue("Query didn't return a device", diter.hasNext());
        IDevice d = diter.next();
        assertFalse("Query returned more than one device", diter.hasNext());
        return d;
    }

    @Test
    public void testToMaster() throws Exception {
        int syncStoreWriteIntervalMs = 0;
        int initialSyncStoreConsolidateIntervalMs = 50;
        ITopologyService mockTopology = makeMockTopologyAllPortsAp();
        replay(mockTopology);
        deviceManager.topology = mockTopology;
        // We want an EntityClassifier that has switch/port as key fields
        deviceManager.entityClassifier = new MockEntityClassifier();
        deviceManager.setSyncStoreWriteInterval(syncStoreWriteIntervalMs);
        deviceManager.setInitialSyncStoreConsolidateMs(initialSyncStoreConsolidateIntervalMs);

        // Add Device1 with two entities with two different IPs
        Entity e1a = new Entity(1L, null, 3, 4L, 5, new Date(1000));
        Entity e1b = new Entity(1L, null, 33,  4L, 5, new Date(2000));
        Device d1 = deviceManager.allocateDevice(1L, e1a,
                                                 DefaultEntityClassifier.entityClass);
        d1 = deviceManager.allocateDevice(d1, e1b, -1);
        DeviceSyncRepresentation dsr = new DeviceSyncRepresentation(d1);
        storeClient.put(dsr.getKey(), dsr);

        // Add Device2 with different switch-ports. Only the most recent
        // one should be the attachment point
        Entity e2a = new Entity(2L, null, null, 4L, 4, new Date(1000));
        Entity e2b = new Entity(2L, null, null, 4L, 5, new Date(2000));
        Device d2 = deviceManager.allocateDevice(2L, e2a,
                                                 DefaultEntityClassifier.entityClass);
        d2 = deviceManager.allocateDevice(d2, e2b, -1);
        d2.updateAttachmentPoint(4L, (short)5,
                                 e2b.getLastSeenTimestamp().getTime());
        SwitchPort swp = new SwitchPort(4L, 5);
        SwitchPort[] aps = d2.getAttachmentPoints();
        // sanity check
        assertArrayEquals("Sanity check: should only have AP(4L,5)",
                          new SwitchPort[] {swp}, aps);
        dsr = new DeviceSyncRepresentation(d2);
        storeClient.put(dsr.getKey(), dsr);

        // Add a tombstone entry to the store to make sure we don't trip a
        // NPE
        dsr = null;
        Versioned<DeviceSyncRepresentation> versionedDsr =
                storeClient.get("FooBar");
        storeClient.put("FooBar", versionedDsr);

        deviceManager.getHAListener().transitionToMaster();

        // Query for the Device1. Make sure we have the two IPs we stored.
        IDevice d = getSingleDeviceFromDeviceManager(1L);
        assertDeviceIps(new Integer[] {3, 33}, d);
        assertArrayEquals(new Short[] { Ethernet.VLAN_UNTAGGED }, d.getVlanId());
        swp = new SwitchPort(4L, 5);
        assertArrayEquals(new SwitchPort[] { swp }, d.getAttachmentPoints());

        // Query for Device2. Make sure we only have the more recent AP
        // Query for the Device1. Make sure we have the two IPs we stored.
        d = getSingleDeviceFromDeviceManager(2L);
        assertArrayEquals(new Integer[0], d.getIPv4Addresses());
        assertArrayEquals(new Short[] { Ethernet.VLAN_UNTAGGED }, d.getVlanId());
        swp = new SwitchPort(4L, 5);
        assertArrayEquals(new SwitchPort[] { swp }, d.getAttachmentPoints());

        //----------------------------
        // add another entry device to the store. since device manager is
        // already master we won't read this device and it should be
        // removed from the store by the consolidate task
        Entity e3 = new Entity(3L, null, null, 1L, 1, null);
        dsr = new DeviceSyncRepresentation();
        dsr.setKey("Device3");
        dsr.setEntities(Collections.singletonList(new SyncEntity(e3)));
        storeClient.put(dsr.getKey(), dsr);

        // make sure it's in the store
        List<DeviceSyncRepresentation> entries = getEntriesFromStore();
        boolean found = false;
        for (DeviceSyncRepresentation entry: entries) {
            if (entry!=null && entry.getKey().equals("Device3"))
                found = true;
        }
        assertTrue("Device3 not in store. Entries in store: " + entries, found);
        // make sure it's not in DevManager
        Iterator<? extends IDevice> diter =
                deviceManager.queryDevices(3L, null, null, null, null);
        assertFalse("Device3 found in DeviceManager. Should be there",
                    diter.hasNext());

        // Wait for consolidate
        Thread.sleep(initialSyncStoreConsolidateIntervalMs + 5);
        // make sure it's in NOT the store
        entries = getEntriesFromStore();
        found = false;
        for (DeviceSyncRepresentation entry: entries) {
            if (entry!=null && entry.getKey().equals("Device3"))
                found = true;
        }
        assertFalse("Device3 not is still in the store. Entries in store: "
                    + entries, found);
        // make sure it's not in DevManager
        diter = deviceManager.queryDevices(3L, null, null, null, null);
        assertFalse("Device3 found in DeviceManager. Should be there",
                    diter.hasNext());
    }


    @Test
    public void testConsolitateStore() throws Exception {
        int syncStoreInternalMs = 0;
        ITopologyService mockTopology = makeMockTopologyAllPortsAp();
        replay(mockTopology);
        deviceManager.topology = mockTopology;
        // We want an EntityClassifier that has switch/port as key fields
        deviceManager.entityClassifier = new MockEntityClassifier();
        deviceManager.setSyncStoreWriteInterval(syncStoreInternalMs);

        // Add Device1 with two entities to store and let device manager
        // learn
        Entity e1a = new Entity(1L, null, null, 4L, 5, new Date(1000));
        Entity e1b = new Entity(1L, null, 3,  4L, 5, new Date(2000));
        Device d1 = deviceManager.learnDeviceByEntity(e1a);
        deviceManager.learnDeviceByEntity(e1b);
        String dev1Key = DeviceSyncRepresentation.computeKey(d1);


        // Add a second device to the store but do NOT add to device manager
        Entity e2 = new Entity(2L, null, null, 5L, 5, new Date());
        Device d2 = deviceManager.allocateDevice(42L, e2,
                                                 DefaultEntityClassifier.entityClass);
        DeviceSyncRepresentation dsr = new DeviceSyncRepresentation(d2);
        storeClient.put(dsr.getKey(), dsr);
        String dev2Key = DeviceSyncRepresentation.computeKey(d2);

        // Make sure we have two devices in the store
        List<DeviceSyncRepresentation> entries = getEntriesFromStore();
        assertEquals(2, entries.size());

        deviceManager.scheduleConsolidateStoreNow();
        Thread.sleep(25); // give the scheduler time to run the task

        // We should still have two entries, however one of them will be a
        // tombstone
        entries = getEntriesFromStore();
        assertEquals(2, entries.size());

        // Device 1 should still be in store
        Versioned<DeviceSyncRepresentation> versioned =
                storeClient.get(dev1Key);
        dsr = versioned.getValue();
        assertNotNull(dsr);
        assertEquals(2, dsr.getEntities().size());
        assertEntityEquals(e1a, dsr.getEntities().get(0));
        assertEntityEquals(e1b, dsr.getEntities().get(1));

        // Device2 should be gone
        versioned = storeClient.get(dev2Key);
        assertNull(versioned.getValue());

        // Run consolitate again. This time we check that tombstones in
        // the store are handled correctly
        deviceManager.scheduleConsolidateStoreNow();
        Thread.sleep(25); // give the scheduler time to run the task

        // Now write a device to the store that doesn't have any switch-port
        // it should be removed
        Entity e3 = new Entity(3L, null, null, null, null, null);
        dsr.setKey("Device3");
        dsr.setEntities(Collections.singletonList(new SyncEntity(e3)));
        storeClient.put(dsr.getKey(), dsr);

        // Run consolitate again. This time we check that tombstones in
        // the store are handled correctly
        deviceManager.scheduleConsolidateStoreNow();
        Thread.sleep(25); // give the scheduler time to run the task
        versioned = storeClient.get("Device3");
        assertNull(versioned.getValue());

    }

}
