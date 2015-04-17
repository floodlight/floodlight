package net.floodlightcontroller.devicemanager.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.SwitchPort;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceSyncRepresentation {
    public static class SyncEntity implements Comparable<SyncEntity> {
        @JsonProperty
        public long macAddress;
        @JsonProperty
        public int ipv4Address;
        @JsonProperty
        public short vlan;
        @JsonProperty
        public long switchDPID;
        @JsonProperty
        public int switchPort;
        @JsonProperty
        public Date lastSeenTimestamp;
        @JsonProperty
        public Date activeSince;

        public SyncEntity() {
            // do nothing;
        }

        public SyncEntity(Entity e) {
            this.macAddress = (e.getMacAddress() != null ? e.getMacAddress().getLong() : 0);
            this.ipv4Address = (e.getIpv4Address() != null ? e.getIpv4Address().getInt() : 0);
            this.vlan = (e.getVlan() != null ? e.getVlan().getVlan() : -1);
            this.switchDPID = (e.getSwitchDPID() != null ? e.getSwitchDPID().getLong() : 0);
            this.switchPort = (e.getSwitchPort() != null ? e.getSwitchPort().getPortNumber() : 0);
            if (e.getLastSeenTimestamp() == null)
                this.lastSeenTimestamp = null;
            else
                this.lastSeenTimestamp = new Date(e.getLastSeenTimestamp().getTime());
            if (e.getActiveSince() == null)
                this.activeSince = null;
            else
                this.activeSince = new Date(e.getActiveSince().getTime());
        }

        public Entity asEntity() {
            Entity e = new Entity(macAddress == 0 ? null : MacAddress.of(macAddress), 
            		vlan == -1 ? null : VlanVid.ofVlan(vlan), 
            		ipv4Address == 0 ? null : IPv4Address.of(ipv4Address), 
            		switchDPID == 0 ? null : DatapathId.of(switchDPID),
                    switchPort == 0 ? null : OFPort.of(switchPort), 
                    lastSeenTimestamp);
            e.setActiveSince(activeSince);
            return e;
        }

        @Override
        public int compareTo(SyncEntity other) {
            return lastSeenTimestamp.compareTo(other.lastSeenTimestamp);
        }

        @Override
        public String toString() {
            return asEntity().toString();
        }
    }

    private String key;
    private List<SyncEntity> entities;

    public DeviceSyncRepresentation() {
        // do nothing
    }

    public DeviceSyncRepresentation(Device device) {
        this.key = computeKey(device);
        this.entities = new ArrayList<SyncEntity>();
        // FIXME: do we need the APs with errors as well??
        // FIXME
        SwitchPort[] aps = device.getAttachmentPoints();
        for(Entity e: device.getEntities()) {
            // Add the entities from the device only if they either don't
            // have a switch/port or if they are an attachment point or
            // if they have an IP address.
            if (!e.hasSwitchPort()) {
                this.entities.add(new SyncEntity(e));
            } else if (isAttachmentPointEntity(aps, e)) {
                this.entities.add(new SyncEntity(e));
            } else if (e.getIpv4Address() != null) {
                this.entities.add(new SyncEntity(e));
            }
        }
        Collections.sort(this.entities);
    }

    private static boolean isAttachmentPointEntity(SwitchPort[] aps, Entity e) {
        if (!e.hasSwitchPort())
            return false;
        for (SwitchPort p: aps) {
            if (e.getSwitchDPID().equals(p.getSwitchDPID()) &&
                    e.getSwitchPort().equals(p.getPort())) {
                return true;
            }
        }
        return false;
    }

    static String computeKey(Device d) {
        StringBuilder bld = new StringBuilder(d.getEntityClass().getName());
        bld.append("::");
        EnumSet<DeviceField> keyFields = d.getEntityClass().getKeyFields();
        if (keyFields.contains(DeviceField.MAC)) {
            bld.append(d.getMACAddressString());
            bld.append("::");
        }
        if (keyFields.contains(DeviceField.VLAN)) {
            if (d.getVlanId() != null)
                bld.append(Arrays.toString(d.getVlanId()));
            bld.append("::");
        }
        if (keyFields.contains(DeviceField.SWITCH) ||
                keyFields.contains(DeviceField.PORT) ) {
            if (d.getAttachmentPoints(true) != null)
                bld.append(Arrays.toString(d.getAttachmentPoints(true)));
            bld.append("::");
        }
        if (keyFields.contains(DeviceField.IPV4)) {
            if (d.getIPv4Addresses() != null)
                bld.append(Arrays.toString(d.getIPv4Addresses()));
            bld.append("::");
        }
        return bld.toString();
    }

    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public List<SyncEntity> getEntities() {
        return entities;
    }
    public void setEntities(List<SyncEntity> entities) {
        if (entities == null) {
            this.entities = null;
        } else {
            List<SyncEntity> tmp = new ArrayList<SyncEntity>(entities);
            Collections.sort(tmp);
            this.entities = tmp;
        }
    }

    @Override
    public String toString() {
        return key;
    }
}
