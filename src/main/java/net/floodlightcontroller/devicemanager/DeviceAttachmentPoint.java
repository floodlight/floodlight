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

package net.floodlightcontroller.devicemanager;

import java.util.Comparator;
import java.util.Date;

import net.floodlightcontroller.topology.SwitchPortTuple;

public class DeviceAttachmentPoint {
    private SwitchPortTuple switchPort;
    private Date lastSeen;
    private Date lastSeenInStorage;
    private Date lastWrittenToStorage;
    private int expire; // zero for never, seconds to expiration otherwise
    
    // Attachment point conflicts
    protected Date lastConflict;
    protected int conflictFrequency;
    private boolean blocked;
        
    private static int conflictThreshold = 300;  // roughly 3 changes in 1 minute
    
    private static long LAST_SEEN_STORAGE_UPDATE_INTERVAL = 1000 * 60 * 5; // 5 minutes
    
    // Comparator for sorting by SwitchCluster
    public static Comparator<DeviceAttachmentPoint> clusterIdComparator = new Comparator<DeviceAttachmentPoint>() {
        @Override
        public int compare(DeviceAttachmentPoint d1, DeviceAttachmentPoint d2) {
            Long d1ClusterId = d1.getSwitchPort().getSw().getSwitchClusterId();
            Long d2ClusterId = d2.getSwitchPort().getSw().getSwitchClusterId();
            return d1ClusterId.compareTo(d2ClusterId);
        }
    };
        
    public static void setStorageUpdateInterval(int intervalInMs) {
        LAST_SEEN_STORAGE_UPDATE_INTERVAL = intervalInMs;
    }
    
    public DeviceAttachmentPoint(SwitchPortTuple switchPort, Date lastSeen) {
        this.switchPort = switchPort;
        this.lastSeen = lastSeen;
        this.lastSeenInStorage = null;
        this.lastWrittenToStorage = null;
        
        this.lastConflict = null;
        this.conflictFrequency = 0;
    }
    
    public SwitchPortTuple getSwitchPort() {
        return switchPort;
    }
    
    public Date getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;
    }
    
    public void lastSeenWrittenToStorage(Date lastWrittenDate) {
        lastSeenInStorage = lastSeen;
        lastWrittenToStorage = lastWrittenDate;
    }
    
    public boolean shouldWriteLastSeenToStorage() {
        return (lastSeen != lastSeenInStorage) && ((lastWrittenToStorage == null) ||
                (lastSeen.getTime() >= lastWrittenToStorage.getTime() + LAST_SEEN_STORAGE_UPDATE_INTERVAL));
    }
    
    public void setExpire(int expire) {
        this.expire = expire;
    }
    
    public int getExpire() {
        return expire;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 2633;
        int result = 1;
        result = prime * result + ((switchPort == null) ? 0 : switchPort.hashCode());       
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof DeviceAttachmentPoint))
            return false;
        DeviceAttachmentPoint other = (DeviceAttachmentPoint) obj;
        if (switchPort == null) {
            if (other.switchPort != null)
                return false;
        } else if (!switchPort.equals(other.switchPort))
            return false;        
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Device Attachment Point [switchPort=" + switchPort + "]";
    }

    /**
     * Keep track of attachment point changes
     * Each change counts as 100, decay to half the value after 1 minute.
     * If the changeScale exceeds 300, we decide that it's flapping.
     */
    public void setConflict(Date currentDate) {
        if (lastConflict == null) {
            lastConflict = currentDate;
            conflictFrequency = 100;
            return;
        }
        
        int decay = (int) ((currentDate.getTime() - lastConflict.getTime()) / 30000);
        if (decay > 1)
            conflictFrequency /= decay;
        lastConflict = currentDate;
        conflictFrequency += 100;
    }
    
    public boolean isInConflict() {
        return lastConflict != null;
    }

    public boolean isFlapping() {
        return conflictFrequency >= conflictThreshold;
    }
    
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isBlocked() {
        return blocked;
    }
    
    public static boolean isNotNull(DeviceAttachmentPoint dap) {
        if (dap == null) return false; 
        if (dap.getSwitchPort() == null) return false;
        if (dap.getSwitchPort().getSw() == null) return false;
        return true;
    }

    public Date getLastSeenInStorage() {
        return lastSeenInStorage;
    }

    public Date getLastWrittenToStorage() {
        return lastWrittenToStorage;
    }

    public Date getLastConflict() {
        return lastConflict;
    }

    public int getConflictFrequency() {
        return conflictFrequency;
    }

    public static int getConflictThreshold() {
        return conflictThreshold;
    }

    public static Comparator<DeviceAttachmentPoint> getClusterIdComparator() {
        return clusterIdComparator;
    }
}
