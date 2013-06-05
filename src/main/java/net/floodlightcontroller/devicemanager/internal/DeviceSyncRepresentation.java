package net.floodlightcontroller.devicemanager.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;


import net.floodlightcontroller.devicemanager.SwitchPort;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceSyncRepresentation {
    public static class SyncEntity implements Comparable<SyncEntity> {
        @JsonProperty
        public long macAddress;
        @JsonProperty
        public Integer ipv4Address;
        @JsonProperty
        public Short vlan;
        @JsonProperty
        public Long switchDPID;
        @JsonProperty
        public Integer switchPort;
        @JsonProperty
        public Date lastSeenTimestamp;
        @JsonProperty
        public Date activeSince;

        public SyncEntity() {
            // do nothing;
        }

        public SyncEntity(Entity e) {
            this.macAddress = e.getMacAddress();
            this.ipv4Address = e.getIpv4Address();
            this.vlan = e.getVlan();
            this.switchDPID = e.getSwitchDPID();
            this.switchPort = e.getSwitchPort();
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
            Entity e = new Entity(macAddress, vlan, ipv4Address, switchDPID,
                                  switchPort, lastSeenTimestamp);
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
