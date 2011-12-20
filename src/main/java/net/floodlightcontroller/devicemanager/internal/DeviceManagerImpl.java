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

/**
 *
 */
package net.floodlightcontroller.devicemanager.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.devicemanager.DeviceNetworkAddress;
import net.floodlightcontroller.devicemanager.IDeviceManager;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSource;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.topology.ITopology;
import net.floodlightcontroller.topology.ITopologyAware;
import net.floodlightcontroller.topology.SwitchPortTuple;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeviceManager creates Devices based upon MAC addresses seen in the network.
 * It tracks any network addresses mapped to the Device, and its location
 * within the network.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class DeviceManagerImpl implements IDeviceManager, IOFMessageListener,
        IOFSwitchListener, ITopologyAware {  
    protected static Logger log = 
        LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected IFloodlightProvider floodlightProvider;

    /**
     * Class to maintain all the device manager maps which consists of four
     * main maps. 
     * (a) data layer address (mac) to device object
     * (b) ipv4 address to device object
     * (c) Switch object to device objects
     * (d) Switch port tuple object to device objects
     * 
     * Also maintained is a pending attachment point object which is a
     * mapping from switch dpid to pending attachment point object
     * @author subrata
     */
    public class DevMgrMaps {
        private Map<Long, Device> dataLayerAddressDeviceMap;
        private Map<Integer, Device> ipv4AddressDeviceMap;
        // The Integer below if the hashCode iof the device
        private Map<IOFSwitch, Map<Integer, Device>> switchDeviceMap;
        // The Integer below is the hashCode of the device
        private Map<SwitchPortTuple, Map<Integer, Device>> switchPortDeviceMap;
        private Map<Long, List<PendingAttachmentPoint>> switchUnresolvedAPMap;

        public Map<Long, Device> getDataLayerAddressDeviceMap() {
            return dataLayerAddressDeviceMap;
        }

        public Map<Integer, Device> getIpv4AddressDeviceMap() {
            return ipv4AddressDeviceMap;
        }

        public Map<IOFSwitch, Map<Integer, Device>> getSwitchDeviceMap() {
            return switchDeviceMap;
        }

        public Map<SwitchPortTuple, Map<Integer, Device>> 
                                                getSwitchPortDeviceMap() {
            return switchPortDeviceMap;
        }

        public Map<Long, List<PendingAttachmentPoint>> 
                                                getSwitchUnresolvedAPMap() {
            return switchUnresolvedAPMap;
        }

        // Constructor for the device maps class
        public DevMgrMaps() {
            dataLayerAddressDeviceMap = new ConcurrentHashMap<Long, Device>();
            ipv4AddressDeviceMap = new ConcurrentHashMap<Integer, Device>();
            switchDeviceMap = 
                new ConcurrentHashMap<IOFSwitch, Map<Integer, Device>>();
            switchPortDeviceMap =
                new ConcurrentHashMap<SwitchPortTuple, Map<Integer, Device>>();
            switchUnresolvedAPMap = 
                new ConcurrentHashMap<Long, List<PendingAttachmentPoint>>();
        }

        // ***********
        // Get methods
        // ***********

        protected Collection<Device> getDevicesOnASwitch(IOFSwitch sw) {
            return switchDeviceMap.get(sw).values();
        }

        protected int getSwitchCount() {
            return switchDeviceMap.size();
        }

        protected Set<IOFSwitch> getSwitches() {
            return switchDeviceMap.keySet();
        }

        protected Set<SwitchPortTuple> getSwitchPorts() {
            return switchPortDeviceMap.keySet();
        }

        protected Collection<Device> getDevices() {
            lock.readLock().lock(); // Do we need the lock TODO
            try {
                return dataLayerAddressDeviceMap.values();
            } finally {
                lock.readLock().unlock();
            }
        }

        // *****************************************************
        // Individual update and delete methods for given device
        // *****************************************************

        private void updateDataLayerAddressDeviceMap(Device d) {
            dataLayerAddressDeviceMap.put(d.getDataLayerAddressAsLong(), d);
        }

        private void delFromDataLayerAddressDeviceMap(Device d) {
            dataLayerAddressDeviceMap.remove(d.getDataLayerAddressAsLong());
        }

        private void updateIpv4AddressDeviceMap(Device d) {
            Collection< DeviceNetworkAddress> dNAColl = d.getNetworkAddresses();
            for (DeviceNetworkAddress dna : dNAColl) {
                ipv4AddressDeviceMap.put(dna.getNetworkAddress(), d);
            }
        }

        private void delFromIpv4AddressDeviceMap(Device d) {
            Collection< DeviceNetworkAddress> dNAColl = d.getNetworkAddresses();
            for (DeviceNetworkAddress dna : dNAColl) {
                ipv4AddressDeviceMap.remove(dna.getNetworkAddress());
            }
        }

        private void updateSwitchDeviceMap(Device d) {
            // Find all the attachment points of this device
            // Then find all the switches from the attachment points
            // Then update the device in maps for all those switches
            Collection<DeviceAttachmentPoint> dapColl = d.getAttachmentPoints();
            for (DeviceAttachmentPoint dap : dapColl) {
                SwitchPortTuple swPrt = dap.getSwitchPort();
                IOFSwitch sw = swPrt.getSw();
                Map<Integer, Device> oneSwDevMap = switchDeviceMap.get(sw);
                if (oneSwDevMap == null) {
                    oneSwDevMap = new ConcurrentHashMap<Integer, Device>();
                    switchDeviceMap.put(sw, oneSwDevMap);
                }
                oneSwDevMap.put(d.hashCode(), d);
            }
        }

        private void delFromSwitchDeviceMap(Device d) {
            // Find all the attachment points of this device
            // Then find all the switches from the attachment points
            // Then update the device in maps for all those switches
            Collection<DeviceAttachmentPoint> dapColl = d.getAttachmentPoints();
            for (DeviceAttachmentPoint dap : dapColl) {
                SwitchPortTuple swPrt = dap.getSwitchPort();
                IOFSwitch sw = swPrt.getSw();
                Map<Integer, Device> oneSwDevMap = switchDeviceMap.get(sw);
                if (oneSwDevMap != null) {
                    oneSwDevMap.remove(d.hashCode());
                }
            }
        }

        private void delFromSwitchDeviceMap(IOFSwitch sw, Device d) {
            Map<Integer, Device> oneSwDevMap = switchDeviceMap.get(sw);
            if (oneSwDevMap != null) {
                oneSwDevMap.remove(d.hashCode());
            }
        }

        protected void delSwitchfromMaps(IOFSwitch sw) {
            // Remove all switch:port mappings of the given switch sw
            Set<SwitchPortTuple> swPortSet = devMgrMaps.getSwitchPorts();
            for (SwitchPortTuple swPort : swPortSet) {
                if (swPort.getSw().equals(sw)) {
                    devMgrMaps.removeSwPort(swPort);
                }
            }

            // Not removing the switch from the SwitchDevice map, let it age out
        }

        private void updateSwitchPortDeviceMap(Device d) {
            // Find all the attachment points of this device
            // Then update the device in maps for all those attachment points
            Collection<DeviceAttachmentPoint> dapColl = d.getAttachmentPoints();
            for (DeviceAttachmentPoint dap : dapColl) {
                SwitchPortTuple swPrt = dap.getSwitchPort();
                Map<Integer, Device> oneSwPrtMap = 
                                        switchPortDeviceMap.get(swPrt);
                if (oneSwPrtMap == null) {
                    oneSwPrtMap = new ConcurrentHashMap<Integer, Device>();
                    switchPortDeviceMap.put(swPrt, oneSwPrtMap);
                }
                oneSwPrtMap.put(d.hashCode(), d);
            }
        }

        private void delFromSwitchPortDeviceMap(Device d) {
            // Find all the attachment points of this device
            // Then update the device in maps for all those attachment points
            Collection<DeviceAttachmentPoint> dapColl = d.getAttachmentPoints();
            for (DeviceAttachmentPoint dap : dapColl) {
                SwitchPortTuple swPrt = dap.getSwitchPort();
                Map<Integer, Device> oneSwPrtMap = 
                                        switchPortDeviceMap.get(swPrt);
                if (oneSwPrtMap != null) {
                    oneSwPrtMap.remove(d.hashCode());
                }
            }
        }

        private void delFromSwitchPortDeviceMap(
                                        SwitchPortTuple swPort, Device d) {
            Map<Integer, Device> oneSwPrtMap = switchPortDeviceMap.get(swPort);
            if (oneSwPrtMap != null) {
                oneSwPrtMap.remove(d.hashCode());
            }
        }

        /**
         * Overall update, delete and clear methods for the set of maps
         * @param d the device to update
         */
        private void updateMaps(Device d) {
            // Update dataLayerAddressDeviceMap
            updateDataLayerAddressDeviceMap(d);
            // Update ipv4AddressDeviceMap
            updateIpv4AddressDeviceMap(d);
            // update switchDeviceMap
            updateSwitchDeviceMap(d);
            // Update switchPortDeviceMap
            updateSwitchPortDeviceMap(d);
        }

        /** 
         * Delete a device object from the device maps
         * @param d the device to remove
         */
        private void delFromMaps(Device d) {
            // Update dataLayerAddressDeviceMap
            delFromDataLayerAddressDeviceMap(d);
            // Update ipv4AddressDeviceMap
            delFromIpv4AddressDeviceMap(d);
            // update switchDeviceMap
            delFromSwitchDeviceMap(d);
            // Update switchPortDeviceMap
            delFromSwitchPortDeviceMap(d);
        }

        /**
         * Delete a device by a data layer address
         * @param dlAddr the address
         */
        protected void delFromMaps(long dlAddr) {
            Device d = devMgrMaps.getDeviceByDataLayerAddr(dlAddr);
            if (d != null) {
                delFromMaps(d);
            }
        }

        /**
         * Clear all the device maps
         */
        private void clearMaps() {
            dataLayerAddressDeviceMap.clear();
            ipv4AddressDeviceMap.clear();
            switchUnresolvedAPMap.clear();
            switchDeviceMap.clear();
            switchPortDeviceMap.clear();
        }

        /**
         * Remove switch-port and also remove this switch-port as the
         * attachment point in the device objects if any
         * @param swPrt The {@link SwitchPortTuple} to remove
         */
        protected void removeSwPort(SwitchPortTuple swPrt) { // done

            Map<Integer, Device> switchPortDevices = 
                                            switchPortDeviceMap.remove(swPrt);

            if (switchPortDevices == null) {
                return;
            }

            // Update the individual devices by updating its attachment points
            for (Device d : switchPortDevices.values()) {
                // Remove the device from the switch->device mapping
                delDevAttachmentPoint(d, swPrt);
            }
        }

        /**
         * Retrieve the device by it's data layer address
         * @param mac the mac address to look up
         * @return the device object
         */
        protected Device getDeviceByDataLayerAddr(long mac) {
            return dataLayerAddressDeviceMap.get(mac);
        }

        /**
         * Check whether the given switch exists in the switch maps
         * @param sw the switch to check
         * @return true if the switch exists
         */
        protected boolean isSwitchPresent (IOFSwitch sw) {
            return switchDeviceMap.containsKey(sw);
        }

        // ***************************************
        // Add operations on the device attributes
        // ***************************************

        /**
         * Add a network layer address to the device with the given link 
         * layer address
         * @param dlAddr The link layer address of the device
         * @param nwAddr the new network layer address
         * @param lastSeen the new last seen timestamp for the network address
         */
        protected void addNwAddrByDataLayerAddr(long dlAddr,
                                                int nwAddr,
                                                Date lastSeen) {
            Device d = getDeviceByDataLayerAddr(dlAddr);
            if (d == null) return;
            Device dCopy = new Device(d);
            DeviceNetworkAddress na = dCopy.getNetworkAddress(nwAddr);
            if (na == null) { // this particular address is not in the device
                na = new DeviceNetworkAddress(nwAddr, lastSeen);
                dCopy.addNetworkAddress(na);
                updateMaps(dCopy);
            }
        }

        /**
         * Add a device attachment point to the device with the given
         * link-layer address
         * @param mac the MAC address
         * @param swPort the {@link SwitchPortTuple} to add
         * @param lastSeen the new last seen timestamp
         */
        protected void addDevAttachmentPoint(long mac,
                                             SwitchPortTuple swPort,
                                             Date lastSeen) {
            addDevAttachmentPoint(mac, swPort.getSw(),
                                  swPort.getPort(), lastSeen);
        }

        /**
         * Add a device attachment point to the device with the given
         * link-layer address
         * @param mac the MAC address
         * @param sw The {@link IOFSwitch} for the new attachment point
         * @param port The port for the new attachment point
         * @param lastSeen the new last seen timestamp
         */
        protected void addDevAttachmentPoint(long mac,
                                             IOFSwitch sw,
                                             short port,
                                             Date lastSeen) {

            Device d = getDeviceByDataLayerAddr(mac);
            // Check if the attachment point is already there
            SwitchPortTuple swPort = new SwitchPortTuple(sw, port);
            DeviceAttachmentPoint dap = d.getAttachmentPoint(swPort);
            if (dap != null) {
                // The attachment point is already there
                dap.setLastSeen(lastSeen); // TODO
                return;
            }

            // new device attachment point for this device
            dap = new DeviceAttachmentPoint(swPort, lastSeen);
            Device dCopy = new Device(d);
            dCopy.addAttachmentPoint(dap);
            // Now add this updated device to the maps, which will replace
            // the old copy
            updateMaps(dCopy);
        }

        // ******************************************
        // Delete operations on the device attributes
        // ******************************************

        /**
         * Delete an attachment point from a device
         * @param d the device
         * @param swPort the {@link SwitchPortTuple} to remove
         */
        protected void delDevAttachmentPoint(Device d, SwitchPortTuple swPort) {
            delDevAttachmentPoint(d, swPort.getSw(), swPort.getPort());
        }

        /**
         * Delete an attachment point from a device
         * @param d the device
         * @param sw the {@link IOFSwitch} for the attachment point to remove
         * @param port the port for the attachment point to remove
         */
        protected void delDevAttachmentPoint(Device d,
                                             IOFSwitch sw,
                                             short port) {

            // Check if the attachment point is there
            SwitchPortTuple swPort = new SwitchPortTuple(sw, port);
            if (d.getAttachmentPoint(swPort) == null) {
                // The attachment point is NOT there
                return;
            }

            // Make a copy of this device
            Device dCopy = new Device(d);
            DeviceAttachmentPoint dap = dCopy.removeAttachmentPoint(swPort);
            // Remove the original device from the Switch-port map
            // This is a no-op when this fn is called from removeSwPort
            // as the switch-pot itself would be deleted from the map 
            delFromSwitchPortDeviceMap(swPort, d);
            // Remove the original device from the Switch map
            delFromSwitchDeviceMap(sw, d);  
            // Now add this updated device to the maps, which will replace
            // the old copy
            updateMaps(dCopy);
            log.debug("Device 1 {}", d);
            log.debug("Device 2 {}", dCopy);
            removeAttachmentPointFromStorage(d, dap);
            d = null; // to catch if anyone is using this reference
        }

        /**
         * Add a new pending attachment point to the unresolved attachment
         * point map
         * @param dpid the DPID of the switch for the attachment point
         * @param mac the MAC address of the device
         * @param port the port for the attachment point
         * @param lastSeen the last seen timestamp
         */
        protected void updateSwitchUnresolvedAPMap(long dpid,
                                                   long mac,
                                                   short port,
                                                   Date lastSeen) {

            PendingAttachmentPoint pap = new PendingAttachmentPoint();
            pap.mac        = mac;
            pap.switchDpid = dpid;
            pap.switchPort = port;
            pap.lastSeen   = lastSeen;

            List<PendingAttachmentPoint> papl = 
                devMgrMaps.switchUnresolvedAPMap.get(dpid);
            if (papl == null) {
                devMgrMaps.switchUnresolvedAPMap.put(dpid, 
                        papl = Collections.synchronizedList(new ArrayList<PendingAttachmentPoint>()));
            }
            papl.add(pap);
        }
    } // End of DevMgrMap class definition

    private DevMgrMaps devMgrMaps;

    public DevMgrMaps getDevMgrMaps() {
        return devMgrMaps;
    }

    protected Set<IDeviceManagerAware> deviceManagerAware;
    protected ReentrantReadWriteLock lock;
    protected volatile boolean shuttingDown = false;

    protected ITopology topology;
    protected LinkedList<Update> updates;
    protected IStorageSource storageSource;

    protected Runnable deviceAgingTimer;
    protected SingletonTask deviceUpdateTask;
    protected Date previousStorageAudit;

    protected final static int DEVICE_MAX_AGE    = 60 * 60 * 24;
    protected final static int DEVICE_NA_MAX_AGE = 60 * 60 *  2;
    protected final static int DEVICE_AP_MAX_AGE = 60 * 60 *  2;

    // Constants for accessing storage
    // Table names
    private static final String DEVICE_TABLE_NAME = "controller_host";
    private static final String DEVICE_ATTACHMENT_POINT_TABLE_NAME = 
                                            "controller_hostattachmentpoint";
    private static final String DEVICE_NETWORK_ADDRESS_TABLE_NAME = 
                                            "controller_hostnetworkaddress";
    // Column names for the host table
    private static final String MAC_COLUMN_NAME       = "mac"; 
    private static final String VLAN_COLUMN_NAME      = "vlan"; 
    // Column names for both the attachment point and network address tables
    private static final String ID_COLUMN_NAME        = "id";
    private static final String DEVICE_COLUMN_NAME    = "host_id";
    private static final String LAST_SEEN_COLUMN_NAME = "last_seen";
    // Column names for the attachment point table
    private static final String SWITCH_COLUMN_NAME    = "switch_id"; 
    private static final String PORT_COLUMN_NAME      = "inport";
    private static final String AP_STATUS_COLUMN_NAME = "status";
    // Column names for the network address table
    private static final String NETWORK_ADDRESS_COLUMN_NAME = "ip";

    protected enum UpdateType {
        ADDED, REMOVED, MOVED, ADDRESS_ADDED, ADDRESS_REMOVED, VLAN_CHANGED
    }

    /**
     * Used internally to feed update queue for IDeviceManagerAware listeners
     */
    protected class Update {
        public Device device;
        public IOFSwitch oldSw;
        public Short oldSwPort;
        public IOFSwitch sw;
        public Short swPort;
        public DeviceNetworkAddress address;
        public UpdateType updateType;

        public Update(UpdateType type) {
            this.updateType = type;
        }
    }

    protected class PendingAttachmentPoint {
        long mac;
        long switchDpid;
        short switchPort;
        Date lastSeen;
        public long getMac() {
            return mac;
        }
        public long getSwitchDpid() {
            return switchDpid;
        }
        public short getSwitchPort() {
            return switchPort;
        }
        public Date getLastSeen() {
            return lastSeen;
        }
    }
    
    public DeviceManagerImpl() {
        this.devMgrMaps = new DevMgrMaps();
        this.lock = new ReentrantReadWriteLock();
        this.updates = new LinkedList<Update>();
    }

    public void startUp() {
        ScheduledExecutorService ses = floodlightProvider.getScheduledExecutor();
        deviceUpdateTask = new SingletonTask(ses, new DeviceUpdateWorker());

        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        floodlightProvider.addOFSwitchListener(this);

        /*
         * Device and storage aging.
         */
        enableDeviceAgingTimer();

        // Read all our device state (MACs, IPs, attachment points) from storage
        readAllDeviceStateFromStorage();
    }

    public void shutDown() {
        shuttingDown = true;
        floodlightProvider.removeOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.removeOFMessageListener(OFType.PORT_STATUS, this);
        floodlightProvider.removeOFSwitchListener(this);
        deviceAgingTimer = null;
     }

    @Override
    public String getName() {
        return "devicemanager";
    }

    @Override
    public int getId() {
        return FlListenerID.DEVICEMANAGERIMPL;
    }

    /**
     * Used in processPortStatus to check if the event is delete or shutdown
     * of a switch-port
     * @param ps
     * @return
     */
    private boolean isPortStatusDelOrModify(OFPortStatus ps) {
        boolean isDelete = 
            ((byte)OFPortReason.OFPPR_DELETE.ordinal() == ps.getReason());
        boolean isModify =
            ((byte)OFPortReason.OFPPR_MODIFY.ordinal() == ps.getReason());
        boolean isNotActive =    
            (((OFPortConfig.OFPPC_PORT_DOWN.getValue() & 
                    ps.getDesc().getConfig()) > 0) ||
            ((OFPortState.OFPPS_LINK_DOWN.getValue() & 
                    ps.getDesc().getState()) > 0));
        return (isDelete || (isModify && isNotActive));
    }

    public Command processPortStatusMessage(IOFSwitch sw, OFPortStatus ps) {
        // if ps is a delete, or a modify where the port is down or 
        // configured down
        if (isPortStatusDelOrModify(ps)) {
            SwitchPortTuple id = new SwitchPortTuple(sw, 
                                            ps.getDesc().getPortNumber());
            lock.writeLock().lock();
            try {
                devMgrMaps.removeSwPort(id);
            } finally {
                lock.writeLock().unlock();
            }
        }
        return Command.CONTINUE;
    }

    private void handleNewDevice(byte[] mac, Date currentDate, 
            SwitchPortTuple swPort, int nwAddr, Short vlan) {
        // Create the new device with attachment point and network address
        Device device = new Device(mac, currentDate);
        device.addAttachmentPoint(swPort, currentDate);
        device.addNetworkAddress(nwAddr, currentDate);
        device.setVlanId(vlan);
        // Update the maps - insert the device in the maps
        devMgrMaps.updateMaps(device);
        writeDeviceToStorage(device, currentDate);
        updateStatus(device, true);
        log.debug("New device learned {}", device);
    }

    private boolean isGratArp(Ethernet eth) {
        if (eth.getPayload() instanceof ARP) {
            ARP arp = (ARP) eth.getPayload();
            if (arp.isGratuitous()) {
                return true;
            }
        }
        return false;
    }

    private int getSrcNwAddr(Ethernet eth, long dlAddr) {
        if (eth.getPayload() instanceof ARP) {
            ARP arp = (ARP) eth.getPayload();
            if ((arp.getProtocolType() == ARP.PROTO_TYPE_IP)
                && (Ethernet.toLong(arp.getSenderHardwareAddress()) ==dlAddr)) {
                return IPv4.toIPv4Address(arp.getSenderProtocolAddress());
            }
        } else if (eth.getPayload() instanceof IPv4) {
            IPv4 ipv4 = (IPv4) eth.getPayload();
            if (ipv4.getPayload() instanceof UDP) {
                UDP udp = (UDP)ipv4.getPayload();
                if (udp.getPayload() instanceof DHCP) {
                    DHCP dhcp = (DHCP)udp.getPayload();
                    if (dhcp.getOpCode() == DHCP.OPCODE_REPLY) {
                        return ipv4.getSourceAddress();
                    }
                }
            }
        }
        return 0;
    }

    /**
     * This method is called for every packet-in and should be optimized for
     * performance.
     * @param sw
     * @param pi
     * @param cntx
     * @return
     */
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, 
                                          FloodlightContext cntx) {
        Command ret = Command.CONTINUE;
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());

        // Create attachment point/update network address if required
        SwitchPortTuple switchPort = new SwitchPortTuple(sw, pi.getInPort());
        // Don't learn from internal port or invalid port
        if (topology.isInternal(switchPort) || 
                !isValidInputPort(switchPort.getPort())) {
            processUpdates();
            return Command.CONTINUE;
        }

        // Packet arrive at a port from where we can learn the device
        // if the source is multicast/broadcast ignore it
        if ((match.getDataLayerSource()[0] & 0x1) != 0) {
            return Command.CONTINUE;
        }

        Long dlAddr = Ethernet.toLong(match.getDataLayerSource());
        Short vlan = match.getDataLayerVirtualLan();
        if (vlan < 0) vlan = null;
        Ethernet eth = IFloodlightProvider.bcStore.get(
                                cntx, IFloodlightProvider.CONTEXT_PI_PAYLOAD);
        int nwSrc = getSrcNwAddr(eth, dlAddr);
        Device device = devMgrMaps.getDeviceByDataLayerAddr(dlAddr);
        Date currentDate = new Date(); // TODO,
        if (device != null) { 
            // Write lock is expensive, check if we have an update first
            boolean newAttachmentPoint = false;
            boolean newNetworkAddress = false;
            boolean updateAttachmentPointLastSeen = false;
            boolean updateNetworkAddressLastSeen = false;
            boolean updateDeviceVlan = false;
            boolean clearAttachmentPoints = false;
            boolean updateDevice = false;

            DeviceAttachmentPoint attachmentPoint = null;
            DeviceNetworkAddress networkAddress = null;

            // Copy-replace of device would be too expensive here
            device.setLastSeen(currentDate);
            updateDevice = device.shouldWriteLastSeenToStorage();
            attachmentPoint = device.getAttachmentPoint(switchPort);

            if (isGratArp(eth)) {
                clearAttachmentPoints = true;
            }

            if (attachmentPoint != null) {
                updateAttachmentPointLastSeen = true;
            } else {
                newAttachmentPoint = true;
            }

            if (nwSrc != 0) {
                networkAddress = device.getNetworkAddress(nwSrc);
                if (networkAddress != null) {
                    updateNetworkAddressLastSeen = true;
                } else {
                    networkAddress = new DeviceNetworkAddress(nwSrc, 
                                                                currentDate);
                    newNetworkAddress = true;
                }
            }
            if ((vlan == null && device.getVlanId() != null) ||
                    (vlan != null && !vlan.equals(device.getVlanId()))) {
                updateDeviceVlan = true;
            }

            if (newAttachmentPoint || newNetworkAddress || updateDeviceVlan) {

                Device nd = new Device(device);

                try {
                    // Check if we have seen this attachmentPoint recently,
                    // An exception is thrown if the attachmentPoint is blocked.
                    if (newAttachmentPoint) {
                        attachmentPoint = getNewAttachmentPoint(nd, switchPort);
                        nd.addAttachmentPoint(attachmentPoint);
                    }

                    if (clearAttachmentPoints) {
                        nd.clearAttachmentPoints();
                    }

                    if (newNetworkAddress) {
                        // add the address
                        nd.addNetworkAddress(networkAddress);
                        log.debug("Device {} added IP {}", 
                                            nd, IPv4.fromIPv4Address(nwSrc));
                    }
                    if (updateDeviceVlan) {
                        nd.setVlanId(vlan);
                        writeDeviceToStorage(nd, currentDate);
                    }

                } catch (APBlockedException e) {
                    assert(attachmentPoint == null);
                    ret = Command.STOP;
                    // install drop flow to avoid overloading the controller
                    // set hard timeout to 5 seconds to avoid blocking host 
                    // forever
                    ForwardingBase.blockHost(floodlightProvider, switchPort,
                        dlAddr, ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT);
                }
                // Update the maps
                devMgrMaps.updateMaps(nd);
                // publish the update after devMgrMaps is updated.
                if (newNetworkAddress) {
                    updateAddress(nd, networkAddress, true);
                }
                if (updateDeviceVlan) {
                    updateVlan(nd);
                }
                device = nd;
            }

            if (updateAttachmentPointLastSeen) {
                attachmentPoint.setLastSeen(currentDate);
                if (attachmentPoint.shouldWriteLastSeenToStorage())
                    writeAttachmentPointToStorage(
                            device, attachmentPoint, currentDate);
            }

            if (updateNetworkAddressLastSeen || newNetworkAddress) {
                if (updateNetworkAddressLastSeen)
                    networkAddress.setLastSeen(currentDate);
                if (newNetworkAddress || 
                        networkAddress.shouldWriteLastSeenToStorage())
                    writeNetworkAddressToStorage(
                            device, networkAddress, currentDate);
            }

            if (updateDevice) {
                writeDeviceToStorage(device, currentDate);
            }

        } else { // device is null 
            handleNewDevice(match.getDataLayerSource(), currentDate,
                    switchPort, nwSrc, vlan);
        }
        processUpdates();
        return ret;
    }

    /**
     * Check if there exists a state of attachment point flapping.
     * If not, we return a new attachment point (or resurrect an old one).
     * If swPort is blocked for this device, throw an exception.
     * 
     * This must be called with write lock held.
     */
    private DeviceAttachmentPoint getNewAttachmentPoint(Device device, 
                                                        SwitchPortTuple swPort)
            throws APBlockedException {
        Date currentDate = new Date();

        // First, check if we have an existing attachment point
        DeviceAttachmentPoint curAttachmentPoint = null;
        IOFSwitch newSwitch = swPort.getSw();
        for (DeviceAttachmentPoint existingAttachmentPoint: 
                                                device.getAttachmentPoints()) {
            IOFSwitch existingSwitch = 
                            existingAttachmentPoint.getSwitchPort().getSw();
            if ((newSwitch == existingSwitch) || ((topology != null) &&
                    topology.inSameCluster(newSwitch, existingSwitch))) {
                curAttachmentPoint = existingAttachmentPoint;
                break;
            }
        }

        // Do we have an old attachment point?
        DeviceAttachmentPoint attachmentPoint = 
                                    device.getOldAttachmentPoint(swPort);
        if (attachmentPoint == null) {
            attachmentPoint = new DeviceAttachmentPoint(swPort, currentDate);
        } else {
            attachmentPoint.setLastSeen(currentDate);
            if (attachmentPoint.isBlocked()) {
                // If curAttachmentPoint exists and active, drop the packet
                if (curAttachmentPoint != null &&
                    currentDate.getTime() - 
                    curAttachmentPoint.getLastSeen().getTime() < 600000) {
                    throw new APBlockedException("Attachment point is blocked");
                }
                log.info("Unblocking {} for device {}",
                         attachmentPoint.getSwitchPort(), device);
                attachmentPoint.setBlocked(false);
            }
            // Remove from old list
            device.removeOldAttachmentPoint(attachmentPoint);
        }

        // Update mappings   
        devMgrMaps.addDevAttachmentPoint(
                device.getDataLayerAddressAsLong(), swPort, currentDate);

        // If curAttachmentPoint exists, we mark it a conflict and may block it.
        if (curAttachmentPoint != null) {
            curAttachmentPoint.setConflict(currentDate);
            device.removeAttachmentPoint(curAttachmentPoint);
            device.addOldAttachmentPoint(curAttachmentPoint);
            if (curAttachmentPoint.isFlapping()) {
                curAttachmentPoint.setBlocked(true);
                writeAttachmentPointToStorage(device, curAttachmentPoint, 
                                                                currentDate);
                log.warn(
                    "Device {}: flapping between {} and {}, block the latter",
                    new Object[] {device, swPort, 
                                        curAttachmentPoint.getSwitchPort()});
            } else {
                removeAttachmentPointFromStorage(device, curAttachmentPoint);
            }

            updateMoved(device, curAttachmentPoint.getSwitchPort(), 
                                                            attachmentPoint);

            if (log.isDebugEnabled()) {
                log.debug("Device {} moved from {} to {}", new Object[] {
                           device, curAttachmentPoint.getSwitchPort(), swPort});
            }
        } else {
            updateStatus(device, true);
            log.debug("Device {} added {}", device, swPort);
        }

        writeAttachmentPointToStorage(device, attachmentPoint, currentDate);
        return attachmentPoint;
    }

    private boolean isValidInputPort(Short port) {
        // Not a physical port. We should not 'discover' attachment points where
        return ((int)port.shortValue() & 0xff00) != 0xff00 || 
                     port.shortValue() == (short)0xfffe;
    }

    /**
     * Removes the specified device from data layer and network layer maps.
     * Does NOT remove the device from switch and switch:port level maps.
     * Must be called from within a write lock.
     * @param device
     */
    protected void delDevice(Device device) {
        devMgrMaps.delFromMaps(device);
        removeDeviceDiscoveredStateFromStorage(device);
        updateStatus(device, false);
        if (log.isDebugEnabled()) {
            log.debug("Removed device {}", device);
        }
        processUpdates();
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
            case PORT_STATUS:
                return this.processPortStatusMessage(sw, (OFPortStatus) msg);
        }

        log.error("received an unexpected message {} from switch {}", msg, sw);
        return Command.CONTINUE;
    }

    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    /**
     * @param topology the topology to set
     */
    public void setTopology(ITopology topology) {
        this.topology = topology;
    }

    @Override
    public Device getDeviceByDataLayerAddress(byte[] address) {
        return getDeviceByDataLayerAddress(Ethernet.toLong(address));
    }

    @Override
    public Device getDeviceByDataLayerAddress(long address) {
        return this.devMgrMaps.dataLayerAddressDeviceMap.get(address);
    }

    @Override
    public Device getDeviceByIPv4Address(Integer address) {
        lock.readLock().lock();
        try {
            return this.devMgrMaps.ipv4AddressDeviceMap.get(address);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void invalidateDeviceAPsByIPv4Address(Integer address) {
        lock.readLock().lock();
        try {
            Device d = this.devMgrMaps.ipv4AddressDeviceMap.get(address);
            if (d!= null && d.getAttachmentPoints() != null) {
                d.getAttachmentPoints().clear();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Device> getDevices() {
        lock.readLock().lock();
        try {
            return new ArrayList<Device>(
                    this.devMgrMaps.dataLayerAddressDeviceMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void addedSwitch(IOFSwitch sw) {
        // Fix up attachment points related to the switch
        lock.writeLock().lock();
        try {
            Long swDpid = sw.getId();
            List<PendingAttachmentPoint> papl = 
                devMgrMaps.switchUnresolvedAPMap.get(swDpid);
            if (papl != null) {
                for (PendingAttachmentPoint pap : papl) {
                    Device d = devMgrMaps.getDeviceByDataLayerAddr(pap.mac);
                    if (d == null) continue;

                    // Add attachment point
                    devMgrMaps.addDevAttachmentPoint(
                            pap.mac, sw, pap.switchPort, pap.lastSeen);
                }
                devMgrMaps.switchUnresolvedAPMap.remove(swDpid);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removedSwitch (IOFSwitch sw) {
        // remove all devices attached to this switch
        if (!devMgrMaps.isSwitchPresent(sw)) {
            // Switch not present
            return;
        }
        lock.writeLock().lock();
        try {
            devMgrMaps.delSwitchfromMaps(sw);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void addedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
            IOFSwitch dstSw, short dstPort, int dstPortState)
    {
        updatedLink(srcSw, srcPort, srcPortState, dstSw, dstPort, dstPortState);
    }

    @Override
    public void updatedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
            IOFSwitch dstSw, short dstPort, int dstPortState)
    {
        if (((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) != 
                    OFPortState.OFPPS_STP_BLOCK.getValue()) &&
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) != 
                    OFPortState.OFPPS_STP_BLOCK.getValue())) {
            // Remove all devices living on this switch:port now that it is 
            // internal
            SwitchPortTuple switchPort = new SwitchPortTuple(dstSw, dstPort);
            lock.writeLock().lock();
            try {
                devMgrMaps.removeSwPort(switchPort);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public void removedLink(IOFSwitch src, short srcPort, IOFSwitch dst, 
                                                                short dstPort)
    {
    }

    /**
     * @param deviceManagerAware the deviceManagerAware to set
     */
    public void setDeviceManagerAware(Set<IDeviceManagerAware> 
                                                        deviceManagerAware) {
        this.deviceManagerAware = deviceManagerAware;
    }

    public void setStorageSource(IStorageSource storageSource) {
        this.storageSource = storageSource;
        storageSource.createTable(DEVICE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        DEVICE_TABLE_NAME, MAC_COLUMN_NAME);
        storageSource.createTable(DEVICE_ATTACHMENT_POINT_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        DEVICE_ATTACHMENT_POINT_TABLE_NAME, ID_COLUMN_NAME);
        storageSource.createTable(DEVICE_NETWORK_ADDRESS_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        DEVICE_NETWORK_ADDRESS_TABLE_NAME, ID_COLUMN_NAME);
    }

    /**
     * Process device manager aware updates.  Call without any lock held
     */
    protected void processUpdates() {
        synchronized (updates) {
            Update update = null;
            while ((update = updates.poll()) != null) {
                log.debug ("processUpdates, update: {}", update);
                if (deviceManagerAware == null) 
                    continue;
                for (IDeviceManagerAware dma : deviceManagerAware) {
                    switch (update.updateType) {
                        case ADDED:
                            dma.deviceAdded(update.device);
                            break;
                        case REMOVED:
                            dma.deviceRemoved(update.device);
                            break;
                        case MOVED:
                            dma.deviceMoved(update.device,
                                    update.oldSw,
                                    update.oldSwPort,
                                    update.sw, update.swPort);
                            break;
                        case ADDRESS_ADDED:
                            dma.deviceNetworkAddressAdded(update.device, 
                                    update.address);
                            break;
                        case ADDRESS_REMOVED:
                            dma.deviceNetworkAddressRemoved(update.device, 
                                    update.address);
                            break;
                        case VLAN_CHANGED:
                            dma.deviceVlanChanged(update.device);
                            break;
                    }
                }
            }
        }
    }

    /**
     * Puts an update in queue for the Device.  Must be called from within the
     * write lock.
     * @param device
     * @param added
     */
    protected void updateStatus(Device device, boolean added) {
        synchronized (updates) {
            Update update;
            if (added) {
                update = new Update(UpdateType.ADDED);
            } else {
                update = new Update(UpdateType.REMOVED);
            }
            update.device = device;
            this.updates.add(update);
        }
    }

    /**
     * Puts an update in queue to indicate the Device moved.  Must be called
     * from within the write lock.
     * @param device The device that has moved.
     * @param oldPort The old switchport
     * @param newDap The new attachment point
     */
    protected void updateMoved(Device device, SwitchPortTuple oldSwPort, 
                                                DeviceAttachmentPoint newDap) {
        // We also clear the attachment points on other islands
        device.clearAttachmentPoints();
        device.addAttachmentPoint(newDap);
        
        synchronized (updates) {
            Update update = new Update(UpdateType.MOVED);
            update.device = device;
            update.oldSw = oldSwPort.getSw();
            update.oldSwPort = oldSwPort.getPort();
            update.sw = newDap.getSwitchPort().getSw();
            update.swPort = newDap.getSwitchPort().getPort();
            this.updates.add(update);
        }
    }

    protected void updateAddress(Device device, DeviceNetworkAddress address, 
                                                            boolean added) {
        synchronized (updates) {
            Update update;
            if (added) {
                update = new Update(UpdateType.ADDRESS_ADDED);
            } else {
                update = new Update(UpdateType.ADDRESS_REMOVED);
            }
            update.device = device;
            update.address = address;
            this.updates.add(update);
        }
    }

    protected void updateVlan(Device device) {
        synchronized (updates) {
            Update update = new Update(UpdateType.VLAN_CHANGED);
            update.device = device;
            this.updates.add(update);
        }
    }

    /**
     * Iterates through all devices and cleans up attachment points
     */
    public void clusterMerged() {
        deviceUpdateTask.reschedule(10, TimeUnit.MILLISECONDS);
    }

    /**
     * Removes any attachment points that are in the same 
     * {@link net.floodlightcontroller.topology.SwitchCluster SwitchCluster} 
     * @param The device to update the attachment points
     */
    public void cleanupAttachmentPoints(Device d) {
        // The long here is the SwitchCluster ID
        Map<Long, DeviceAttachmentPoint> map = 
                            new HashMap<Long, DeviceAttachmentPoint>();

        // Get only the latest DAPs into a map
        for (DeviceAttachmentPoint dap : d.getAttachmentPoints()) {
            if (DeviceAttachmentPoint.isNotNull(dap)) {
                long clusterId = 
                            dap.getSwitchPort().getSw().getSwitchClusterId();
                if (map.containsKey(clusterId)) {
                    // We compare to see which one is newer, move attachment 
                    // point to "old" list.
                    // They are removed after deleting from storage.
                    DeviceAttachmentPoint value = map.get(clusterId);
                    if (dap.getLastSeen().after(value.getLastSeen()) && 
                            !topology.isInternal(dap.getSwitchPort())) {
                        map.put(clusterId, dap);
                        d.addOldAttachmentPoint(value); // on copy of device
                    }
                } else {
                    map.put(clusterId, dap);
                }
            }
        }

        synchronized (d) {
            // Since the update below is happening on a copy of the device it
            // should not impact packetIn processing time due to lock contention
            d.setAttachmentPoints(map.values());
        }
    }

    public void clearAllDeviceStateFromMemory() {
        devMgrMaps.clearMaps();
    }

    private Date ageBoundaryDifference(Date currentDate, long expire) {
        if (expire == 0) {
            return new Date(0);
        }
        return new Date(currentDate.getTime() - 1000*expire);
    }

    // *********************
    // Storage Write Methods
    // *********************

    protected void writeDeviceToStorage(Device device, Date currentDate) {
        Map<String, Object> rowValues = new HashMap<String, Object>();
        String macString = device.getDlAddrString();
        rowValues.put(MAC_COLUMN_NAME, macString);
        if (device.getVlanId() != null)
            rowValues.put(VLAN_COLUMN_NAME, device.getVlanId());
        rowValues.put(LAST_SEEN_COLUMN_NAME, currentDate);
        storageSource.updateRowAsync(DEVICE_TABLE_NAME, rowValues);
        device.lastSeenWrittenToStorage(currentDate); // TODO

        for (DeviceAttachmentPoint attachmentPoint: 
                                            device.getAttachmentPoints()) {
            writeAttachmentPointToStorage(device, attachmentPoint, currentDate);
        }
        for (DeviceNetworkAddress networkAddress: 
                                            device.getNetworkAddresses()) {
            writeNetworkAddressToStorage(device, networkAddress, currentDate);
        }
    }

    protected void writeAttachmentPointToStorage(Device device,
            DeviceAttachmentPoint attachmentPoint, Date currentDate) {
        assert(device != null);
        assert(attachmentPoint != null);
        String deviceId = device.getDlAddrString();
        SwitchPortTuple switchPort = attachmentPoint.getSwitchPort();
        assert(switchPort != null);
        String switchId = switchPort.getSw().getStringId();
        Short port = switchPort.getPort();
        String attachmentPointId = 
                            deviceId + "-" + switchId + "-" + port.toString();

        Map<String, Object> rowValues = new HashMap<String, Object>();
        rowValues.put(ID_COLUMN_NAME, attachmentPointId);
        rowValues.put(DEVICE_COLUMN_NAME, deviceId);
        rowValues.put(SWITCH_COLUMN_NAME, switchId);
        rowValues.put(PORT_COLUMN_NAME, port);
        rowValues.put(LAST_SEEN_COLUMN_NAME, attachmentPoint.getLastSeen());
        String status = null;
        if (attachmentPoint.isBlocked())
            status = "blocked: duplicate mac";
        rowValues.put(AP_STATUS_COLUMN_NAME, status);

        storageSource.updateRowAsync(DEVICE_ATTACHMENT_POINT_TABLE_NAME, 
                                                                    rowValues);
        attachmentPoint.lastSeenWrittenToStorage(currentDate);
    }

    // **********************
    // Storage Remove Methods
    // **********************

    protected void removeAttachmentPointFromStorage(Device device, 
                                    DeviceAttachmentPoint attachmentPoint) {
        try {
            String deviceId = device.getDlAddrString();
            SwitchPortTuple switchPort = attachmentPoint.getSwitchPort();
            String switchId = switchPort.getSw().getStringId();
            Short port = switchPort.getPort();
            String attachmentPointId = 
                            deviceId + "-" + switchId + "-" + port.toString();
            storageSource.deleteRowAsync(
                        DEVICE_ATTACHMENT_POINT_TABLE_NAME, attachmentPointId);
        } catch (NullPointerException e) {
            log.debug("Null ptr exception for device {} attach-point {}",
                    device, attachmentPoint);
        }
    }

    protected void writeNetworkAddressToStorage(Device device,
            DeviceNetworkAddress networkAddress, Date currentDate) {
        assert(device != null);
        assert(networkAddress != null);
        String deviceId = device.getDlAddrString();
        String networkAddressString = IPv4.fromIPv4Address(
                                            networkAddress.getNetworkAddress());
        String networkAddressId = deviceId + "-" + networkAddressString;

        Map<String, Object> rowValues = new HashMap<String, Object>();
        rowValues.put(ID_COLUMN_NAME, networkAddressId);
        rowValues.put(DEVICE_COLUMN_NAME, deviceId);
        rowValues.put(NETWORK_ADDRESS_COLUMN_NAME, networkAddressString);
        rowValues.put(LAST_SEEN_COLUMN_NAME, networkAddress.getLastSeen());
        storageSource.updateRowAsync(DEVICE_NETWORK_ADDRESS_TABLE_NAME, 
                                                                    rowValues);
        networkAddress.lastSeenWrittenToStorage(currentDate);
    }

    protected void removeNetworkAddressFromStorage(Device device, 
                                        DeviceNetworkAddress networkAddress) {
        assert(device != null);
        assert(networkAddress != null);
        String deviceId = device.getDlAddrString();
        String networkAddressString = IPv4.fromIPv4Address(
                                            networkAddress.getNetworkAddress());
        String networkAddressId = deviceId + "-" + networkAddressString;
        storageSource.deleteRowAsync(DEVICE_NETWORK_ADDRESS_TABLE_NAME, 
                                                            networkAddressId);
    }

    // ********************
    // Storage Read Methods
    // ********************

    public boolean readAllDeviceStateFromStorage() {
        Date currentDate = new Date();
        try {
            // These methods MUST be called in this order
            readDevicesFromStorage(currentDate);
            readDeviceAttachmentPointsFromStorage(currentDate);
            readDeviceNetworkAddressesFromStorage(currentDate);
            return true;
        }
        catch (StorageException e) {
            log.error("Error reading device data from storage", e);
            return false;
        }
    }

    /**
     * Exclude any entries which are older than the 
     * @throws StorageException
     */
    private void readDevicesFromStorage(Date currentDate) 
                                                throws StorageException {
        String [] colNames = new String[] {MAC_COLUMN_NAME, 
                                    VLAN_COLUMN_NAME, LAST_SEEN_COLUMN_NAME};

        IResultSet deviceResultSet = storageSource.executeQuery(
                                    DEVICE_TABLE_NAME, colNames, null, null);

        while (deviceResultSet.next()) {
            Date lastSeen = deviceResultSet.getDate(LAST_SEEN_COLUMN_NAME);
            String macString = deviceResultSet.getString(MAC_COLUMN_NAME);
            assert(macString != null);
            if (macString == null) {                
                log.debug("Ignoring storage entry with no mac address");
                continue;
            }

            byte[] macBytes = HexString.fromHexString(macString);
            if (lastSeen == null) lastSeen = new Date();
            Device d = new Device(macBytes, lastSeen);
            if (deviceResultSet.containsColumn(VLAN_COLUMN_NAME)) {
                d.setVlanId(deviceResultSet.getShort(VLAN_COLUMN_NAME));
                if (d.getVlanId() < 0 || d.getVlanId() >= 4096) {
                    log.debug("Ignore storage entry with invalid vlan {}", d);
                    continue;
                }
            }
            devMgrMaps.updateMaps(d);
        }
    }

    private void readDeviceAttachmentPointsFromStorage(Date currentDate) 
        throws StorageException {

        String [] colNames = new String[] {
                DEVICE_COLUMN_NAME, SWITCH_COLUMN_NAME, 
                PORT_COLUMN_NAME, LAST_SEEN_COLUMN_NAME};

        IResultSet dapResultSet = storageSource.executeQuery(
                DEVICE_ATTACHMENT_POINT_TABLE_NAME, colNames, null, null);

        while (dapResultSet.next()) {
           Date lastSeen = dapResultSet.getDate(LAST_SEEN_COLUMN_NAME);
           if (lastSeen == null) lastSeen = new Date();

           String macString  = dapResultSet.getString(DEVICE_COLUMN_NAME);
           String dpidString = dapResultSet.getString(SWITCH_COLUMN_NAME);

           if (macString == null || dpidString == null)
               continue;

           long mac     = HexString.toLong(macString);
           long swDpid  = HexString.toLong(dpidString);
           IOFSwitch sw = floodlightProvider.getSwitches().get(swDpid);

           Integer port = dapResultSet.getIntegerObject(PORT_COLUMN_NAME);
           if (port == null || port > Short.MAX_VALUE) continue;

           if (sw == null) { // switch has not joined yet
               devMgrMaps.updateSwitchUnresolvedAPMap(
                       swDpid, mac, port.shortValue(), lastSeen);
           } else {
               devMgrMaps.addDevAttachmentPoint(
                                       mac, sw, port.shortValue(), lastSeen);
           }
        }
    }

    private void readDeviceNetworkAddressesFromStorage(Date currentDate) 
                                                    throws StorageException {

        String [] colNames = new String[]{  DEVICE_COLUMN_NAME, 
                                            NETWORK_ADDRESS_COLUMN_NAME, 
                                            LAST_SEEN_COLUMN_NAME};
        IResultSet dnaResultSet = storageSource.executeQuery(
                    DEVICE_NETWORK_ADDRESS_TABLE_NAME, colNames, null, null);

        while (dnaResultSet.next()) {
            Date lastSeen = dnaResultSet.getDate(LAST_SEEN_COLUMN_NAME);
            if (lastSeen == null) lastSeen = new Date();

            String macStr  = dnaResultSet.getString(DEVICE_COLUMN_NAME);
            String netaddr = dnaResultSet.getString(
                                                NETWORK_ADDRESS_COLUMN_NAME);

            if (macStr == null) {
                continue;
            }
            if (netaddr == null) {
                // If the key is in the database then ignore this record
                continue;
            }
            devMgrMaps.addNwAddrByDataLayerAddr(HexString.toLong(macStr), 
                                        IPv4.toIPv4Address(netaddr),
                                        lastSeen);
        }
    }

    public void removeDeviceDiscoveredStateFromStorage(Device device) {
        String deviceId = device.getDlAddrString();

        // Remove all of the attachment points
        storageSource.deleteRowsAsync(DEVICE_ATTACHMENT_POINT_TABLE_NAME,
                new OperatorPredicate(DEVICE_COLUMN_NAME, 
                        OperatorPredicate.Operator.EQ, deviceId));
        storageSource.deleteRowsAsync(DEVICE_NETWORK_ADDRESS_TABLE_NAME,
                new OperatorPredicate(DEVICE_COLUMN_NAME, 
                        OperatorPredicate.Operator.EQ, deviceId));

        // Remove the device
        storageSource.deleteRow(DEVICE_TABLE_NAME, deviceId);
    }

    // ********************
    // Device aging methods
    // ********************    

    private void removeAgedNetworkAddresses(Device device, Date currentDate) {
        Collection<DeviceNetworkAddress> addresses = 
                                                device.getNetworkAddresses();

        for (DeviceNetworkAddress address : addresses) {
            long expire = address.getExpire();

            if (expire == 0) {
                expire = DEVICE_NA_MAX_AGE;
            }
            Date agedBoundary = ageBoundaryDifference(currentDate, expire);

            if (address.getLastSeen().before(agedBoundary)) {
                device.removeNetworkAddress(address.getNetworkAddress());
            }
        }
    }

    private void removeAgedAttachmentPoints(Device device, Date currentDate) {
        Collection<DeviceAttachmentPoint> aps = device.getAttachmentPoints();

        for (DeviceAttachmentPoint ap : aps) {
            int expire = ap.getExpire();

            if (expire == 0) {
                expire = DEVICE_AP_MAX_AGE;
            }
            Date agedBoundary = ageBoundaryDifference(currentDate, expire);
            if (ap.getLastSeen().before(agedBoundary)) {
                device.removeAttachmentPoint(ap.getSwitchPort());
            }
        }
    }

    /**
     * Age device entry based on an expiry associated with the device.
     */
     private void removeAgedDevices(Date currentDate) {
        Date deviceAgeBoundary = ageBoundaryDifference(currentDate, 
                                                                DEVICE_MAX_AGE);

        Collection<Device> deviceColl = devMgrMaps.getDevices();
        for (Device device: deviceColl) {
            removeAgedNetworkAddresses(device, currentDate);
            removeAgedAttachmentPoints(device, currentDate);

            if ((device.getAttachmentPoints().size() == 0) &&
                (device.getNetworkAddresses().size() == 0) &&
                (device.getLastSeen().before(deviceAgeBoundary))) {
                delDevice(device);
            }
        } 
    }

    /**
     * Removes aged rows in a table, based on the tables LAST_SEEN_COLUMN_NAME,
     * requiring 'last_seen' to exist in the table.
     * 
     * @param tableName
     * @param aging
     * @param currentDate
     */
    private void removeAgedRowsFromStorage(String tableName,
                                           String hostIdFieldName,
                                           int aging,
                                           Date currentDate) {
        if (aging == 0) {
            return;
        }

        Date ageBoundary = ageBoundaryDifference(currentDate, DEVICE_MAX_AGE);
        IResultSet resultSet = null;
        try {
            /**
             * The reason this storage call is asynchronous even though it's 
             * immediately followed by a synchronous get is that there may be 
             * other queued up asynchronous storage operations that would affect
             * the results of executing this query. So we make this call 
             * asynchronous as well so that we see the affects of the previous 
             * asynchronous calls.
             */
            Future<IResultSet> future = 
                storageSource.executeQueryAsync(tableName, null,
                    new OperatorPredicate(LAST_SEEN_COLUMN_NAME, 
                            OperatorPredicate.Operator.LT, ageBoundary),null);
            // FIXME: What timeout should we use here?
            resultSet = future.get(30, TimeUnit.SECONDS);
            while (resultSet.next()) {

                String dlAddrStr = resultSet.getString(hostIdFieldName);
                if (dlAddrStr == null) {
                    continue;
                }

                long dlAddr = HexString.toLong(dlAddrStr);

                log.debug("removeRowsFromTable:" + hostIdFieldName + " " +
                   resultSet.getString(hostIdFieldName) + " " + dlAddr +
                   " " + resultSet.getDate(LAST_SEEN_COLUMN_NAME).toString() +
                   " " + currentDate.toString());

                lock.writeLock().lock();
                try {
                    devMgrMaps.delFromMaps(dlAddr);
                } finally {
                    lock.writeLock().unlock();
                }                
                resultSet.deleteRow();
            }
            resultSet.save();
            resultSet.close();
            resultSet = null;
        }
        catch (ExecutionException exc) {
            log.error("Error accessing storage to remove old devices", exc);
        }
        catch (InterruptedException exc) {
            log.error("Interruption accessing storage to remove old devices", 
                                                                        exc);
        }
        catch (TimeoutException exc) {
            log.warn("Timeout accessing storage to remove old devices", exc);
        }
        finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    /**
     * Expire all age-out managed state.  Not intended to be called
     * frequently since storage is queried.
     */
    private void removeAgedDeviceStorageState(Date currentDate) {
        removeAgedRowsFromStorage(DEVICE_TABLE_NAME,
                                  MAC_COLUMN_NAME,
                                  DEVICE_MAX_AGE, 
                                  currentDate);

        removeAgedRowsFromStorage(DEVICE_ATTACHMENT_POINT_TABLE_NAME,
                                  DEVICE_COLUMN_NAME,
                                  DEVICE_AP_MAX_AGE, 
                                  currentDate);

        removeAgedRowsFromStorage(DEVICE_NETWORK_ADDRESS_TABLE_NAME,
                                  DEVICE_COLUMN_NAME,
                                  DEVICE_NA_MAX_AGE,
                                  currentDate);
    }

    private void removeAgedDeviceState() {
        Date currentDate = new Date();
        long dayInMsec = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

        removeAgedDevices(currentDate);

        /*
         * Once a day review the storage state to expire very
         * old entries.
         */
        Date yesterday = new Date(currentDate.getTime() - dayInMsec);
        if ((previousStorageAudit == null) ||
            (previousStorageAudit.before(yesterday))) {
            previousStorageAudit = currentDate;
            removeAgedDeviceStorageState(currentDate);
        }
    }

    private static final int DEVICE_AGING_TIMER= 15; // in minutes
    private static final int DEVICE_AGING_TIMER_INTERVAL = 1; // in seconds

    /**
     * Create the deviceAgingTimer, which calls removeAgedDeviceState()
     * periodically.
     */
    private void enableDeviceAgingTimer() {
        if (deviceAgingTimer != null) {
            return;
        }

        deviceAgingTimer = new Runnable() {
            @Override
            public void run() {
                log.debug("Running device aging timer {} minutes",
                                DEVICE_AGING_TIMER);
                removeAgedDeviceState();

                if (deviceAgingTimer != null) {
                    ScheduledExecutorService ses =
                        floodlightProvider.getScheduledExecutor();
                    ses.schedule(this, DEVICE_AGING_TIMER, TimeUnit.MINUTES);
                }
            }
        };
        floodlightProvider.getScheduledExecutor().schedule(
            deviceAgingTimer, DEVICE_AGING_TIMER_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("topology"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    protected class DeviceUpdateWorker implements Runnable {   
        @Override
        public void run() {
            try { 
                log.debug("DeviceUpdateWorker: cleaning up attachment points");
                for (IOFSwitch sw  : devMgrMaps.getSwitches()) {
                    // If the set of devices connected to the switches we 
                    // re-iterate a max of 3 times - WHY? TODO
                    int maxIter = 3;
                    while (maxIter > 0) {
                        try {
                            for (Device d: devMgrMaps.getDevicesOnASwitch(sw)) {
                                Device dCopy = new Device(d);
                                cleanupAttachmentPoints(dCopy);
                                for (DeviceAttachmentPoint dap : 
                                            dCopy.getOldAttachmentPoints()) {
                                    // Don't remove conflict attachment points 
                                    // with recent activities
                                    if (dap.isInConflict())
                                        continue;
                                    // Delete from memory after storage, 
                                    // otherwise an exception will
                                    // leave stale attachment points on storage.
                                    removeAttachmentPointFromStorage(dCopy, dap);
                                    dCopy.removeOldAttachmentPoint(dap);
                                    // Update the maps with the new dev. copy
                                    devMgrMaps.updateMaps(dCopy);
                                }
                            }
                            maxIter = 0;
                        }  catch (ConcurrentModificationException e) {
                            maxIter--;
                        } catch (NullPointerException e) { }
                    }
                }
                log.debug("DeviceUpdateWorker: finished cleaning up device " +
                            "attachment points");
            } catch (StorageException e) {
                log.error("DeviceUpdateWorker had a storage exception, " +
                            "Floodlight exiting");
                System.exit(1);
            }
        }
    }

    @Override
    public void updatedSwitch(IOFSwitch sw) {
        if (sw.hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH)) {
            removedSwitch(sw);
        }
    }
}
