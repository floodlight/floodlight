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
import java.util.Map;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
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
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

import org.easymock.EasyMock;
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
    protected OFPacketIn packetIn_1, packetIn_2, packetIn_3;
    protected IPacket testARPReplyPacket_1, testARPReplyPacket_2, testARPReplyPacket_3;
    protected IPacket testARPReqPacket_1, testARPReqPacket_2;
    protected byte[] testARPReplyPacket_1_Serialized, testARPReplyPacket_2_Serialized;
    private byte[] testARPReplyPacket_3_Serialized;
    MockFloodlightProvider mockFloodlightProvider;
    DeviceManagerImpl deviceManager;
    MemoryStorageSource storageSource;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        RestApiServer restApi = new RestApiServer();
        MockThreadPoolService tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);
        mockFloodlightProvider = getMockFloodlightProvider();
        deviceManager = new DeviceManagerImpl();
        fmc.addService(IDeviceManagerService.class, deviceManager);
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
        this.testARPReplyPacket_1_Serialized = testARPReplyPacket_1.serialize();
        
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
        this.testARPReplyPacket_2_Serialized = testARPReplyPacket_2.serialize();
        
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
        this.packetIn_1 = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testARPReplyPacket_1_Serialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testARPReplyPacket_1_Serialized.length);
        
        // Build the PacketIn
        this.packetIn_2 = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testARPReplyPacket_2_Serialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testARPReplyPacket_2_Serialized.length);
        
        // Build the PacketIn
        this.packetIn_3 = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testARPReplyPacket_3_Serialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testARPReplyPacket_3_Serialized.length);
    }

    
    @Test
    public void testAddHostAttachmentPoint() throws Exception {
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        Device d = new Device(((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress());
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
    public void testIsNewer() throws Exception {
        long delta = 100;

        IOFSwitch bdsw1 = createMock(IOFSwitch.class);
        IOFSwitch bdsw2 = createMock(IOFSwitch.class);
        IOFSwitch nonbdsw1 = createMock(IOFSwitch.class);
        IOFSwitch nonbdsw2 = createMock(IOFSwitch.class);
        expect(bdsw1.getId()).andReturn(1L).anyTimes();
        expect(bdsw2.getId()).andReturn(2L).anyTimes();
        expect(nonbdsw1.getId()).andReturn(3L).anyTimes();
        expect(nonbdsw2.getId()).andReturn(4L).anyTimes();

        ITopologyService mockTopology = createMock(ITopologyService.class);
        SwitchPortTuple bdspt1 = new SwitchPortTuple(bdsw1, (short)1);
        SwitchPortTuple bdspt2 = new SwitchPortTuple(bdsw2, (short)1);
        SwitchPortTuple nonbdspt1 = new SwitchPortTuple(nonbdsw1, (short)1);
        SwitchPortTuple nonbdspt2 = new SwitchPortTuple(nonbdsw2, (short)1);
        deviceManager.setTopology(mockTopology);
        expect(mockTopology.isBroadcastDomainPort(1L, (short)1)).andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(2L, (short)1)).andReturn(true).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(3L, (short)1)).andReturn(false).anyTimes();
        expect(mockTopology.isBroadcastDomainPort(4L, (short)1)).andReturn(false).anyTimes();

        // Two BD APs comparison
        Date lastSeen_bd1 = new Date();
        Date lastSeen_bd2 = new Date(lastSeen_bd1.getTime() + DeviceManagerImpl.BD_TO_BD_TIMEDIFF_MS + delta);
        DeviceAttachmentPoint dap_bd1 = new DeviceAttachmentPoint(bdspt1, lastSeen_bd1);
        DeviceAttachmentPoint dap_bd2 = new DeviceAttachmentPoint(bdspt2, lastSeen_bd2);

        Date lastSeen_bd3 = new Date();
        Date lastSeen_bd4 = new Date(lastSeen_bd3.getTime() - DeviceManagerImpl.BD_TO_BD_TIMEDIFF_MS + delta);
        DeviceAttachmentPoint dap_bd3 = new DeviceAttachmentPoint(bdspt1, lastSeen_bd3);
        DeviceAttachmentPoint dap_bd4 = new DeviceAttachmentPoint(bdspt2, lastSeen_bd4);

        // Two non-BD APs comparison
        Date lastSeen_nonbd1 = new Date();
        Date lastSeen_nonbd2 = new Date(lastSeen_nonbd1.getTime() + delta);
        DeviceAttachmentPoint dap_nonbd1 = new DeviceAttachmentPoint(nonbdspt1, lastSeen_nonbd1);
        DeviceAttachmentPoint dap_nonbd2 = new DeviceAttachmentPoint(nonbdspt2, lastSeen_nonbd2);

        Date lastSeen_nonbd3 = new Date();
        Date lastSeen_nonbd4 = new Date(lastSeen_bd3.getTime() - delta);
        DeviceAttachmentPoint dap_nonbd3 = new DeviceAttachmentPoint(nonbdspt1, lastSeen_nonbd3);
        DeviceAttachmentPoint dap_nonbd4 = new DeviceAttachmentPoint(nonbdspt2, lastSeen_nonbd4);

        // BD and non-BD APs comparison
        Date lastSeen_bd5 = new Date();
        Date lastSeen_nonbd5 = new Date(lastSeen_bd3.getTime() - DeviceManagerImpl.NBD_TO_BD_TIMEDIFF_MS + delta);
        DeviceAttachmentPoint dap_bd5 = new DeviceAttachmentPoint(bdspt1, lastSeen_bd5);
        DeviceAttachmentPoint dap_nonbd5 = new DeviceAttachmentPoint(bdspt2, lastSeen_nonbd5);

        Date lastSeen_bd6 = new Date();
        Date lastSeen_nonbd6 = new Date(lastSeen_bd6.getTime() + DeviceManagerImpl.NBD_TO_BD_TIMEDIFF_MS + delta);
        DeviceAttachmentPoint dap_bd6 = new DeviceAttachmentPoint(nonbdspt1, lastSeen_bd6);
        DeviceAttachmentPoint dap_nonbd6 = new DeviceAttachmentPoint(nonbdspt2, lastSeen_nonbd6);

        replay(bdsw1, bdsw2, nonbdsw1, nonbdsw2, mockTopology);

        boolean testbd1_2 = deviceManager.isNewer(dap_bd1, dap_bd2);
        boolean testbd2_1 = deviceManager.isNewer(dap_bd2, dap_bd1);
        boolean testbd3_4 = deviceManager.isNewer(dap_bd3, dap_bd4);
        boolean testbd4_3 = deviceManager.isNewer(dap_bd4, dap_bd3);

        boolean testnonbd1_2 = deviceManager.isNewer(dap_nonbd1, dap_nonbd2);
        boolean testnonbd2_1 = deviceManager.isNewer(dap_nonbd2, dap_nonbd1);
        boolean testnonbd3_4 = deviceManager.isNewer(dap_nonbd3, dap_nonbd4);
        boolean testnonbd4_3 = deviceManager.isNewer(dap_nonbd4, dap_nonbd3);

        boolean testbdnonbd5_5 = deviceManager.isNewer(dap_bd5, dap_nonbd5);
        boolean testnonbdbd5_5 = deviceManager.isNewer(dap_nonbd5, dap_bd5);
        boolean testbdnonbd6_6 = deviceManager.isNewer(dap_bd6, dap_nonbd6);
        boolean testnonbdbd6_6 = deviceManager.isNewer(dap_nonbd6, dap_bd6);

        verify(bdsw1, bdsw2, nonbdsw1, nonbdsw2, mockTopology);

        assertFalse(testbd1_2);
        assertTrue(testbd2_1);
        assertFalse(testbd3_4);
        assertTrue(testbd4_3);

        assertFalse(testnonbd1_2);
        assertTrue(testnonbd2_1);
        assertTrue(testnonbd3_4);
        assertFalse(testnonbd4_3);

        assertTrue(testbdnonbd5_5);
        assertFalse(testnonbdbd5_5);
        assertFalse(testbdnonbd6_6);
        assertTrue(testnonbdbd6_6);
    }

    @Test
    public void testDeviceAging() throws Exception {

        byte[] dataLayerSource = ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch1 = createMock(IOFSwitch.class);
        expect(mockSwitch1.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch1.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        IOFSwitch mockSwitch2 = createMock(IOFSwitch.class);
        ITopologyService mockTopology = createMock(ITopologyService.class);
        expect(mockTopology.isInternal(1L, (short)1)).andReturn(false);
        deviceManager.setTopology(mockTopology);

        // reduce the aging period to a few seconds
        DeviceManagerImpl.DEVICE_AGING_TIMER = 2;
        DeviceManagerImpl.DEVICE_AP_MAX_AGE = 1;
        DeviceManagerImpl.DEVICE_NA_MAX_AGE = 1;
        DeviceManagerImpl.DEVICE_MAX_AGE = 3;

        Date currentDate = new Date();

        // build our expected Device
        Device device = new Device();
        device.setDataLayerAddress(dataLayerSource);
        device.addAttachmentPoint(new SwitchPortTuple(mockSwitch1, (short)1), currentDate);
        Integer ipaddr = IPv4.toIPv4Address("192.168.1.1");
        device.addNetworkAddress(ipaddr, currentDate);

        expect(mockSwitch2.getId()).andReturn(2L).anyTimes();
        expect(mockSwitch2.getStringId()).andReturn("00:00:00:00:00:00:00:02").anyTimes();
        expect(mockTopology.isInternal(2L, (short)2)).andReturn(false);
        expect(mockTopology.inSameCluster(1L, 2L)).andReturn(false).atLeastOnce();
        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        // Start recording the replay on the mocks
        replay(mockSwitch1, mockSwitch2, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch1, this.packetIn_1);

        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch2, this.packetIn_3.setInPort((short)2));

        // Verify the replay matched our expectations
        verify(mockSwitch1, mockSwitch2, mockTopology);
        // Verify the device
        Device rdevice = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertEquals(device, rdevice);
        assertEquals(2, rdevice.getAttachmentPoints().size());
        assertEquals(2, rdevice.getNetworkAddresses().size());

        // Sleep to make sure the aging thread has run
        Thread.sleep((DeviceManagerImpl.DEVICE_AGING_TIMER + DeviceManagerImpl.DEVICE_AGING_TIMER_INTERVAL)*1000);

        rdevice = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertEquals(0, rdevice.getAttachmentPoints().size());
        assertEquals(0, rdevice.getNetworkAddresses().size());

        // Make sure the device's AP and NA were removed from storage
        deviceManager.readAllDeviceStateFromStorage();
        rdevice = deviceManager.getDeviceByDataLayerAddress(dataLayerSource);
        assertEquals(0, rdevice.getAttachmentPoints().size());
        assertEquals(0, rdevice.getNetworkAddresses().size());

        // Sleep 4 more seconds to allow device aging thread to run
        Thread.sleep(DeviceManagerImpl.DEVICE_MAX_AGE*1000);

        assertNull(deviceManager.getDeviceByDataLayerAddress(dataLayerSource));

        // Make sure the device's AP and NA were removed from storage
        deviceManager.readAllDeviceStateFromStorage();
        assertNull(deviceManager.getDeviceByDataLayerAddress(dataLayerSource));

        // Reset the device cache
        deviceManager.clearAllDeviceStateFromMemory();
    }

    @Test
    public void testDeviceDiscover() throws Exception {

        byte[] dataLayerSource = ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

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

        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1);

        // Verify the replay matched our expectations
        verify(mockSwitch);

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

        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);
        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));

        // Verify the replay matched our expectations
        verify(mockSwitch, mockTopology);

        // Verify the device
        assertEquals(device, deviceManager.getDeviceByDataLayerAddress(dataLayerSource));

        // Reset the device cache
        deviceManager.clearAllDeviceStateFromMemory();
    }

    @Test
    public void testDeviceRecoverFromStorage() throws Exception {
        byte[] dataLayerSource = ((Ethernet)this.testARPReplyPacket_2).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        ITopologyService mockTopology = createNiceMock(ITopologyService.class);

        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        expect(mockSwitch.getStringId()).andReturn("00:00:00:00:00:00:00:01").anyTimes();
        expect(mockTopology.isInternal(1L, (short)1)).andReturn(false);
        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
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
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_2);
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_3);

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
        byte[] dataLayerSource = ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).atLeastOnce();
        ITopologyService mockTopology = createNiceMock(ITopologyService.class);
        //expect(mockTopology.isInternal(new SwitchPortTuple(mockSwitch, 1))).andReturn(false);
        deviceManager.setTopology(mockTopology);
        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

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
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1);

        Thread.sleep(100);

        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1);

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

        byte[] dataLayerSource = ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

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
        expect(mockTopology.isBroadcastDomainPort(1L, (short)1))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.isBroadcastDomainPort(1L, (short)2))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.inSameCluster(1L, 1L)).andReturn(true).atLeastOnce();
        deviceManager.setTopology(mockTopology);
        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();

        // Start recording the replay on the mocks
        expect(mockTopology.isInSameBroadcastDomain((long)1, (short)2, (long)1, (short)1)).andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain((long)1, (short)1, (long)1, (short)2)).andReturn(false).anyTimes();
        replay(mockSwitch, mockTopology);

        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1);
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));

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
        byte[] dataLayerSource = ((Ethernet)this.testARPReplyPacket_1).getSourceMACAddress();

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
        expect(mockTopology.isBroadcastDomainPort(1L, (short)1))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.isBroadcastDomainPort(1L, (short)2))
                           .andReturn(false).atLeastOnce();
        expect(mockTopology.inSameCluster(1L, 1L)).andReturn(true).atLeastOnce();
        expect(mockTopology.isInSameBroadcastDomain((long)1, (short)1, 
                                                    (long)1, (short)2)).andReturn(false).anyTimes();
        expect(mockTopology.isInSameBroadcastDomain((long)1, (short)2, 
                                                    (long)1, (short)1)).andReturn(false).anyTimes();

        deviceManager.setTopology(mockTopology);
        expect(mockTopology.isAllowed(EasyMock.anyLong(), EasyMock.anyShort())).andReturn(true).anyTimes();
        // Start recording the replay on the mocks
        replay(mockSwitch, mockTopology);

        // Get the listener and trigger the packet in
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1);
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)1));
        mockFloodlightProvider.dispatchMessage(mockSwitch, this.packetIn_1.setInPort((short)2));

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
