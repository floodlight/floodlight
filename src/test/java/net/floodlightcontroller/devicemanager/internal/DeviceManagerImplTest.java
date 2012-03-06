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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.isA;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import static net.floodlightcontroller.devicemanager.IDeviceManagerService.DeviceField.*;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.devicemanager.IDeviceManagerService.DeviceField;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.topology.ITopologyService;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class DeviceManagerImplTest extends FloodlightTestCase {
    private OFPacketIn packetIn;
    private IPacket testPacket;
    private byte[] testPacketSerialized;
    MockFloodlightProvider mockFloodlightProvider;
    DeviceManagerImpl deviceManager;
    MemoryStorageSource storageSource;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        mockFloodlightProvider = getMockFloodlightProvider();
        deviceManager = new DeviceManagerImpl();
        fmc.addService(IDeviceManagerService.class, deviceManager);
        storageSource = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IRestApiService.class, restApi);
        restApi.init(fmc);
        storageSource.init(fmc);
        deviceManager.init(fmc);
        storageSource.startUp(fmc);
        deviceManager.startUp(fmc);
        
        // Build our test packet
        this.testPacket = new Ethernet()
            .setSourceMACAddress("00:44:33:22:11:00")
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
                    .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                    .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                    .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                    .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        this.testPacketSerialized = testPacket.serialize();
        
        // Build the PacketIn
        this.packetIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testPacketSerialized.length);
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
        public EnumSet<IDeviceManagerService.DeviceField> getKeyFields() {
            return testKeyFields;
        }
        
    }
    
    @Test
    public void testEntityLearning() throws Exception {
        IDeviceManagerAware mockListener = 
                createStrictMock(IDeviceManagerAware.class);
        
        deviceManager.addListener(mockListener);
        deviceManager.setEntityClassifier(new TestEntityClassifier());
        
        Entity entity1 = new Entity(1L, null, null, 1L, 1, new Date());
        Entity entity2 = new Entity(1L, null, null, 10L, 1, new Date());
        Entity entity3 = new Entity(1L, null, 1, 10L, 1, new Date());
        Entity entity4 = new Entity(1L, null, 1, 1L, 1, new Date());
        Entity entity5 = new Entity(2L, (short)4, 1, 5L, 2, new Date());
        Entity entity6 = new Entity(2L, (short)4, 1, 50L, 3, new Date());

        
        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener);
        
        Device d1 = deviceManager.learnDeviceByEntity(entity1);        
        assertSame(d1, deviceManager.learnDeviceByEntity(entity1)); 
        assertSame(d1, deviceManager.findDeviceByEntity(entity1));
        assertArrayEquals(new IEntityClass[]{ DefaultEntityClassifier.entityClass }, 
                          d1.entityClasses);

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
    }
    
    @Test
    public void testAttachmentPointLearning() throws Exception {
        IDeviceManagerAware mockListener = 
                createStrictMock(IDeviceManagerAware.class);
        
        deviceManager.addListener(mockListener);

        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.inSameCluster(1L, 2L)).andReturn(true).anyTimes();
        expect(mockTopology.inSameCluster(2L, 1L)).andReturn(true).anyTimes();

        expect(mockTopology.inSameCluster(3L, 4L)).andReturn(true).anyTimes();
        expect(mockTopology.inSameCluster(4L, 3L)).andReturn(true).anyTimes();
        
        expect(mockTopology.inSameCluster(1L, 3L)).andReturn(false).anyTimes();
        expect(mockTopology.inSameCluster(3L, 1L)).andReturn(false).anyTimes();
        expect(mockTopology.inSameCluster(1L, 4L)).andReturn(false).anyTimes();
        expect(mockTopology.inSameCluster(4L, 1L)).andReturn(false).anyTimes();

        expect(mockTopology.inSameCluster(2L, 3L)).andReturn(false).anyTimes();
        expect(mockTopology.inSameCluster(3L, 2L)).andReturn(false).anyTimes();
        expect(mockTopology.inSameCluster(2L, 4L)).andReturn(false).anyTimes();
        expect(mockTopology.inSameCluster(4L, 2L)).andReturn(false).anyTimes();
        
        replay(mockTopology);
        
        deviceManager.topology = mockTopology;
        
        Entity entity1 = new Entity(1L, null, 1, 1L, 1, new Date());
        Entity entity2 = new Entity(1L, null, null, 2L, 1, new Date());
        Entity entity3 = new Entity(1L, null, null, 3L, 1, new Date());
        Entity entity4 = new Entity(1L, null, null, 4L, 1, new Date());
        
        IDevice d;
        SwitchPort[] aps;
        Integer[] ips;

        mockListener.deviceAdded(isA(IDevice.class));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity1);
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
        assertArrayEquals(new SwitchPort[] { new SwitchPort(2L, 1) }, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity3);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints(); 
        assertArrayEquals(new SwitchPort[] { new SwitchPort(3L, 1),
                                             new SwitchPort(2L, 1) }, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);

        reset(mockListener);
        mockListener.deviceMoved((isA(IDevice.class)));
        replay(mockListener);

        d = deviceManager.learnDeviceByEntity(entity4);
        assertEquals(1, deviceManager.getAllDevices().size());
        aps = d.getAttachmentPoints(); 
        assertArrayEquals(new SwitchPort[] { new SwitchPort(2L, 1), 
                                             new SwitchPort(4L, 1) }, aps);
        ips = d.getIPv4Addresses();
        assertArrayEquals(new Integer[] { 1 }, ips);
        verify(mockListener);
    }

    @Test
    public void testPacketIn() throws Exception {
        deviceManager.addIndex(true, true, EnumSet.of(DeviceField.IPV4));
        byte[] dataLayerSource = ((Ethernet)this.testPacket).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getStringId()).
            andReturn("00:00:00:00:00:00:00:01").anyTimes();
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isInternal(mockSwitch, 
                                       (short)1)).andReturn(false).anyTimes();
        deviceManager.topology = mockTopology;

        Date currentDate = new Date();
        
        // build our expected Device
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        Device device = 
                new Device(new Long(deviceManager.deviceKeyCounter),
                           new Entity(Ethernet.toLong(dataLayerSource),
                                      (short)5,
                                      ipaddr,
                                      1L,
                                      1,
                                      currentDate),
                           DefaultEntityClassifier.entityClasses);

        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn);

        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

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
                new Device(new Long(deviceManager.deviceKeyCounter),
                           new Entity(Ethernet.toLong(dataLayerSource),
                                      (short)5,
                                      ipaddr,
                                      2L,
                                      2,
                                      currentDate),
                           DefaultEntityClassifier.entityClasses);

        reset(mockSwitch, mockTopology);
        expect(mockSwitch.getId()).andReturn(2L).anyTimes();
        expect(mockSwitch.getStringId()).
            andReturn("00:00:00:00:00:00:00:02").anyTimes();
        expect(mockTopology.isInternal(mockSwitch, (short)2)).andReturn(false);
        expect(mockTopology.inSameCluster(1L, 2L)).andReturn(true);
        
        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, 
                                               this.packetIn.setInPort((short)2));

        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

        // Verify the device
        rdevice = (Device)
                deviceManager.findDevice(Ethernet.toLong(dataLayerSource), 
                                         (short)5, null, null, null);
        assertEquals(device, rdevice);
    }
    
    @Test
    public void testAttachmentPointFlapping() throws Exception {
        fail();
        /*
    	OFPhysicalPort port1 = new OFPhysicalPort();
    	OFPhysicalPort port2 = new OFPhysicalPort();
        port1.setName("port1");
        port2.setName("port2");
        
        byte[] dataLayerSource = ((Ethernet)this.testPacket).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockSwitch.getPort((short)1)).andReturn(port1).anyTimes();
        expect(mockSwitch.getPort((short)2)).andReturn(port2).anyTimes();
        expect(mockTopology.isInternal(new SwitchPortTuple(mockSwitch, 1)))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.isInternal(new SwitchPortTuple(mockSwitch, 2)))
                           .andReturn(false).atLeastOnce();
        deviceManager.setTopology(mockTopology);

        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);

        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn);
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));

        Device device = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        
        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

        // Verify the device
        assertEquals(device.getAttachmentPoints().size(), 1);
        assertEquals(device.getOldAttachmentPoints().size(), 1);
        for (DeviceAttachmentPoint ap : device.getOldAttachmentPoints()) {
            assertTrue(ap.isBlocked());
        }
        
        // Reset the device cache
        deviceManager.clearAllDeviceStateFromMemory();
        */
    }
    /*
    private static final Map<String, Object> pcPort1;
    static {
    	pcPort1 = new HashMap<String, Object>();
    	pcPort1.put(DeviceManagerImpl.PORT_CHANNEL_COLUMN_NAME, "channel");
    	pcPort1.put(DeviceManagerImpl.PC_PORT_COLUMN_NAME, "port1");
    	pcPort1.put(DeviceManagerImpl.PC_SWITCH_COLUMN_NAME, "00:00:00:00:00:00:00:01");
    	pcPort1.put(DeviceManagerImpl.PC_ID_COLUMN_NAME, "00:00:00:00:00:00:00:01|port1");
    }
    
    private static final Map<String, Object> pcPort2;
    static {
    	pcPort2 = new HashMap<String, Object>();
    	pcPort2.put(DeviceManagerImpl.PORT_CHANNEL_COLUMN_NAME, "channel");
    	pcPort2.put(DeviceManagerImpl.PC_PORT_COLUMN_NAME, "port2");
    	pcPort2.put(DeviceManagerImpl.PC_SWITCH_COLUMN_NAME, "00:00:00:00:00:00:00:01");
    	pcPort2.put(DeviceManagerImpl.PC_ID_COLUMN_NAME, "00:00:00:00:00:00:00:01|port2");
    }
    
    private void setupPortChannel() {
        storageSource.insertRow(DeviceManagerImpl.PORT_CHANNEL_TABLE_NAME, pcPort1);
        storageSource.insertRow(DeviceManagerImpl.PORT_CHANNEL_TABLE_NAME, pcPort2);
        deviceManager.readPortChannelConfigFromStorage();
    }
    
    private void teardownPortChannel() {
        storageSource.deleteRow(DeviceManagerImpl.PORT_CHANNEL_TABLE_NAME,
                pcPort1.get(DeviceManagerImpl.PC_ID_COLUMN_NAME));
        storageSource.deleteRow(DeviceManagerImpl.PORT_CHANNEL_TABLE_NAME,
                pcPort2.get(DeviceManagerImpl.PC_ID_COLUMN_NAME));
        deviceManager.readPortChannelConfigFromStorage();
    }
    */
    /**
     * The same test as testAttachmentPointFlapping except for port-channel
     * @throws Exception
     */
    @Test
    public void testPortChannel() throws Exception {
        fail();
        /*
    	OFPhysicalPort port1 = new OFPhysicalPort();
    	OFPhysicalPort port2 = new OFPhysicalPort();
        port1.setName("port1");
        port2.setName("port2");

        setupPortChannel();
        byte[] dataLayerSource = ((Ethernet)this.testPacket).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getPort((short)1)).andReturn(port1).anyTimes();
        expect(mockSwitch.getPort((short)2)).andReturn(port2).anyTimes();
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isInternal(new SwitchPortTuple(mockSwitch, 1)))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.isInternal(new SwitchPortTuple(mockSwitch, 2)))
                           .andReturn(false).atLeastOnce();
        deviceManager.setTopology(mockTopology);

        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);

        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn);
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));

        Device device = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        
        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

        // Verify the device
        assertEquals(device.getAttachmentPoints().size(), 1);
        assertEquals(device.getOldAttachmentPoints().size(), 1);
        for (DeviceAttachmentPoint ap : device.getOldAttachmentPoints()) {
            assertFalse(ap.isBlocked());
        }
        
        // Reset the device cache
        deviceManager.clearAllDeviceStateFromMemory();
        
        teardownPortChannel();
        */
    }
}
