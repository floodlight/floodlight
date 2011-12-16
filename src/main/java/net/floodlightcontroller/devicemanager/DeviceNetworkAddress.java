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

import java.util.Date;

public class DeviceNetworkAddress {
    private Integer networkAddress;
    private Date lastSeen;
    private Date lastSeenInStorage;
    private Date lastWrittenToStorage;
    private long expire;

    private static long LAST_SEEN_STORAGE_UPDATE_INTERVAL = 1000 * 60 * 5; // 5 minutes
    
    public static void setStorageUpdateInterval(int intervalInMs) {
        LAST_SEEN_STORAGE_UPDATE_INTERVAL = intervalInMs;
    }

    public DeviceNetworkAddress(Integer networkAddress, Date lastSeen) {
        this.networkAddress = networkAddress;
        this.lastSeen = lastSeen;
        this.lastSeenInStorage = null;
        this.lastWrittenToStorage = null;
    }
    
    public Integer getNetworkAddress() {
        return networkAddress;
    }
    
    // FIXME: The methods below dealing with the lastSeen value are duplicated
    // from the DeviceAttachmentPoint code. Should refactor so code is shared.
    
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
    
    public void setExpire(long renewalTime) {
        this.expire = renewalTime;
    }
    
    public long getExpire() {
        return expire;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 2633;
        int result = 1;
        result = prime * result + ((networkAddress == null) ? 0 : networkAddress.hashCode());
        // FIXME: Should we be including last_seen here?
        //result = prime * result + ((lastSeen == null) ? 0 : lastSeen.hashCode());
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
        if (!(obj instanceof DeviceNetworkAddress))
            return false;
        DeviceNetworkAddress other = (DeviceNetworkAddress) obj;
        if (networkAddress == null) {
            if (other.networkAddress != null)
                return false;
        } else if (!networkAddress.equals(other.networkAddress))
            return false;
        // FIXME: Should we be including last_seen here?
        //if (lastSeen == null) {
        //    if (other.lastSeen != null)
        //        return false;
        //} else if (!lastSeen.equals(other.lastSeen))
        //    return false;
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Device Network Address [networkAddress=" + ((networkAddress == null) ? "null" : networkAddress) + "]";
    }

    public Date getLastSeenInStorage() {
        return lastSeenInStorage;
    }

    public Date getLastWrittenToStorage() {
        return lastWrittenToStorage;
    }
}
