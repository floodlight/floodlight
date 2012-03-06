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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.linkdiscovery.SwitchPortTuple;
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

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPhysicalPort;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class DeviceManagerImplTest extends FloodlightTestCase {
    protected OFPacketIn packetIn, anotherPacketIn;
    protected IPacket testPacket, anotherTestPacket;
    protected byte[] testPacketSerialized, anotherTestPacketSerialized;
    private IPacket thirdTestPacket;
    private byte[] thirdTestPacketSerialized;
    private OFPacketIn thirdPacketIn;
    MockFloodlightProvider mockFloodlightProvider;
    DeviceManagerImpl deviceManager;
    MemoryStorageSource storageSource;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        Set<IFloodlightModule> modSet = new HashSet<IFloodlightModule>();
        RestApiServer restApi = new RestApiServer();
        modSet.add(restApi);
        mockFloodlightProvider = getMockFloodlightProvider();
        modSet.add(mockFloodlightProvider);
        deviceManager = new DeviceManagerImpl();
        modSet.add(deviceManager);
        fmc.addService(IDeviceManagerService.class, deviceManager);
        storageSource = new MemoryStorageSource();
        modSet.add(storageSource);
        fmc.addService(IStorageSourceService.class, storageSource);
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IRestApiService.class, restApi);
        fmc.createConfigMaps(modSet);
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
        
        // Another test packet with a different source IP
        this.anotherTestPacket = new Ethernet()
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
        this.anotherTestPacketSerialized = anotherTestPacket.serialize();
        
        this.thirdTestPacket = new Ethernet()
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
        this.thirdTestPacketSerialized = thirdTestPacket.serialize();
        
        // Build the PacketIn
        this.packetIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testPacketSerialized.length);
        
        // Build the PacketIn
        this.anotherPacketIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.anotherTestPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.anotherTestPacketSerialized.length);
        
        // Build the PacketIn
        this.thirdPacketIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.thirdTestPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.thirdTestPacketSerialized.length);
    }

    
    @Test
    public void testAddHostAttachmentPoint() throws Exception {
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        Device d = new Device(((Ethernet)this.testPacket).getSourceMACAddress());
        Date cDate = new Date();
        SwitchPortTuple spt1 = new SwitchPortTuple(mockSwitch, (short)1);
        SwitchPortTuple spt2 = new SwitchPortTuple(mockSwitch, (short)2);
        DeviceAttachmentPoint dap1 = new DeviceAttachmentPoint(spt1, cDate);
        DeviceAttachmentPoint dap2 = new DeviceAttachmentPoint(spt2, cDate);
        d.addAttachmentPoint(dap1);
        d.addAttachmentPoint(dap2);

        assertEquals(dap1, d.getAttachmentPoint(spt1));
        assertEquals(dap2, d.getAttachmentPoint(spt2));
        assertEquals((int)2, d.getAttachmentPoints().size());
    }
    
    @Test
    public void testDeviceDiscover() throws Exception {
        
        byte[] dataLayerSource = ((Ethernet)this.testPacket).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isInternal(1L, (short)1)).andReturn(false);
        deviceManager.setTopology(mockTopology);

        Date currentDate = new Date();
        
        // build our expected Device
        Device device = new Device();
        device.setDataLayerAddress(dataLayerSource);
        device.addAttachmentPoint(new SwitchPortTuple(mockSwitch, (short)1), currentDate);
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        device.addNetworkAddress(ipaddr, currentDate);

        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn);

        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

        // Verify the device
        Device rdevice = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertEquals(device, rdevice);
        assertEquals(new Short((short)5), rdevice.getVlanId());
        assertEquals(device, deviceManager.getDeviceByIPv4Address(ipaddr));

        // move the port on this device
        device.addAttachmentPoint(new SwitchPortTuple(mockSwitch, (short)2), currentDate);
        
        reset(mockSwitch, mockTopology);
        expect(mockSwitch.getId()).andReturn(2L).anyTimes();
        expect(mockSwitch.getStringId()).andReturn("00:00:00:00:00:00:00:02").anyTimes();
        expect(mockTopology.isInternal(2L, (short)2)).andReturn(false);

        assertEquals(1, deviceManager.getDeviceByIPv4Address(ipaddr).getAttachmentPoints().size());
        deviceManager.invalidateDeviceAPsByIPv4Address(ipaddr);
        assertEquals(0, deviceManager.getDeviceByIPv4Address(ipaddr).getAttachmentPoints().size());
        
        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn.setInPort((short)2));

        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

        // Verify the device
        assertEquals(device, deviceManager.getDeviceByDataLayerAddress(dataLayerSource));
        
        // Reset the device cache
        deviceManager.clearAllDeviceStateFromMemory();
    }
    
    @Test
    public void testDeviceRecoverFromStorage() throws Exception {
        byte[] dataLayerSource = ((Ethernet)this.anotherTestPacket).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        ITopologyService mockTopology = createNiceMock(ITopologyService.class);
        
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        expect(mockTopology.isInternal(1L, (short)1)).andReturn(false);
        deviceManager.setTopology(mockTopology);

        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        
        // Add the switch so the list isn't empty
        mockFloodlightProvider.getSwitches().put(mockSwitch.getId(), mockSwitch);

        // build our expected Device
        Device device = new Device();
        Date currentDate = new Date();
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        Integer ipaddr2 = IPv4.toIPv4Address("192.168.1.3");
        device.setDataLayerAddress(dataLayerSource);
        SwitchPortTuple spt = new SwitchPortTuple(mockSwitch, (short)1);
        DeviceAttachmentPoint dap = new DeviceAttachmentPoint(spt, currentDate);
        device.addAttachmentPoint(dap);
        device.addNetworkAddress(ipaddr, currentDate);
        device.addNetworkAddress(ipaddr2, currentDate);
        
        // Get the listener and trigger the packet ins
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.anotherPacketIn);
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.thirdPacketIn);

        // Verify the device
        assertEquals(device, deviceManager.getDeviceByDataLayerAddress(dataLayerSource));
        assertEquals(device, deviceManager.getDeviceByIPv4Address(ipaddr));
        assertEquals(device, deviceManager.getDeviceByIPv4Address(ipaddr2));
        assertEquals(dap, device.getAttachmentPoint(spt));
        
        // Reset the device cache
        deviceManager.clearAllDeviceStateFromMemory();
        
        // Verify the device
        assertNull(deviceManager.getDeviceByDataLayerAddress(dataLayerSource));
        assertNull(deviceManager.getDeviceByIPv4Address(ipaddr));
        assertNull(deviceManager.getDeviceByIPv4Address(ipaddr2));
        
        // Load the device cache from storage
        deviceManager.readAllDeviceStateFromStorage();
        
        // Verify the device
        Device device2 = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertEquals(device, device2);
        assertEquals(dap, device2.getAttachmentPoint(spt));
        
        deviceManager.clearAllDeviceStateFromMemory();
        mockFloodlightProvider.setSwitches(new HashMap<Long,IOFSwitch>());
        deviceManager.removedSwitch(mockSwitch);
        deviceManager.readAllDeviceStateFromStorage();
        
        device2 = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertEquals(device, device2);

        assertNull(device2.getAttachmentPoint(spt));
        // The following two asserts seems to be incorrect, need to
        // replace NULL check with the correct value TODO
        //assertNull(deviceManager.getDeviceByIPv4Address(ipaddr));
        //assertNull(deviceManager.getDeviceByIPv4Address(ipaddr2));
        deviceManager.addedSwitch(mockSwitch);
        assertEquals(dap, device.getAttachmentPoint(spt));
        assertEquals(device, deviceManager.getDeviceByIPv4Address(ipaddr));
        assertEquals(device, deviceManager.getDeviceByIPv4Address(ipaddr2));
    }
    
    @Test
    public void testDeviceUpdateLastSeenToStorage() throws Exception {
        deviceManager.clearAllDeviceStateFromMemory();
        
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        byte[] dataLayerSource = ((Ethernet)this.testPacket).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).atLeastOnce();
        ITopologyService mockTopology = createNiceMock(ITopologyService.class);
        //expect(mockTopology.isInternal(new SwitchPortTuple(mockSwitch, 1))).andReturn(false);
        deviceManager.setTopology(mockTopology);

        Date currentDate = new Date();
        // build our expected Device
        Device device = new Device();
        // Set Device to always update last-seen to storage
        Device.setStorageUpdateInterval(1);
        device.setDataLayerAddress(dataLayerSource);
        device.addAttachmentPoint(new SwitchPortTuple(mockSwitch, (short)1), currentDate);
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        device.addNetworkAddress(ipaddr, currentDate);

        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn);
        
        Thread.sleep(100);
        
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn);
        
        // Clear the device cache
        deviceManager.clearAllDeviceStateFromMemory();
        // Load the device cache from storage
        deviceManager.readAllDeviceStateFromStorage();
        
        // Make sure the last seen is after our date
        device = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertTrue(device.getLastSeen().after(currentDate));
    }
    
    @Test
    public void testAttachmentPointFlapping() throws Exception {
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
        expect(mockTopology.isInternal(1L, (short)1))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.isInternal(1L, (short)2))
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
    }
    
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
    
    /**
     * The same test as testAttachmentPointFlapping except for port-channel
     * @throws Exception
     */
    @Test
    public void testPortChannel() throws Exception {
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
        expect(mockTopology.isInternal(1L, (short)1))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.isInternal(1L, (short)2))
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
    }
}
