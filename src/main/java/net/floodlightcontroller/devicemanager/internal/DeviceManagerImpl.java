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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.DeviceAttachmentPoint;
import net.floodlightcontroller.devicemanager.DeviceNetworkAddress;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;
import net.floodlightcontroller.devicemanager.IDeviceManagerAware;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.SwitchPortTuple;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.EventHistory;
import net.floodlightcontroller.util.EventHistory.EvAction;

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
public class DeviceManagerImpl implements IDeviceManagerService, IOFMessageListener,
        IOFSwitchListener, ILinkDiscoveryListener, IFloodlightModule, IStorageSourceListener,
        ITopologyListener, IInfoProvider {

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
        private Map<String, String> portChannelMap;

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
            portChannelMap =
                new ConcurrentHashMap<String, String>();
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

        private void delFromIpv4AddressDeviceMap(Integer ip, Device d) {
            ipv4AddressDeviceMap.remove(ip);
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
        private synchronized void updateMaps(Device d) {
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
        protected void removeSwPort(SwitchPortTuple swPrt) {

            Map<Integer, Device> switchPortDevices = 
                                            switchPortDeviceMap.remove(swPrt);

            if (switchPortDevices == null) {
                return;
            }

            // Update the individual devices by updating its attachment points
            for (Device d : switchPortDevices.values()) {
                // Remove the device from the switch->device mapping
                delDevAttachmentPoint(d.getDataLayerAddressAsLong(), swPrt);
                evHistAttachmtPt(d.getDataLayerAddressAsLong(), swPrt, EvAction.REMOVED,
                                                        "SwitchPort removed");
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

        /**
         * Reinitialize portChannelMap upon config change
         */
        protected void clearPortChannelMap() {
            portChannelMap.clear();
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
         * Delete a network address from a device
         * @param dlAddr The link layer address of the device
         * @param nwAddr the new network layer address
         */
        protected void delNwAddrByDataLayerAddr(long dlAddr,
                                                int nwAddr) {
            Device d = getDeviceByDataLayerAddr(dlAddr);
            if (d == null) return;
            Device dCopy = new Device(d);
            DeviceNetworkAddress na = dCopy.getNetworkAddress(nwAddr);
            
            if (na != null) {
                delFromIpv4AddressDeviceMap(nwAddr, d);
                dCopy.removeNetworkAddress(na);
                updateMaps(dCopy);
                removeNetworkAddressFromStorage(d.getDlAddrString(), na);
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
        protected boolean addDevAttachmentPoint(long mac,
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
                return false;
            }

            // new device attachment point for this device
            dap = new DeviceAttachmentPoint(swPort, lastSeen);
            Device dCopy = new Device(d);
            dCopy.addAttachmentPoint(dap);
            // Now add this updated device to the maps, which will replace
            // the old copy
            updateMaps(dCopy);
            return true;
        }

        // ******************************************
        // Delete operations on the device attributes
        // ******************************************

        /**
         * Delete an attachment point from a device
         * @param d the device
         * @param swPort the {@link SwitchPortTuple} to remove
         */
        protected void delDevAttachmentPoint(long dlAddr, SwitchPortTuple swPort) {
            delDevAttachmentPoint(devMgrMaps.getDeviceByDataLayerAddr(dlAddr), 
                swPort.getSw(), swPort.getPort());
        }

        /**
         * Delete an attachment point from a device
         * @param d the device
         * @param sw the {@link IOFSwitch} for the attachment point to remove
         * @param port the port for the attachment point to remove
         */
        protected boolean delDevAttachmentPoint(Device d,
                                             IOFSwitch sw,
                                             short port) {

            // Check if the attachment point is there
            SwitchPortTuple swPort = new SwitchPortTuple(sw, port);
            if (d.getAttachmentPoint(swPort) == null) {
                // The attachment point is NOT there
                return false;
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
            if (log.isDebugEnabled()) {
                log.debug("Remove AP {} post {} prev {} for Device {}", 
                          new Object[] {dap, dCopy.getAttachmentPoints().size(),
                                        d.getAttachmentPoints().size(), dCopy});
            }
            removeAttachmentPointFromStorage(d.getDlAddrString(),
                    HexString.toHexString(dap.getSwitchPort().getSw().getId()),
                    dap.getSwitchPort().getPort().toString());
            d = null;
            return true;
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
                        papl = Collections.synchronizedList(
                                new ArrayList<PendingAttachmentPoint>()));
            }
            papl.add(pap);
        }
        
        /**
         * Add switch-port to port_channel mapping to portChannelMap
         * @param switch_id
         * @param port_no
         * @param port_channel
         */
        protected void addPortToPortChannel(String switch_id,
                            String port_name, String port_channel) {
            String swPort = switch_id + port_name;
            portChannelMap.put(swPort, port_channel);
        }
        
        /**
         * Check if two ports belong to the same port channel
         * @param swPort1
         * @param swPort2
         * @return
         */
        protected boolean inSamePortChannel(SwitchPortTuple swPort1,
                                        SwitchPortTuple swPort2) {
            IOFSwitch sw = swPort1.getSw();
            String portName = sw.getPort(swPort1.getPort()).getName();
            String key = sw.getStringId() + portName;
            String portChannel1 = portChannelMap.get(key);
            if (portChannel1 == null)
                return false;

            sw = swPort2.getSw();
            portName = sw.getPort(swPort2.getPort()).getName();
            key = sw.getStringId() + portName;
            String portChannel2 = portChannelMap.get(key);
            if (portChannel2 == null)
                return false;
            return portChannel1.equals(portChannel2);
        }
    } // End of DevMgrMap class definition

    private DevMgrMaps devMgrMaps;

    public DevMgrMaps getDevMgrMaps() {
        return devMgrMaps;
    }

    protected static Logger log = 
            LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected Set<IDeviceManagerAware> deviceManagerAware;
    protected LinkedList<Update> updates;
    protected ReentrantReadWriteLock lock;
    protected volatile boolean shuttingDown = false;
    protected volatile boolean portChannelConfigChanged = false;

    // Our dependencies
    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkDiscovery;
    protected ITopologyService topology;
    protected IStorageSourceService storageSource;
    protected IThreadPoolService threadPool;
    protected IRestApiService restApi;

    protected Runnable deviceAgingTimer;
    protected SingletonTask deviceUpdateTask;
    protected Date previousStorageAudit;

    protected static int DEVICE_MAX_AGE    = 60 * 60 * 24;
    protected static int DEVICE_NA_MAX_AGE = 60 * 60 *  2;
    protected static int DEVICE_AP_MAX_AGE = 60 * 60 *  2;
    protected static long NBD_TO_BD_TIMEDIFF_MS = 300000; // 5 minutes
    protected static long BD_TO_BD_TIMEDIFF_MS = 5000; // 5 seconds
    // This the amount of time that we need for a device to move from
    // a non-broadcast domain port to a broadcast domain port.

    // Constants for accessing storage
    // Table names
    private static final String DEVICE_TABLE_NAME = "controller_host";
    private static final String DEVICE_ATTACHMENT_POINT_TABLE_NAME = 
                                            "controller_hostattachmentpoint";
    private static final String DEVICE_NETWORK_ADDRESS_TABLE_NAME = 
                                            "controller_hostnetworkaddress";
    protected static final String PORT_CHANNEL_TABLE_NAME = "controller_portchannelconfig";
    
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
    // Column names for the port channel table
    protected static final String PC_ID_COLUMN_NAME = "id";
    protected static final String PORT_CHANNEL_COLUMN_NAME = "port_channel_id";
    protected static final String PC_SWITCH_COLUMN_NAME = "switch";
    protected static final String PC_PORT_COLUMN_NAME = "port";

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
        evHistAttachmtPt(mac, swPort, EvAction.ADDED, "New device");
        device.addNetworkAddress(nwAddr, currentDate);
        device.setVlanId(vlan);
        // Update the maps - insert the device in the maps
        devMgrMaps.updateMaps(device);
        writeDeviceToStorage(device, currentDate);
        updateStatus(device, true);
        if (log.isDebugEnabled()) {
            log.debug("New device learned {}", device);
        }
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
            return ipv4.getSourceAddress();
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

        Ethernet eth = IFloodlightProviderService.bcStore.get(
                    cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        /** If a broadcast or multicast packetIn comes from a port that's not allowed by higher
         *  level topology, it should be dropped.
         *  If it is an unicast packetIn, let it go through. This is the case when the broadcast
         *  domain where the unicast packet originated learned the dst device on the wrong port.
         *  Hopefully, the response from dst device will correct the learning of the BD.
         */
        short pinPort = pi.getInPort();
        long pinSw = sw.getId();
        if (topology.isAllowed(pinSw, pinPort) == false) {
            if (eth.getEtherType() == Ethernet.TYPE_BDDP ||
                (eth.isBroadcast() == false && eth.isMulticast() == false)) {
                return Command.CONTINUE;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("deviceManager: Stopping packet as it is coming" +
                            "in on a port blocked by higher layer on." +
                            "switch ={}, port={}", new Object[] {sw.getStringId(), pinPort});
                }
                return Command.STOP;
            }
        }

        Command ret = Command.CONTINUE;
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort(), sw.getId());
        // Add this packet-in to event history
        evHistPktIn(match);
        if (log.isDebugEnabled())
            log.debug("Entering packet_in processing sw {}, port {}. {} --> {}, type {}",
                      new Object[] { sw.getStringId(), pi.getInPort(), 
                               HexString.toHexString(match.getDataLayerSource()),
                               HexString.toHexString(match.getDataLayerDestination()),
                               match.getDataLayerType()
                      });

        /**
         * Drop all broadcast and multicast packets from not-allowed incoming broadcast ports
         */
        if ((eth.isBroadcast() || eth.isMulticast()) &&
            !topology.isIncomingBroadcastAllowed(pinSw, pinPort)) {
            if (log.isDebugEnabled()) {
                log.debug("Drop broadcast/multicast packets with src {} from not-allowed incoming broadcast ports {} {}",
                        new Object[] {HexString.toHexString(eth.getSourceMACAddress()),
                        HexString.toHexString(pinSw), pinPort});
            }
            return Command.STOP;
        }

        // Create attachment point/update network address if required
        SwitchPortTuple switchPort = new SwitchPortTuple(sw, pi.getInPort());
        // Don't learn from internal port or invalid port
        if (topology.isInternal(switchPort.getSw().getId(), switchPort.getPort()) || 
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
        int nwSrc = getSrcNwAddr(eth, dlAddr);
        Device device = devMgrMaps.getDeviceByDataLayerAddr(dlAddr);
        if (log.isTraceEnabled()) {
            long dstAddr = Ethernet.toLong(match.getDataLayerDestination());
            Device dstDev = devMgrMaps.getDeviceByDataLayerAddr(dstAddr);
            if (device != null)
                log.trace("    Src.AttachmentPts: {}", device.getAttachmentPointsMap().keySet());
            if (dstDev != null)
                log.trace("    Dst.AttachmentPts: {}", dstDev.getAttachmentPointsMap().keySet());
        }
        Date currentDate = new Date(); 
        if (device != null) { 
            // Write lock is expensive, check if we have an update first
            boolean newAttachmentPoint = false;
            boolean newNetworkAddress = false;
            boolean updateAttachmentPointLastSeen = false;
            boolean updateNetworkAddressLastSeen = false;
            boolean updateNeworkAddressMap = false;
            boolean updateDeviceVlan = false;
            boolean clearAttachmentPoints = false;
            boolean updateDevice = false;

            DeviceAttachmentPoint attachmentPoint = null;
            DeviceNetworkAddress networkAddress = null;

            // Copy-replace of device would be too expensive here
            device.setLastSeen(currentDate);
            updateDevice = device.shouldWriteLastSeenToStorage();
            
            if (isGratArp(eth)) {
                clearAttachmentPoints = true;
            }

            attachmentPoint = device.getAttachmentPoint(switchPort);
            if (attachmentPoint != null) {
                updateAttachmentPointLastSeen = true;
            } else {
                newAttachmentPoint = true;
                if ((eth.isBroadcast() || eth.isMulticast()) &&
                    topology.isIncomingBroadcastAllowed(pinSw, pinPort) == false) {
                    if (log.isDebugEnabled()) {
                        log.debug("Port {} {} is not allowed for incoming broadcast packet",
                                  HexString.toHexString(pinSw), pinPort);
                    }
                    newAttachmentPoint = false;
                }
            }

            if (nwSrc != 0) {
                networkAddress = device.getNetworkAddress(nwSrc);
                if (networkAddress != null) {
                    updateNetworkAddressLastSeen = true;
                } else if (eth != null && (eth.getPayload() instanceof ARP)) {
                    /** MAC-IP association should be learnt from both ARP request and reply.
                     *  Since a host learns some other host's mac to ip mapping after receiving 
                     *  an ARP request from the host.
                     *  
                     *  However, device's MAC-IP mapping could be wrong if a host sends a ARP request with
                     *  an IP other than its own. Unfortunately, there isn't an easy way to allow both learning
                     *  and prevent incorrect learning.
                     */
                    networkAddress = new DeviceNetworkAddress(nwSrc, 
                                                            currentDate);
                    newNetworkAddress = true;
                }

                // Also, if this address is currently mapped to a different 
                // device, fix it. This should be rare, so it is OK to do update
                // the storage from here.
                //
                // NOTE: the mapping is observed, and the decision is made based
                // on that, outside a lock. So the state may change by the time 
                // we get to do the map update. But that is OK since the mapping
                // will eventually get consistent.
                Device deviceByNwaddr = this.getDeviceByIPv4Address(nwSrc);
                if ((deviceByNwaddr != null) &&
                    (deviceByNwaddr.getDataLayerAddressAsLong() != 
                                    device.getDataLayerAddressAsLong())) {
                    updateNeworkAddressMap = true;
                    Device dCopy = new Device(deviceByNwaddr);
                    DeviceNetworkAddress naOld = dCopy.getNetworkAddress(nwSrc);
                    Map<Integer, DeviceNetworkAddress> namap = 
                                                dCopy.getNetworkAddressesMap();
                    if (namap.containsKey(nwSrc)) namap.remove(nwSrc);
                    dCopy.setNetworkAddresses(namap.values());
                    this.devMgrMaps.updateMaps(dCopy);
                    if (naOld !=null) 
                        removeNetworkAddressFromStorage(dCopy.getDlAddrString(), naOld);
                }

            }
            if ((vlan == null && device.getVlanId() != null) ||
                    (vlan != null && !vlan.equals(device.getVlanId()))) {
                updateDeviceVlan = true;
            }

            if (newAttachmentPoint || newNetworkAddress || updateDeviceVlan || 
                                updateNeworkAddressMap) {

                Device nd = new Device(device);

                try {
                    // Check if we have seen this attachmentPoint recently,
                    // An exception is thrown if the attachmentPoint is blocked.
                    if (newAttachmentPoint) {
                        attachmentPoint = getNewAttachmentPoint(nd, switchPort);
                        if (attachmentPoint == null) {
                            newAttachmentPoint = false;
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Learned new AP for device {} at {}",
                                        HexString.toHexString(nd.getDataLayerAddressAsLong()),
                                        attachmentPoint);
                            }
                            nd.addAttachmentPoint(attachmentPoint);
                            evHistAttachmtPt(nd.getDataLayerAddressAsLong(), 
                                             attachmentPoint.getSwitchPort(),
                                             EvAction.ADDED, 
                                             "New AP from pkt-in");
                        }
                    }

                    if (clearAttachmentPoints) {
                        nd.clearAttachmentPoints();
                        evHistAttachmtPt(nd, 0L, (short)(-1),
                                EvAction.CLEARED, "Grat. ARP from pkt-in");
                    }

                    if (newNetworkAddress) {
                        // add the address
                        nd.addNetworkAddress(networkAddress);
                        if (log.isTraceEnabled()) {
                            log.trace("Device {} added IP {}", 
                                      new Object[] {nd, IPv4.fromIPv4Address(nwSrc)});
                        }
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
            if (log.isTraceEnabled())
                log.trace("   new device");
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
        for (DeviceAttachmentPoint existingAttachmentPoint: 
                                                device.getAttachmentPoints()) {
            // if the two switches are in the same cluster
            long currSw = existingAttachmentPoint.getSwitchPort().getSw().getId();
            short currPort = existingAttachmentPoint.getSwitchPort().getPort();
            long newSw = swPort.getSw().getId();
            short newPort = swPort.getPort();
            long dt = currentDate.getTime() - existingAttachmentPoint.getLastSeen().getTime();
            if (topology.inSameCluster(currSw, newSw)) {
                if ((topology.isBroadcastDomainPort(currSw, currPort) == false) &&
                    (topology.isBroadcastDomainPort(newSw, newPort) == true)) {
                    if (dt < NBD_TO_BD_TIMEDIFF_MS) {
                        // if the packet was seen within the last 5 minutes, we should ignore.
                        // it should also ignore processing the packet.
                        if (log.isDebugEnabled()) {
                            log.debug("Surpressing too quick move of {} from non broadcast domain port {} {}" +
                                    " to broadcast domain port {} {}. Last seen on non-BD {} sec ago",
                                    new Object[] {device.getDlAddrString(),
                                                existingAttachmentPoint.getSwitchPort().getSw().getStringId(), currPort,
                                                swPort.getSw().getStringId(), newPort,
                                                dt/1000 }
                                    );
                        }
                        return null;
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("AP move of {} from non broadcast domain port {} {}" +
                                    " to broadcast domain port {} {}. Last seen on BD {} sec ago",
                                    new Object[] { device.getDlAddrString(),
                                                   existingAttachmentPoint.getSwitchPort().getSw().getStringId(), currPort,
                                                   swPort.getSw().getStringId(), newPort,
                                                   dt/1000 }
                                    );
                        }
                        curAttachmentPoint = existingAttachmentPoint;
                        break;
                    }
                } else if ((topology.isBroadcastDomainPort(currSw, currPort) == true) &&
                          (topology.isBroadcastDomainPort(newSw, newPort) == true)) {
                    if (topology.isInSameBroadcastDomain(currSw, currPort, newSw, newPort)) {
                        if (log.isDebugEnabled()) {
                            log.debug("new AP {} {} and current AP {} {} belong to the same broadcast domain",
                                    new Object[] {HexString.toHexString(newSw), newPort,
                                    HexString.toHexString(currSw), currPort});
                        }
                        return null;
                    }
                    if (dt < BD_TO_BD_TIMEDIFF_MS) {
                        // if the packet was seen within the last 5 seconds, we should ignore.
                        // it should also ignore processing the packet.
                        if (log.isDebugEnabled()) {
                            log.debug("Surpressing too quick move of {} from one broadcast domain port {} {}" +
                                    " to another broadcast domain port {} {}. Last seen on BD {} sec ago",
                                    new Object[] {device.getDlAddrString(),
                                                existingAttachmentPoint.getSwitchPort().getSw().getStringId(), currPort,
                                                swPort.getSw().getStringId(), newPort,
                                                dt/1000 }
                                    );
                        }
                        return null;
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("AP move of {} from one broadcast domain port {} {}" +
                                    " to another broadcast domain port {} {}. Last seen on BD {} sec ago",
                                    new Object[] { device.getDlAddrString(),
                                                   existingAttachmentPoint.getSwitchPort().getSw().getStringId(), currPort,
                                                   swPort.getSw().getStringId(), newPort,
                                                   dt/1000 }
                                    );
                        }
                        curAttachmentPoint = existingAttachmentPoint;
                        break;
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("AP move of {} from port {} {}" +
                                " to port {} {}. Last seen {} sec ago",
                                new Object[] { device.getDlAddrString(),
                                               existingAttachmentPoint.getSwitchPort().getSw().getStringId(), currPort,
                                               swPort.getSw().getStringId(), newPort,
                                               dt/1000 }
                                );
                    }
                    curAttachmentPoint = existingAttachmentPoint;
                    break;
                }
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
                // Attachment point is currently in blocked state
                // If curAttachmentPoint exists and active, drop the packet
                if (curAttachmentPoint != null &&
                    currentDate.getTime() - 
                    curAttachmentPoint.getLastSeen().getTime() < 600000) {
                    throw new APBlockedException("Attachment point is blocked");
                }
                log.info("Unblocking {} for device {}",
                         attachmentPoint.getSwitchPort(), device);
                attachmentPoint.setBlocked(false);
                evHistAttachmtPt(device.getDataLayerAddressAsLong(), swPort,
                    EvAction.UNBLOCKED, "packet-in after block timer expired");
            }
            // Remove from old list
            device.removeOldAttachmentPoint(attachmentPoint);
        }

        // Update mappings
        devMgrMaps.addDevAttachmentPoint(
                device.getDataLayerAddressAsLong(), swPort, currentDate);
        evHistAttachmtPt(device.getDataLayerAddressAsLong(), swPort,
                EvAction.ADDED, "packet-in GNAP");

        // If curAttachmentPoint exists, we mark it a conflict and may block it.
        if (curAttachmentPoint != null) {
            device.removeAttachmentPoint(curAttachmentPoint);
            device.addOldAttachmentPoint(curAttachmentPoint);
            // If two ports are in the same port-channel, we don't treat it
            // as conflict, but will forward based on the last seen switch-port
            if (!devMgrMaps.inSamePortChannel(swPort,
                    curAttachmentPoint.getSwitchPort())) {
                curAttachmentPoint.setConflict(currentDate);
                if (curAttachmentPoint.isFlapping()) {
                    curAttachmentPoint.setBlocked(true);
                    evHistAttachmtPt(device.getDataLayerAddressAsLong(),
                            curAttachmentPoint.getSwitchPort(),
                            EvAction.BLOCKED, "Conflict");
                    writeAttachmentPointToStorage(device, curAttachmentPoint,
                                                currentDate);
                    log.warn(
                        "Device {}: flapping between {} and {}, block the latter",
                        new Object[] {device, swPort, 
                        curAttachmentPoint.getSwitchPort()});
                    // Check if flapping is between the same switch port
                    if (swPort.getSw().getId() ==
                        curAttachmentPoint.getSwitchPort().getSw().getId() &&
                        swPort.getPort() ==
                        curAttachmentPoint.getSwitchPort().getPort()) {
                        log.warn("Fake flapping on port " + swPort.getPort() +
                            " between sw " + swPort.getSw() + " and " +
                            curAttachmentPoint.getSwitchPort().getSw());
                        device.removeOldAttachmentPoint(curAttachmentPoint);
                        removeAttachmentPointFromStorage(device.getDlAddrString(),
                            HexString.toHexString(curAttachmentPoint.getSwitchPort().getSw().getId()),
                            curAttachmentPoint.getSwitchPort().getPort().toString());
                    }
                } else {
                    removeAttachmentPointFromStorage(device.getDlAddrString(),
                        HexString.toHexString(curAttachmentPoint.getSwitchPort().getSw().getId()),
                        curAttachmentPoint.getSwitchPort().getPort().toString());
                    evHistAttachmtPt(device.getDataLayerAddressAsLong(),
                            curAttachmentPoint.getSwitchPort(),
                            EvAction.REMOVED, "Conflict");
                }
            }
            updateMoved(device, curAttachmentPoint.getSwitchPort(),
                                                            attachmentPoint);

            if (log.isDebugEnabled()) {
                log.debug("Device {} moved from {} to {}", new Object[] {
                           device.getDlAddrString(), curAttachmentPoint.getSwitchPort(), swPort});
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
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    /**
     * @param topology the topology to set
     */
    public void setTopology(ITopologyService topology) {
        this.topology = topology;
    }
    
    /**
     * @param topology the topology to set
     */
    public void setLinkDiscovery(ILinkDiscoveryService linkDiscovery) {
        this.linkDiscovery = linkDiscovery;
    }

    @Override
    public Device getDeviceByDataLayerAddress(byte[] address) {
        if (address.length != Ethernet.DATALAYER_ADDRESS_LENGTH) return null;
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
    public boolean isDeviceKnownToCluster(long deviceId, long switchId) {
        Device device = devMgrMaps.getDeviceByDataLayerAddr(deviceId);
        if (device == null) {
            return false;
        }
        /** 
         * Iterate through all APs and check if the switch clusterID matches
         * with the given clusterId
         */
        for(DeviceAttachmentPoint dap : device.getAttachmentPoints()) {
            if (dap == null) continue;
            if (topology.getSwitchClusterId(switchId) == 
                topology.getSwitchClusterId(dap.getSwitchPort().getSw().getId())) {
                    return true;
            }
        }
        return false;
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
        /**
         * No point to restore the old APs on the switch since
         * the hosts connecting to the switch will be discovered
         * by ARP if anyone wants to talk to the hosts.
         */
        // Fix up attachment points related to the switch
        /*
        lock.writeLock().lock();
        try {
            Long swDpid = sw.getId();
            List<PendingAttachmentPoint> papl = 
                devMgrMaps.getSwitchUnresolvedAPMap().get(swDpid);
            if (papl != null) {
                for (PendingAttachmentPoint pap : papl) {
                    Device d = devMgrMaps.getDeviceByDataLayerAddr(pap.mac);
                    if (d == null) continue;

                    // Add attachment point
                    devMgrMaps.addDevAttachmentPoint(
                            pap.mac, sw, pap.switchPort, pap.lastSeen);
                    evHistAttachmtPt(pap.mac,
                            sw.getId(), pap.switchPort,
                            EvAction.ADDED, "Switch Added");
                }
                devMgrMaps.getSwitchUnresolvedAPMap().remove(swDpid);
            }
        } finally {
            lock.writeLock().unlock();
        }*/
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

    public void addedOrUpdatedLink(long srcSw, short srcPort, int srcPortState,
            long dstSw, short dstPort, int dstPortState, ILinkDiscovery.LinkType type)
    {
        if (((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) != 
                    OFPortState.OFPPS_STP_BLOCK.getValue()) &&
            ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue()) != 
                    OFPortState.OFPPS_STP_BLOCK.getValue())) {
            // Remove all devices living on this switch:port now that it is 
            // internal
            SwitchPortTuple switchPort = new SwitchPortTuple(floodlightProvider.getSwitches().get(dstSw), dstPort);
            lock.writeLock().lock();
            try {
                devMgrMaps.removeSwPort(switchPort);
            } finally {
                lock.writeLock().unlock();
            }
        }
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
     * @param oldSwPort The old switchport
     * @param newDap The new attachment point
     */
    protected void updateMoved(Device device, SwitchPortTuple oldSwPort, 
                                                DeviceAttachmentPoint newDap) {
        // We also clear the attachment points on other islands
        device.clearAttachmentPoints();
        evHistAttachmtPt(device, 0L, (short)(-1), EvAction.CLEARED, "Moved");
        device.addAttachmentPoint(newDap);
        evHistAttachmtPt(device.getDataLayerAddressAsLong(), 
                newDap.getSwitchPort(), 
                EvAction.ADDED, "Moved");
        
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
    @Override
    public void topologyChanged() {
        deviceUpdateTask.reschedule(10, TimeUnit.MILLISECONDS);
    }

    protected boolean isNewer(DeviceAttachmentPoint dap1,
                            DeviceAttachmentPoint dap2) {
        // dap 1 is newer than dap 2 if
        // (1) if dap 1 is a non-broadcast domain attachment point
        //      (a) if (dap 2 is a non-broadcast domain attachment point
        //          and dap1.lastseen time is after dap2.last seentime
        //      OR
        //      (b) if (dap 2 is a braodcast domain attachment point
        //          and dap1.lastseen time is after (dap2.lastseen-5minutes)
        // (2) if dap 1 is a broadcast domain attachment point
        //      (a) if dap 2 is a non-broadcast attachment point and
        //          dap1.lastseen is after (dap2.lastseen + 5 minutes)
        //      OR
        //      (b) if dap2 is a broadcastdomain attachment point and
        //          dap2.lastseen

        SwitchPortTuple sp1, sp2;
        boolean sp1IsBDPort, sp2IsBDPort;
        sp1 = dap1.getSwitchPort();
        sp2 = dap2.getSwitchPort();

        sp1IsBDPort = topology.isBroadcastDomainPort(sp1.getSw().getId(),
                                               sp1.getPort());
        sp2IsBDPort = topology.isBroadcastDomainPort(sp2.getSw().getId(),
                                               sp2.getPort());
        long ls1 = dap1.getLastSeen().getTime();
        long ls2 = dap2.getLastSeen().getTime();

        if (sp1IsBDPort == false) {
            if (sp2IsBDPort == false) {
                return (ls1 > ls2);
            } else {
                return (ls1 > (ls2 - NBD_TO_BD_TIMEDIFF_MS));
            }
        } else {
            if (sp2IsBDPort == false) {
                return (ls1 > (ls2 + NBD_TO_BD_TIMEDIFF_MS));
            } else {
                return ((ls1 > ls2 + BD_TO_BD_TIMEDIFF_MS) ||
                        (ls1 < ls2 && ls1 > ls2 - BD_TO_BD_TIMEDIFF_MS));
            }
        }
    }
    /**
     * Removes any attachment points that are in the same
     * {@link net.floodlightcontroller.topology.SwitchCluster SwitchCluster}
     * @param d The device to update the attachment points
     */
    public void cleanupAttachmentPoints(Device d) {
        // The long here is the SwitchCluster ID
        Map<Long, DeviceAttachmentPoint> tempAPMap =
                            new HashMap<Long, DeviceAttachmentPoint>();
        Map<Long, DeviceAttachmentPoint> tempOldAPMap =
            new HashMap<Long, DeviceAttachmentPoint>();

        // Get only the latest DAPs into a map
        for (DeviceAttachmentPoint dap : d.getAttachmentPoints()) {
            if (DeviceAttachmentPoint.isNotNull(dap) &&
                            !topology.isInternal(dap.getSwitchPort().getSw().getId(), dap.getSwitchPort().getPort())) {
                long clusterId = topology.getSwitchClusterId(
                            dap.getSwitchPort().getSw().getId());
                // do not use attachment points if the attachment point is
                // not allowed by topology
                long swid = dap.getSwitchPort().getSw().getId();
                short port = dap.getSwitchPort().getPort();
                if (topology.isAllowed(swid, port) == false)
                    continue;

                if (tempAPMap.containsKey(clusterId)) {
                    // We compare to see which one is newer, move attachment 
                    // point to "old" list.
                    // They are removed after deleting from storage.

                    DeviceAttachmentPoint existingDap = tempAPMap.get(clusterId);
                    if (isNewer(dap, existingDap)) {
                        tempAPMap.put(clusterId, dap);
                        tempOldAPMap.put(clusterId, existingDap);
                    }
                } else {
                    tempAPMap.put(clusterId, dap);
                }
            }
        }

        synchronized (d) {
            /**
             * Since the update below is happening on a copy of the device it
             * should not impact packetIn processing time due to lock contention
             *
             * Also make sure the attachmentPoints are in non-blocked state
             */
            for (DeviceAttachmentPoint dap: tempAPMap.values()) {
            	dap.setBlocked(false);
            }
            d.setAttachmentPoints(tempAPMap.values());
            for (DeviceAttachmentPoint dap : tempOldAPMap.values()) {
            	dap.setBlocked(false);
                d.addOldAttachmentPoint(dap);
            }

            log.debug("After cleanup, device {}", d);
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
        device.lastSeenWrittenToStorage(currentDate);

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
                            deviceId + "|" + switchId + "|" + port.toString();

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

    protected void removeAttachmentPointFromStorage(String deviceId,
                                    String switchId, String port) {
        String attachmentPointId =
                        deviceId + "|" + switchId + "|" + port;
        try {
            storageSource.deleteRowAsync(
                        DEVICE_ATTACHMENT_POINT_TABLE_NAME, attachmentPointId);
        } catch (NullPointerException e) {
            log.warn("Null ptr exception for device {} on sw {} port {}",
                    new Object[] {deviceId, switchId, port});
        }
    }

    protected void writeNetworkAddressToStorage(Device device,
            DeviceNetworkAddress networkAddress, Date currentDate) {
        assert(device != null);
        assert(networkAddress != null);
        String deviceId = device.getDlAddrString();
        String networkAddressString = IPv4.fromIPv4Address(
                                            networkAddress.getNetworkAddress());
        String networkAddressId = deviceId + "|" + networkAddressString;

        if (networkAddress.getNetworkAddress() == 0) {
            log.error("Zero network address for device {}\n {}",
                device, Thread.currentThread().getStackTrace());
            return;
        }
        
        Map<String, Object> rowValues = new HashMap<String, Object>();
        rowValues.put(ID_COLUMN_NAME, networkAddressId);
        rowValues.put(DEVICE_COLUMN_NAME, deviceId);
        rowValues.put(NETWORK_ADDRESS_COLUMN_NAME, networkAddressString);
        rowValues.put(LAST_SEEN_COLUMN_NAME, networkAddress.getLastSeen());
        storageSource.updateRowAsync(DEVICE_NETWORK_ADDRESS_TABLE_NAME, 
                                                                    rowValues);
        networkAddress.lastSeenWrittenToStorage(currentDate);
    }

    protected void removeNetworkAddressFromStorage(String deviceId,
                                        DeviceNetworkAddress networkAddress) {
        assert(deviceId != null);
        assert(networkAddress != null);
        String networkAddressString = IPv4.fromIPv4Address(
                                            networkAddress.getNetworkAddress());
        String networkAddressId = deviceId + "|" + networkAddressString;
        storageSource.deleteRowAsync(DEVICE_NETWORK_ADDRESS_TABLE_NAME, 
                                                            networkAddressId);
    }

    // ********************
    // Storage Read Methods
    // ********************

    public boolean readPortChannelConfigFromStorage() {
        devMgrMaps.clearPortChannelMap();

        try {
            IResultSet pcResultSet = storageSource.executeQuery(
            PORT_CHANNEL_TABLE_NAME, null, null, null);
        
            while (pcResultSet.next()) {
                String port_channel = pcResultSet.getString(PORT_CHANNEL_COLUMN_NAME);
                String switch_id = pcResultSet.getString(PC_SWITCH_COLUMN_NAME);
                String port_name = pcResultSet.getString(PC_PORT_COLUMN_NAME);
                devMgrMaps.addPortToPortChannel(switch_id, port_name, port_channel);
            }
            return true;
        } catch (StorageException e) {
            log.error("Error reading port-channel data from storage {}", e);
            return false;
        }
    }
    
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
            log.error("Error reading device data from storage {}", e);
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
               removeAttachmentPointFromStorage(macString, dpidString, port.toString());
               devMgrMaps.updateSwitchUnresolvedAPMap(
                       swDpid, mac, port.shortValue(), lastSeen);
           } else {
               devMgrMaps.addDevAttachmentPoint(
                                       mac, sw, port.shortValue(), lastSeen);
               evHistAttachmtPt(mac, sw.getId(), port.shortValue(), 
                                       EvAction.ADDED, "Read from storage");
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
        storageSource.deleteMatchingRowsAsync(DEVICE_ATTACHMENT_POINT_TABLE_NAME,
                new OperatorPredicate(DEVICE_COLUMN_NAME, 
                        OperatorPredicate.Operator.EQ, deviceId));
        storageSource.deleteMatchingRowsAsync(DEVICE_NETWORK_ADDRESS_TABLE_NAME,
                new OperatorPredicate(DEVICE_COLUMN_NAME, 
                        OperatorPredicate.Operator.EQ, deviceId));

        // Remove the device
        storageSource.deleteRow(DEVICE_TABLE_NAME, deviceId);
    }
    
    /**
     * IStorageSource listeners.
     * Need to optimize if we support a large number of port-channel entries.
     */

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        portChannelConfigChanged = true;
        deviceUpdateTask.reschedule(5, TimeUnit.SECONDS);
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        portChannelConfigChanged = true;
        deviceUpdateTask.reschedule(5, TimeUnit.SECONDS);          
    }

    /**
     * Remove aged network address from device
     *    
     * @param device
     * @param currentDate
     * @return the new device object since the device is immutable
     */

    private Device removeAgedNetworkAddresses(Device device, Date currentDate) {
        Collection<DeviceNetworkAddress> addresses = 
                                                device.getNetworkAddresses();

        for (DeviceNetworkAddress address : addresses) {
            long expire = address.getExpire();

            if (expire == 0) {
                expire = DEVICE_NA_MAX_AGE;
            }
            Date agedBoundary = ageBoundaryDifference(currentDate, expire);

            if (address.getLastSeen().before(agedBoundary)) {
                devMgrMaps.delNwAddrByDataLayerAddr(device.getDataLayerAddressAsLong(), 
                    address.getNetworkAddress().intValue());
            }
        }
        
        return devMgrMaps.getDeviceByDataLayerAddr(device.getDataLayerAddressAsLong());
    }

    /**
     * Remove aged device attachment point
     * 
     * @param device
     * @param currentDate
     * @return the new device object since the device is immutable
     */
    private Device removeAgedAttachmentPoints(Device device, Date currentDate) {
        if (device == null) return null;

        long dlAddr = device.getDataLayerAddressAsLong();
        Collection<DeviceAttachmentPoint> aps = device.getAttachmentPoints();

        for (DeviceAttachmentPoint ap : aps) {
            int expire = ap.getExpire();

            if (expire == 0) {
                expire = DEVICE_AP_MAX_AGE;
            }
            Date agedBoundary = ageBoundaryDifference(currentDate, expire);
            if (ap.getLastSeen().before(agedBoundary)) {
                log.debug("remove AP {} from device {}", ap, HexString.toHexString(dlAddr));
                devMgrMaps.delDevAttachmentPoint(dlAddr, ap.getSwitchPort());
                evHistAttachmtPt(device.getDataLayerAddressAsLong(), 
                        ap.getSwitchPort(), EvAction.REMOVED,
                        "Aged");
            }
        }
        
        return devMgrMaps.getDeviceByDataLayerAddr(device.getDataLayerAddressAsLong());
    }

    /**
     * Age device entry based on an expiry associated with the device.
     */
    private void removeAgedDevices(Date currentDate) {
        Date deviceAgeBoundary = ageBoundaryDifference(currentDate, 
                                        DEVICE_MAX_AGE);

        Collection<Device> deviceColl = devMgrMaps.getDevices();
        for (Device device: deviceColl) {
             device = removeAgedNetworkAddresses(device, currentDate);
             device = removeAgedAttachmentPoints(device, currentDate);

             if ((device.getAttachmentPoints().size() == 0) &&
                 (device.getNetworkAddresses().size() == 0) &&
                 (device.getLastSeen().before(deviceAgeBoundary))) {
                 delDevice(device);
             }
        }
    }

    protected static int DEVICE_AGING_TIMER= 60 * 15; // in seconds
    protected static final int DEVICE_AGING_TIMER_INTERVAL = 1; // in seconds

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
                Date currentDate = new Date();
                removeAgedDevices(currentDate);

                if (deviceAgingTimer != null) {
                    ScheduledExecutorService ses =
                        threadPool.getScheduledExecutor();
                    ses.schedule(this, DEVICE_AGING_TIMER, TimeUnit.SECONDS);
                }
            }
        };
        threadPool.getScheduledExecutor().schedule(
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
            boolean updatePortChannel = portChannelConfigChanged;
            portChannelConfigChanged = false;

            if (updatePortChannel) {
                readPortChannelConfigFromStorage();
            }

            try { 
                log.debug("DeviceUpdateWorker: cleaning up attachment points.");
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
                                    if (dap.isInConflict() && !updatePortChannel)
                                        continue;
                                    // Delete from memory after storage,
                                    // otherwise an exception will
                                    // leave stale attachment points on storage.
                                    log.debug("Remove AP {} from storage for device {}", dap, dCopy.getDlAddrString());
                                    removeAttachmentPointFromStorage(dCopy.getDlAddrString(),
                                        HexString.toHexString(dap.getSwitchPort().getSw().getId()),
                                        dap.getSwitchPort().getPort().toString());
                                    dCopy.removeOldAttachmentPoint(dap);
                                }
                                // Update the maps with the new device copy
                                devMgrMaps.updateMaps(dCopy);
                            }
                            break;
                        }  catch (ConcurrentModificationException e) {
                            maxIter--;
                        } catch (NullPointerException e) { }
                    }
                    if (maxIter == 0) {
                        log.warn("Device attachment point clean up " +
                                "attempted three times and failed.");
                    }
                }
            } catch (StorageException e) {
                log.error("DeviceUpdateWorker had a storage exception, " +
                        "Floodlight exiting");
                System.exit(1);
            }
        }
    }

    @Override
    public void linkDiscoveryUpdate(LDUpdate update) {
        if (update.getOperation() == UpdateOperation.SWITCH_UPDATED) {
            updatedSwitch(update.getSrc(), update.getSrcType());
        } else if (update.getOperation() == UpdateOperation.ADD_OR_UPDATE) {
            this.addedOrUpdatedLink(update.getSrc(), update.getSrcPort(), 
                                    update.getSrcPortState(), 
                                    update.getDst(), update.getDstPort(),
                                    update.getDstPortState(), 
                                    update.getType());
        }
    }

    public void updatedSwitch(long swId, SwitchType stype) {
        IOFSwitch sw = floodlightProvider.getSwitches().get(swId);
        if (sw.hasAttribute(IOFSwitch.SWITCH_IS_CORE_SWITCH)) {
            removedSwitch(sw);
        }
    }
    
    // **************************************************
    // Device Manager's Event History members and methods
    // **************************************************

    // Attachment-point event history
    public EventHistory<EventHistoryAttachmentPoint> evHistDevMgrAttachPt;
    public EventHistoryAttachmentPoint evHAP;

    private void evHistAttachmtPt(long dlAddr, SwitchPortTuple swPrt,
                                            EvAction action, String reason) {
        evHistAttachmtPt(
                dlAddr,
                swPrt.getSw().getId(),
                swPrt.getPort(), action, reason);
    }

    private void evHistAttachmtPt(byte [] mac, SwitchPortTuple swPrt,
                                          EvAction action, String reason) {
        evHistAttachmtPt(Ethernet.toLong(mac), swPrt.getSw().getId(),
                                            swPrt.getPort(), action, reason);
    }

    private void evHistAttachmtPt(Device d, long dpid, short port,
                                            EvAction op, String reason) {
        evHistAttachmtPt(d.getDataLayerAddressAsLong(),dpid, port, op, reason);
    }

    private void evHistAttachmtPt(long mac, long dpid, short port,
                                              EvAction action, String reason) {
        if (evHAP == null) {
            evHAP = new EventHistoryAttachmentPoint();
        }
        evHAP.dpid   = dpid;
        evHAP.port   = port;
        evHAP.mac    = mac;
        evHAP.reason = reason;
        evHAP = evHistDevMgrAttachPt.put(evHAP, action);
    }

    /***
     * Packet-In Event history related classes and members
     * @author subrata
     *
     */

    public EventHistory<OFMatch> evHistDevMgrPktIn;

    private void evHistPktIn(OFMatch packetIn) {
        evHistDevMgrPktIn.put(packetIn, EvAction.PKT_IN);
    }

    // IFloodlightModule methods
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDeviceManagerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(IDeviceManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(ITopologyService.class);
        l.add(ILinkDiscoveryService.class);
        l.add(IStorageSourceService.class);
        l.add(IThreadPoolService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        // Wire up all our dependencies
        floodlightProvider = 
                context.getServiceImpl(IFloodlightProviderService.class);
        topology =
                context.getServiceImpl(ITopologyService.class);
        linkDiscovery = 
                context.getServiceImpl(ILinkDiscoveryService.class);
        storageSource =
                context.getServiceImpl(IStorageSourceService.class);
        threadPool =
                context.getServiceImpl(IThreadPoolService.class);
        restApi =
                context.getServiceImpl(IRestApiService.class);
        
        // We create this here because there is no ordering guarantee
        this.deviceManagerAware = new HashSet<IDeviceManagerAware>();
        this.updates = new LinkedList<Update>();
        this.devMgrMaps = new DevMgrMaps();
        this.lock = new ReentrantReadWriteLock();
        
        this.evHistDevMgrAttachPt = 
                new EventHistory<EventHistoryAttachmentPoint>("Attachment-Point");
        this.evHistDevMgrPktIn =
                new EventHistory<OFMatch>("Pakcet-In");
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // This is our 'constructor'

        if (linkDiscovery != null) {
            // Register to get updates from topology
            linkDiscovery.addListener(this);
        } else {
            log.error("Could not add linkdiscovery listener");
        }
        if (topology != null) {
            topology.addListener(this);
        } else {
            log.error("Could not add topology listener");
        }

        // Create our database tables
        storageSource.createTable(DEVICE_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        DEVICE_TABLE_NAME, MAC_COLUMN_NAME);
        storageSource.createTable(DEVICE_ATTACHMENT_POINT_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        DEVICE_ATTACHMENT_POINT_TABLE_NAME, ID_COLUMN_NAME);
        storageSource.createTable(DEVICE_NETWORK_ADDRESS_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        DEVICE_NETWORK_ADDRESS_TABLE_NAME, ID_COLUMN_NAME);
        storageSource.createTable(PORT_CHANNEL_TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(
                        PORT_CHANNEL_TABLE_NAME, PC_ID_COLUMN_NAME);
        storageSource.addListener(PORT_CHANNEL_TABLE_NAME, this);

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        deviceUpdateTask = new SingletonTask(ses, new DeviceUpdateWorker());
         
        // Register for the OpenFlow messages we want
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        // Register for switch events
        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addInfoProvider("summary", this);

        // Register our REST API
        restApi.addRestletRoutable(new DeviceManagerWebRoutable());
        
        // Read all our device state (MACs, IPs, attachment points) from storage
        readAllDeviceStateFromStorage();
        // Device and storage aging.
        enableDeviceAgingTimer();
    }

    @Override
    public void addListener(IDeviceManagerAware listener) {
        deviceManagerAware.add(listener);
    }

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type))
            return null;

        Map<String, Object> info = new HashMap<String, Object>();
        info.put("# hosts", devMgrMaps.dataLayerAddressDeviceMap.size());
        info.put("# IP Addresses", devMgrMaps.ipv4AddressDeviceMap.size());
        int num_aps = 0;
        for (Map<Integer, Device> devAps : devMgrMaps.switchPortDeviceMap.values())
            num_aps += devAps.size();
        info.put("# attachment points", num_aps);

        return info;
    }
}
