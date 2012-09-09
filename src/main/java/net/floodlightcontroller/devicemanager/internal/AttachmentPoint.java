/**
 *    Copyright 2011,2012 Big Switch Networks, Inc.
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
 * @author Srini
 */

package net.floodlightcontroller.devicemanager.internal;

public class AttachmentPoint {
    long  sw;
    short port;
    long  activeSince;
    long  lastSeen;

    // Timeout for moving attachment points from OF/broadcast
    // domain to another.
    public static final long INACTIVITY_INTERVAL = 30000; // 30 seconds
    public static final long EXTERNAL_TO_EXTERNAL_TIMEOUT = 5000;  // 5 seconds
    public static final long OPENFLOW_TO_EXTERNAL_TIMEOUT = 30000; // 30 seconds
    public static final long CONSISTENT_TIMEOUT = 30000;           // 30 seconds

    public AttachmentPoint(long sw, short port, long activeSince,
                           long lastSeen) {
        this.sw = sw;
        this.port = port;
        this.activeSince = activeSince;
        this.lastSeen = lastSeen;
    }

    public AttachmentPoint(long sw, short port, long lastSeen) {
        this.sw = sw;
        this.port = port;
        this.lastSeen = lastSeen;
        this.activeSince = lastSeen;
    }

    public AttachmentPoint(AttachmentPoint ap) {
        this.sw = ap.sw;
        this.port = ap.port;
        this.activeSince = ap.activeSince;
        this.lastSeen = ap.lastSeen;
    }

    public long getSw() {
        return sw;
    }
    public void setSw(long sw) {
        this.sw = sw;
    }
    public short getPort() {
        return port;
    }
    public void setPort(short port) {
        this.port = port;
    }
    public long getActiveSince() {
        return activeSince;
    }
    public void setActiveSince(long activeSince) {
        this.activeSince = activeSince;
    }
    public long getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(long lastSeen) {
        if (this.lastSeen + INACTIVITY_INTERVAL < lastSeen)
            this.activeSince = lastSeen;
        if (this.lastSeen < lastSeen)
            this.lastSeen = lastSeen;
    }

    /**
     *  Hash is generated using only switch and port
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + port;
        result = prime * result + (int) (sw ^ (sw >>> 32));
        return result;
    }

    /**
     * Compares only the switch and port
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AttachmentPoint other = (AttachmentPoint) obj;
        if (port != other.port)
            return false;
        if (sw != other.sw)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "AttachmentPoint [sw=" + sw + ", port=" + port
               + ", activeSince=" + activeSince + ", lastSeen=" + lastSeen
               + "]";
    }
}
