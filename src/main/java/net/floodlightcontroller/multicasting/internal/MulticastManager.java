package net.floodlightcontroller.multicasting.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.IPAddress;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.MacVlanPair;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.multicasting.IMulticastListener;
import net.floodlightcontroller.multicasting.IMulticastService;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * MulticastManager is only a service gateway between ParticipantTable
 * in Multicast Service and the MulticastGroups & MulticastPaths in 
 * Topology Service.
 * 
 */
public class MulticastManager implements IFloodlightModule, IMulticastService, IDeviceListener {
    
    /**
     * Table contains multicast group membership information
     */
    private static ParticipantTable participantTable = new ParticipantTable();
    
    private static Set<IMulticastListener> multicastListeners = 
            new HashSet<IMulticastListener>();
    
    // private IFloodlightProviderService floodlightProviderService;
    private IDeviceService deviceService;
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMulticastService.class);
        return l;
    }
    
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,  IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IMulticastService.class, this);
        return m;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(ITopologyService.class);
        l.add(IDeviceService.class);
        return l;
    }
    
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        // floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        deviceService = context.getServiceImpl(IDeviceService.class);
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        deviceService.addListener(this);
    }
    
    @Override
    public void addParticipant(ParticipantGroupAddress pgAddress, MacVlanPair intf, NodePortTuple ap) {
        Set<NodePortTuple> attachmentPoints = participantTable.getAttachmentPoints(pgAddress, intf);
        if (!attachmentPoints.contains(ap)) {
            participantTable.add(pgAddress, intf, ap);
            for (IMulticastListener multicastListener: multicastListeners) {
                multicastListener.ParticipantAdded(pgAddress, intf, ap);
            }
        }
    }

    @Override
    public void removeParticipant(ParticipantGroupAddress pgAddress, MacVlanPair intf, NodePortTuple ap) {
        Set<NodePortTuple> attachmentPoints = participantTable.getAttachmentPoints(pgAddress, intf);
        if (attachmentPoints.contains(ap)) {
            participantTable.remove(pgAddress, intf, ap);
            for (IMulticastListener multicastListener: multicastListeners) {
                multicastListener.ParticipantRemoved(pgAddress, intf, ap);
            }
        }
    }
    
    @Override
    public boolean hasParticipant(ParticipantGroupAddress pgAddress, MacVlanPair intf) {
        return participantTable.contains(pgAddress, intf);
    }
    
    @Override
    public Set<NodePortTuple> getParticipantAPs(ParticipantGroupAddress pgAddress, MacVlanPair intf) {
        return participantTable.getAttachmentPoints(pgAddress, intf);
    }
    
    @Override
    public Set<MacVlanPair> getParticipantIntfs(ParticipantGroupAddress pgAddress) {
        return participantTable.getIntfs(pgAddress);
    }

    @Override
    public Set<ParticipantGroupAddress> getParticipantGroupAddresses(MacVlanPair intf) {
        return participantTable.getGroupAddresses(intf);
    }

    @Override
    public Set<MacVlanPair> getAllParticipantIntfs() {
        return participantTable.getAllIntfs();
    }

    @Override
    public Set<ParticipantGroupAddress> getAllParticipantGroupAddresses() {
        return participantTable.getAllGroupAddresses();
    }

    @Override
    public boolean hasParticipantIntf(MacVlanPair intf) {
        return participantTable.hasIntf(intf);
    }

    @Override
    public boolean hasParticipantGroupAddress(ParticipantGroupAddress pgAddress) {
        return participantTable.hasGroupAddress(pgAddress);
    }

    @Override
    public void deleteParticipantGroupAddress(ParticipantGroupAddress pgAddress) {
        Set<MacVlanPair> intfSet = participantTable.getIntfs(pgAddress);
        for (MacVlanPair intf: intfSet) {
            Set<NodePortTuple> apSet = participantTable.getAttachmentPoints(pgAddress, intf);
            for (NodePortTuple ap: apSet) {
                removeParticipant(pgAddress, intf, ap);
            }
        }
    }

    @Override
    public void deleteParticipantIntf(MacVlanPair intf) {
        Set<ParticipantGroupAddress> pgAddressSet = participantTable.getGroupAddresses(intf);
        for (ParticipantGroupAddress pgAddress: pgAddressSet) {
            Set<NodePortTuple> apSet = participantTable.getAttachmentPoints(pgAddress, intf);
            for (NodePortTuple ap: apSet) {
                removeParticipant(pgAddress, intf, ap);
            }
        }
    }

    @Override
    public void clearAllParticipants(boolean clearPgOpts) {
        participantTable.clearTable(clearPgOpts);
        for (IMulticastListener multicastListener: multicastListeners) {
            multicastListener.ParticipantsReset();
        }
    }
    
    @Override
    public void setParticipantGroupOptions(ParticipantGroupAddress pgAddress, 
            ParticipantGroupOptions pgOpts) {
        participantTable.setGroupOptions(pgAddress, pgOpts);
    }

    @Override
    public ParticipantGroupOptions getParticipantGroupOptions(
            ParticipantGroupAddress pgAddress) {
        return participantTable.getGroupOptions(pgAddress);
    }
    
    @Override
    public ParticipantGroupAddress queryParticipantGroupAddress(MacAddress macAddress, 
            VlanVid vlanVid, IPAddress<?> ipAddress, TransportPort port) {
        return participantTable.queryGroupAddress(macAddress, vlanVid, 
                ipAddress, port);
    }
    
    @Override
    public void addListener(IMulticastListener listener) {
        multicastListeners.add(listener);
    }
    
    @Override
    public void removeListener(IMulticastListener listener) {
        multicastListeners.remove(listener);
    }

    @Override
    public String getName() {
        return "multicasting";
    }

    @Override
    public boolean isCallbackOrderingPrereq(String type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(String type, String name) {
        return false;
    }

    @Override
    public void deviceAdded(IDevice device) {
        // nothing to do
    }

    @Override
    public void deviceRemoved(IDevice device) {
        MacAddress macAddress = device.getMACAddress();
        VlanVid[] vlanIds = device.getVlanId();
        for (VlanVid vlanId: vlanIds) {
            MacVlanPair intf = new MacVlanPair(macAddress, vlanId);
            Set<ParticipantGroupAddress> pgAddressSet = getParticipantGroupAddresses(intf);
            for (ParticipantGroupAddress pgAddress: pgAddressSet) {
                Set<NodePortTuple> memberAP = getParticipantAPs(pgAddress, intf);
                for (NodePortTuple ap: memberAP) {
                    removeParticipant(pgAddress, intf, ap);
                }
            }
        }
    }

    @Override
    public void deviceMoved(IDevice device) {
        /*
         * If the device AP list does't contain a participant
         * AP, remove that AP from participants.
         */
        Set<NodePortTuple> devApSet = 
                new HashSet<NodePortTuple>(Arrays.asList(device.getAttachmentPoints()));
        MacAddress devMacAddress = device.getMACAddress();
        VlanVid[] devVlanIds = device.getVlanId();
        for (VlanVid devVlanId: devVlanIds) {
            MacVlanPair intf = new MacVlanPair(devMacAddress, devVlanId);
            Set<ParticipantGroupAddress> pgAddressSet = getParticipantGroupAddresses(intf);
            for (ParticipantGroupAddress pgAddress: pgAddressSet) {
                Set<NodePortTuple> apSet = getParticipantAPs(pgAddress, intf);
                for (NodePortTuple ap: apSet) {
                    if (!devApSet.contains(ap)) {
                        removeParticipant(pgAddress, intf, ap);
                    }
                }
            }
        }
    }

    @Override
    public void deviceIPV4AddrChanged(IDevice device) {
        // nothing to do
    }

    @Override
    public void deviceIPV6AddrChanged(IDevice device) {
        // nothing to do
    }

    @Override
    public void deviceVlanChanged(IDevice device) {
        /*
         * If the macAddress of the changed device is a 
         * participant macAddress, then remove those interfaces 
         * from the participants whose vlanIds don't belong to 
         * the device's current set of vlanIds, provided that 
         * corresponding pgAddresses use vlanId filter.
         */
        MacAddress devMacAddress = device.getMACAddress();
        Set<VlanVid> devVlanIdSet = new HashSet<VlanVid>(Arrays.asList(device.getVlanId()));
        Set<MacVlanPair> intfSet = getAllParticipantIntfs();
        for (MacVlanPair intf: intfSet) {
            MacAddress macAddress = intf.getMac();
            VlanVid vlanVid = intf.getVlan();
            if (devMacAddress.equals(macAddress) && !devVlanIdSet.contains(vlanVid)) {
                for (ParticipantGroupAddress pgAddress: getParticipantGroupAddresses(intf)) {
                    if (pgAddress.getVlanVid() != null) {
                        for (NodePortTuple ap: getParticipantAPs(pgAddress, intf)) {
                            removeParticipant(pgAddress, intf, ap);
                        }
                    }
                }
            }
        }
    }
}