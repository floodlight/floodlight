/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.flowcache;

import java.util.List;

import org.openflow.protocol.OFPort;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.flowcache.IFlowReconcileEngineService.FCQueryEvType;
import net.floodlightcontroller.flowcache.PriorityPendingQueue.EventPriority;


/**
 * The Class FCQueryObj.
 */
public class FCQueryObj {
    /** The application instance name. */
    public String applInstName;
    /*BVS interface name*/
    public String appInstInterfaceName;
    /** The match vlan Id. */
    public List<Integer> vlans;
    /*the match mac*/
    public String mac;
    /** The moved device. */
    public List<String> tag;
    public IDevice deviceMoved;
    /*switch DPID: match switch port + Link down event*/
    public long srcSwId;
    /*List of ports: match switch port*/
    public List<String> matchPortList;
    /*switch port: link down event */
    public short portDown;
    /*match ip subnet*/
    public String srcIpsubnet;
    public String destIpsubnet;
    public String srcBVS;
    public String destBVS;
    /*BVS priority change*/
    public int lowP;
    public int highP;
    /** The caller name */
    public String callerName;
    /** Event type and priority that triggered this flow query submission */
    public FCQueryEvType evType;
    public EventPriority evPriority;
    /** The caller opaque data. Returned unchanged in the query response
     * via the callback. The type of this object could be different for
     * different callers */
    public Object callerOpaqueObj;
    public DIRECTION direction;
    public SwitchPort[] oldAp;
    public enum DIRECTION {
        INGRESS,
        EGRESS,
        BOTH
    };

    /**
     * Instantiates a new flow query object
     */
    public FCQueryObj(FCQueryEvType evType) {
        this.evType = evType;
        this.evPriority = mapEventToPriority(evType);
        this.applInstName     = null;
        this.deviceMoved        = null;
        this.callerName       = null;
        this.callerOpaqueObj  = null;
        this.vlans=null;
        this.mac=null;
        this.tag=null;
        this.appInstInterfaceName = null;
        this.srcSwId = 0L;
        this.portDown = OFPort.OFPP_NONE.getValue();
        this.srcIpsubnet = null;
        this.destIpsubnet = null;
        this.srcBVS = null;
        this.destBVS = null;
        this.lowP = -1;
        this.highP = -1;
        this.oldAp =null;
        this.direction=null;
    }

    public static EventPriority mapEventToPriority(FCQueryEvType type) {
        EventPriority priority=EventPriority.EVENT_LOW;
        switch (type) {
            case DEVICE_MOVED:
                priority=EventPriority.EVENT_HIGH;
                break;
            case LINK_DOWN:
                priority=EventPriority.EVENT_MEDIUM;
                break;
            case REWRITE_QUERY:  /**rewrite query is triggered by Link_down event*/
                priority=EventPriority.EVENT_MEDIUM;
                break;
            default:
                break;
        }
        return priority;
    }

    @Override
    public String toString() {
        return "FCQueryObj [applInstName="
                + applInstName + ", callerName=" + callerName + ", evType="
                + evType + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((applInstName == null) ? 0 : applInstName.hashCode());
        result = prime * result
                 + ((callerName == null) ? 0 : callerName.hashCode());
        result = prime
                 * result
                 + ((callerOpaqueObj == null) ? 0
                                             : callerOpaqueObj.hashCode());
        result = prime * result + ((evType == null) ? 0 : evType.hashCode());
        result = prime * result + ((mac == null) ? 0 : mac.hashCode());
        result = prime * result + (int) srcSwId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        FCQueryObj other = (FCQueryObj) obj;
        if (applInstName == null) {
            if (other.applInstName != null) return false;
        } else if (!applInstName.equals(other.applInstName)) return false;
        if (callerName == null) {
            if (other.callerName != null) return false;
        } else if (!callerName.equals(other.callerName)) return false;
        if (callerOpaqueObj == null) {
            if (other.callerOpaqueObj != null) return false;
        } else if (!callerOpaqueObj.equals(other.callerOpaqueObj))
                                                                  return false;
        if (evType != other.evType) return false;
        if (!vlans.equals(other.vlans)) return false;

        if (srcSwId == 0L) {
            if (other.srcSwId != 0L) return false;
        } else if (srcSwId != other.srcSwId) return false;
        if (!matchPortList.equals(other.matchPortList)) return false;
        if (!vlans.equals(other.vlans)) return false;
        if (portDown != other.portDown) return false;
        if (mac != other.mac) return false;
        if (tag != other.tag) return false;
        if (srcIpsubnet == null) {
            if (other.srcIpsubnet != null) return false;
        } else if (!srcIpsubnet.equals(other.srcIpsubnet)) return false;
        if (appInstInterfaceName == null) {
            if (other.appInstInterfaceName != null) return false;
        } else if (!appInstInterfaceName.equals(other.appInstInterfaceName)) return false;
        return true;
    }
}
