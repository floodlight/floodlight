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

import java.util.Date;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public class AttachmentPoint {
    DatapathId  sw;
    OFPort port;
    Date  activeSince;
    Date  lastSeen;

    // Timeout for moving attachment points from OF/broadcast
    // domain to another.
    public static final long INACTIVITY_INTERVAL = 30000; // 30 seconds
    public static final long EXTERNAL_TO_EXTERNAL_TIMEOUT = 5000;  // 5 seconds
    public static final long OPENFLOW_TO_EXTERNAL_TIMEOUT = 30000; // 30 seconds
    public static final long CONSISTENT_TIMEOUT = 30000;           // 30 seconds

    public AttachmentPoint(DatapathId sw, OFPort port, Date activeSince, Date lastSeen) {
        this.sw = sw;
        this.port = port;
        this.activeSince = activeSince;
        this.lastSeen = lastSeen;
    }

    public AttachmentPoint(DatapathId sw, OFPort port, Date lastSeen) {
        this.sw = sw;
        this.port = port;
        this.lastSeen = lastSeen;
        this.activeSince = lastSeen;
    }

    public AttachmentPoint(AttachmentPoint ap) {
        this.sw = ap.getSw();
        this.port = ap.port;
        this.activeSince = ap.activeSince;
        this.lastSeen = ap.lastSeen;
    }

    public DatapathId getSw() {
        return sw;
    }
    public void setSw(DatapathId sw) {
        this.sw = sw;
    }
    public OFPort getPort() {
        return port;
    }
    public void setPort(OFPort port) {
        this.port = port;
    }
    public Date getActiveSince() {
        return activeSince;
    }
    public void setActiveSince(Date activeSince) {
        this.activeSince = activeSince;
    }
    public Date getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(Date lastSeen) {
        if (this.lastSeen.getTime() + INACTIVITY_INTERVAL < lastSeen.getTime())
            this.activeSince = lastSeen;
        if (this.lastSeen.before(lastSeen))
            this.lastSeen = lastSeen;
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((port == null) ? 0 : port.hashCode());
		result = prime * result + ((sw == null) ? 0 : sw.hashCode());
		return result;
	}

    @Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AttachmentPoint other = (AttachmentPoint) obj;
		if (port == null) {
			if (other.port != null)
				return false;
		} else if (!port.equals(other.port))
			return false;
		if (sw == null) {
			if (other.sw != null)
				return false;
		} else if (!sw.equals(other.sw))
			return false;
		return true;
	}

    @Override
    public String toString() {
        return "AttachmentPoint [sw=" + sw + ", port=" + port
               + ", activeSince=" + activeSince + ", lastSeen=" + lastSeen.toString()
               + "]";
    }
}
