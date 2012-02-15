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

import java.util.Map;
import java.util.Set;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.devicemanager.IDeviceManager;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.topology.ITopologyAware;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeviceManager creates Devices based upon MAC addresses seen in the network.
 * It tracks any network addresses mapped to the Device, and its location
 * within the network.
 * @author readams
 */
public class DeviceManagerImpl implements IDeviceManager, IOFMessageListener,
        IOFSwitchListener, ITopologyAware, IStorageSourceListener {  
    protected static Logger logger = 
        LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected IFloodlightProvider floodlightProvider;
    
    /**
     * This is the master device map that maps device IDs to {@link Device}
     * objects.
     */
    protected Map<Long, Device> deviceMap;
    
    /**
     * This is the primary entity index that maps entities to device IDs.
     */
    protected Map<IndexedEntity, Long> primaryIndex;
    
    /**
     * This map contains secondary indices for each of the configured {@ref IEntityClass} 
     * that exist
     */
    protected Map<IEntityClass, Map<IndexedEntity, Long>> secondaryIndexMap;

    // **************
    // IDeviceManager
    // **************
    
    // None for now
    
    // ******************
    // IOFMessageListener
    // ******************
    
    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getId() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        // TODO Auto-generated method stub
        return null;
    }
    
    // **********************
    // IStorageSourceListener
    // **********************
    
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        // TODO Auto-generated method stub
        
    }

    // **************
    // ITopologyAware
    // **************
    
    @Override
    public void addedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
                          IOFSwitch dstSw, short dstPort, int dstPortState) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updatedLink(IOFSwitch srcSw, short srcPort,
                            int srcPortState, IOFSwitch dstSw,
                            short dstPort, int dstPortState) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removedLink(IOFSwitch srcSw, short srcPort, IOFSwitch dstSw,
                            short dstPort) {
        // TODO Auto-generated method stub
    }

    @Override
    public void clusterMerged() {
        // TODO Auto-generated method stub
    }

    // *****************
    // IOFSwitchListener
    // *****************
    
    @Override
    public void updatedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
    }


    @Override
    public void addedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
    }

    // ****************
    // Internal methods
    // ****************
    
    public Command processPortStatusMessage(IOFSwitch sw, OFPortStatus ps) {
        return null;
        
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
        return null;
        /*
        Command ret = Command.CONTINUE;
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort(), sw.getId());
        // Add this packet-in to event history
        evHistPktIn(match);

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
            boolean updateNeworkAddressMap = false;
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
                } else if (eth != null && (eth.getPayload() instanceof ARP)) {
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
                                removeNetworkAddressFromStorage(dCopy, naOld);
                    log.info(
                     "Network address {} moved from {} to {} due to packet {}",
                            new Object[] {networkAddress,
                                          deviceByNwaddr.getDataLayerAddress(),
                                          device.getDataLayerAddress(),
                                          eth});
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
                        nd.addAttachmentPoint(attachmentPoint);
                        evHistAttachmtPt(nd, attachmentPoint.getSwitchPort(),
                                         EvAction.ADDED, "New AP from pkt-in");
                    }

                    if (clearAttachmentPoints) {
                        nd.clearAttachmentPoints();
                        evHistAttachmtPt(nd, 0L, (short)(-1),
                                EvAction.CLEARED, "Grat. ARP from pkt-in");
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
        */
    }
}
