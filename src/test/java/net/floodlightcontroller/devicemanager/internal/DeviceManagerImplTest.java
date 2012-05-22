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

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyShort;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import static net.floodlightcontroller.devicemanager.IDeviceService.DeviceField.*;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.IDeviceService;
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
import static net.floodlightcontroller.devicemanager.SwitchPort.ErrorStatus.*;
import static org.junit.Assert.*;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.util.HexString;

public class DeviceManagerImplTest extends FloodlightTestCase {
    protected OFPacketIn packetIn_1, packetIn_2, packetIn_3;
    protected IPacket testARPReplyPacket_1, testARPReplyPacket_2, 
        testARPReplyPacket_3;
    protected IPacket testARPReqPacket_1, testARPReqPacket_2;
    protected byte[] testARPReplyPacket_1_Srld, testARPReplyPacket_2_Srld;
    private byte[] testARPReplyPacket_3_Serialized;
    MockFloodlightProvider mockFloodlightProvider;
    DeviceManagerImpl deviceManager;
    MemoryStorageSource storageSource;
    
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
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);
        mockFloodlightProvider = getMockFloodlightProvider();
        deviceManager = new DeviceManagerImpl();
        fmc.addService(IDeviceService.class, deviceManager);
        storageSource = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IRestApiService.class, restApi);
        tp.init(fmc);
        restApi.init(fmc);
        storageSource.init(fmc);
        deviceManager.init(fmc);
        storageSource.startUp(fmc);
        deviceManager.startUp(fmc);
        tp.startUp(fmc);
        
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
    
    static EnumSet<DeviceField> testKeyFields;
    static {
        testKeyFields = EnumSet.of(MAC, VLAN, SWITCH, PORT);
    }
    
    public static class TestEntityClass implements IEntityClass {
        @Override
        public EnumSet<DeviceField> getKeyFields() {
            return testKeyFields;
        }
    }

    protected static IEntityClass testEC = new TestEntityClass();
    
    public static class TestEntityClassifier extends DefaultEntityClassifier {
        
        @Override
        public Collection<IEntityClass> classifyEntity(Entity entity) {
            if (entity.switchDPID >= 10L) {
                return Arrays.asList(testEC);
            }
            return DefaultEntityClassifier.entityClasses;
        }

        @Override
        public EnumSet<IDeviceService.DeviceField> getKeyFields() {
            return testKeyFields;
        }
        
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
                createStrictMock(IDeviceListener.class);
        
        deviceManager.addListener(mockListener);
        deviceManager.setEntityClassifier(new TestEntityClassifier());
        deviceManager.startUp(null);
        
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.getL2DomainId(anyLong())).
            andReturn(1L).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(), anyShort())).
        andReturn(false).anyTimes();

        expect(mockTopology.isAttachmentPointPort(anyLong(), 
                                       anyShort())).andReturn(true).anyTimes();
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
        assertArrayEquals(new IEntityClass[]{ DefaultEntityClassifier.entityClass }, 
                          d1.entityClasses);
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
        assertArrayEquals(new IEntityClass[]{ testEC }, 
                          d2.entityClasses);
        assertArrayEquals(new Short[] { -1 }, d2.getVlanId());
        assertArrayEquals(new Integer[] { }, d2.getIPv4Addresses());

        assertEquals(2, deviceManager.getAllDevices().size());
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        replay(mockListener);
        
        Device d3 = deviceManager.learnDeviceByEntity(entity3);
        assertNotSame(d2, d3);
        assertArrayEquals(new IEntityClass[]{ testEC }, 
                          d3.entityClasses);
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
        assertArrayEquals(new IEntityClass[]{ DefaultEntityClassifier.entityClass },
                          d4.entityClasses);
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
        assertArrayEquals(new SwitchPort[] { new SwitchPort(50L, 3) },
                          d7.getAttachmentPoints());
        assertArrayEquals(new Short[] { (short) 4 },
                          d7.getVlanId());

        assertEquals(4, deviceManager.getAllDevices().size());
        verify(mockListener);
    }
    
    @Test
    public void testAttachmentPointLearning() throws Exception {
        IDeviceListener mockListener = 
                createStrictMock(IDeviceListener.class);
        
        deviceManager.addListener(mockListener);

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
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1),
                                             new SwitchPort(10L, 1) }, aps);
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
        
        replay(mockTopology);
        
        deviceManager.topology = mockTopology;
        
        Calendar c = Calendar.getInstance();
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        c.add(Calendar.MILLISECOND, 
              (int)DeviceManagerImpl.NBD_TO_BD_TIMEDIFF_MS / 2);
        Entity entity2 = new Entity(1L, null, null, 1L, 2, c.getTime());
        c.add(Calendar.MILLISECOND, 
              (int)DeviceManagerImpl.NBD_TO_BD_TIMEDIFF_MS / 2 + 1);
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

    @Test
    public void testPacketIn() throws Exception {
        byte[] dataLayerSource = 
                ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

        // Mock up our expected behavior
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isAttachmentPointPort(anyLong(), 
                                       anyShort())).andReturn(true).anyTimes();
        deviceManager.topology = mockTopology;

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
                           DefaultEntityClassifier.entityClasses);

        expect(mockTopology.isAllowed(EasyMock.anyLong(), 
                                      EasyMock.anyShort())).
                                      andReturn(true).anyTimes();
        // Start recording the replay on the mocks
        replay(mockTopology);
        // Get the listener and trigger the packet in
        IOFSwitch switch1 = mockFloodlightProvider.getSwitches().get(1L);
        mockFloodlightProvider.dispatchMessage(switch1, this.packetIn_1);

        // Verify the replay matched our expectations
        verify(mockTopology);

        // Verify the device
        Device rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(dataLayerSource), 
                                         (short)5, null, null, null);

        assertEquals(device, rdevice);
        assertEquals(new Short((short)5), rdevice.getVlanId()[0]);
        
        Device result = null;
        Iterator<? extends IDevice> dstiter = 
                deviceManager.queryClassDevices(device, null, null, ipaddr, 
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
                           DefaultEntityClassifier.entityClasses);

        reset(mockTopology);
        expect(mockTopology.isAttachmentPointPort(anyLong(), 
                                       anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).andReturn(1L).anyTimes();
        
        // Start recording the replay on the mocks
        replay(mockTopology);
        // Get the listener and trigger the packet in
        IOFSwitch switch5 = mockFloodlightProvider.getSwitches().get(5L);
        mockFloodlightProvider.
            dispatchMessage(switch5, 
                            this.packetIn_1.setInPort((short)2));

        // Verify the replay matched our expectations
        verify(mockTopology);

        // Verify the device
        rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(dataLayerSource), 
                                         (short)5, null, null, null);
        assertEquals(device, rdevice);
    }

    @Test
    public void testEntityExpiration() throws Exception {
        IDeviceListener mockListener = 
                createStrictMock(IDeviceListener.class);
        mockListener.deviceIPV4AddrChanged(isA(IDevice.class));
        mockListener.deviceMoved(isA(IDevice.class));
        
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isAttachmentPointPort(anyLong(), 
                                       anyShort())).andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(anyLong(), 
                                                  anyShort())).
                                       andReturn(false).anyTimes();
        expect(mockTopology.getL2DomainId(1L)).andReturn(1L).anyTimes();
        expect(mockTopology.getL2DomainId(5L)).andReturn(5L).anyTimes();
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
                                             new SwitchPort(5L, 1) }, 
                          d.getAttachmentPoints());
        Iterator<? extends IDevice> diter = 
                deviceManager.queryClassDevices(d, null, null, 1, null, null);
        assertTrue(diter.hasNext());
        assertEquals(d.getDeviceKey(), diter.next().getDeviceKey());
        diter = deviceManager.queryClassDevices(d, null, null, 2, null, null);
        assertTrue(diter.hasNext());
        assertEquals(d.getDeviceKey(), diter.next().getDeviceKey());
        
        deviceManager.addListener(mockListener);
        replay(mockListener);
        deviceManager.entityCleanupTask.reschedule(0, null);
        
        d = deviceManager.getDevice(d.getDeviceKey());
        assertArrayEquals(new Integer[] { 2 }, d.getIPv4Addresses());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, 
                          d.getAttachmentPoints());
        diter = deviceManager.queryClassDevices(d, null, null, 2, null, null);
        assertTrue(diter.hasNext());
        assertEquals(d.getDeviceKey(), diter.next().getDeviceKey());
        diter = deviceManager.queryClassDevices(d, null, null, 1, null, null);
        assertFalse(diter.hasNext());
        
        d = deviceManager.findDevice(1L, null, null, null, null);
        assertArrayEquals(new Integer[] { 2 }, d.getIPv4Addresses());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, 
                          d.getAttachmentPoints());
        
        verify(mockListener);
    }

    @Test
    public void testDeviceExpiration() throws Exception {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MILLISECOND, -DeviceManagerImpl.ENTITY_TIMEOUT-1);
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, c.getTime());
        Entity entity2 = new Entity(1L, null, 2, 5L, 1, c.getTime());

        IDevice d = deviceManager.learnDeviceByEntity(entity2);
        d = deviceManager.learnDeviceByEntity(entity1);
        assertArrayEquals(new Integer[] { 1, 2 }, d.getIPv4Addresses());
        
        deviceManager.entityCleanupTask.reschedule(0, null);

        IDevice r = deviceManager.getDevice(d.getDeviceKey());
        assertNull(r);
        Iterator<? extends IDevice> diter = 
                deviceManager.queryClassDevices(d, null, null, 1, null, null);
        assertFalse(diter.hasNext());
        
        r = deviceManager.findDevice(1L, null, null, null, null);
        assertNull(r);
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
        replay(mockTopology);
        deviceManager.topology = mockTopology;
        
        Entity entity1 = new Entity(1L, null, null, 1L, 1, c.getTime());
        Entity entity1a = new Entity(1L, null, 1, 1L, 1, c.getTime());
        Entity entity2 = new Entity(1L, null, null, 5L, 1, c.getTime());
        Entity entity3 = new Entity(1L, null, null, 10L, 1, c.getTime());
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/2);
        entity1.setLastSeenTimestamp(c.getTime());
        entity1a.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, 1);
        entity2.setLastSeenTimestamp(c.getTime());
        c.add(Calendar.MILLISECOND, 1);
        entity3.setLastSeenTimestamp(c.getTime());

        deviceManager.learnDeviceByEntity(entity1);
        deviceManager.learnDeviceByEntity(entity1a);
        deviceManager.learnDeviceByEntity(entity2);
        IDevice d = deviceManager.learnDeviceByEntity(entity3);

        // all entities are active, so entity3 should win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1) }, 
                          d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1),
                                             new SwitchPort(1L, 1, 
                                                            DUPLICATE_DEVICE),
                                             new SwitchPort(5L, 1, 
                                                            DUPLICATE_DEVICE) }, 
                          d.getAttachmentPoints(true));

        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/4);
        entity1.setLastSeenTimestamp(c.getTime());

        // all are still active; entity3 should still win
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1) }, 
                          d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(10L, 1),
                                             new SwitchPort(1L, 1, 
                                                            DUPLICATE_DEVICE),
                                             new SwitchPort(5L, 1, 
                                                            DUPLICATE_DEVICE) }, 
                          d.getAttachmentPoints(true));
        
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT+1);
        entity1.setLastSeenTimestamp(c.getTime());
        
        assertEquals(entity1.getActiveSince(), entity1.getLastSeenTimestamp());
        // entity1 should now be the only active entity
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, 
                          d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, 
                          d.getAttachmentPoints(true));
        
        deviceManager.startUp(null);
        c = Calendar.getInstance();
        entity1.setActiveSince(c.getTime());
        entity1.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity1);

        // entity1 is only entity
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, 
                          d.getAttachmentPoints());
        
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/2);
        entity2.setActiveSince(c.getTime());
        entity2.setLastSeenTimestamp(c.getTime());
        d = deviceManager.learnDeviceByEntity(entity2);

        // entity2 is strictly later
        assertArrayEquals(new SwitchPort[] { new SwitchPort(5L, 1) }, 
                          d.getAttachmentPoints());
        
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT);
        entity1.setLastSeenTimestamp(c.getTime());
        
        // entity 1 is strictly later
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1) }, 
                          d.getAttachmentPoints());
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
        replay(mockTopology);
        deviceManager.topology = mockTopology;
        
        Entity entity1 = new Entity(1L, null, null, 1L, 1, c.getTime());
        Entity entity2 = new Entity(1L, null, null, 1L, 2, c.getTime());
        Entity entity3 = new Entity(1L, null, null, 5L, 1, c.getTime());
        Entity entity4 = new Entity(1L, null, null, 5L, 2, c.getTime());
        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT/2);
        entity1.setLastSeenTimestamp(c.getTime());
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
                                             new SwitchPort(5L, 2),
                                             new SwitchPort(1L, 1, 
                                                            DUPLICATE_DEVICE),
                                             new SwitchPort(5L, 1, 
                                                            DUPLICATE_DEVICE) }, 
                          d.getAttachmentPoints(true));

        c.add(Calendar.MILLISECOND, Entity.ACTIVITY_TIMEOUT);
        entity1.setLastSeenTimestamp(c.getTime());

        // entities 3,4 are still in conflict, but 1 should be resolved
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 2) }, 
                          d.getAttachmentPoints());
        assertArrayEquals(new SwitchPort[] { new SwitchPort(1L, 1),
                                             new SwitchPort(5L, 2),
                                             new SwitchPort(5L, 1, 
                                                            DUPLICATE_DEVICE) }, 
                          d.getAttachmentPoints(true));
        
        entity3.setLastSeenTimestamp(c.getTime());
        
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

        doTestDeviceQuery();
    }
    
    @Test
    public void testDeviceQuery() throws Exception {
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
                deviceManager.queryClassDevices(d, null, 
                                                (short)1, 1, null, null);
        int count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(1, count);

        iter = deviceManager.queryClassDevices(d, null, 
                                               (short)3, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(1, count);

        iter = deviceManager.queryClassDevices(d, null, 
                                               (short)1, 3, null, null);
        count = 0;
        while (iter.hasNext()) {
            count += 1;
            iter.next();
        }
        assertEquals(0, count);

        deviceManager.learnDeviceByEntity(entity5);
        iter = deviceManager.queryClassDevices(d, null, 
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

        doTestDeviceClassQuery();
    }

    @Test
    public void testDeviceClassQuery() throws Exception {
        doTestDeviceClassQuery();
    }
}
