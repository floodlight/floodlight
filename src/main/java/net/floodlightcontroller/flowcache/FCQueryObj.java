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

import java.util.Arrays;
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

    /** The caller of the flow query. */
    public IFlowQueryHandler fcQueryHandler;
    /** The application instance name. */
    public String applInstName;
    /*BVS interface name*/
    public String appInstInterfaceName;
    /** The vlan Id. */
    public Short[] vlans;
    /** The destination device. */
    public IDevice dstDevice;
    /** The source device. */
    public IDevice srcDevice;
    /*source switch DPID*/
    public long srcSwId;
    /*switch port */
    public short swPort;
    public List<String> swPortList;
    /*ip subnet*/
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
        this.fcQueryHandler    = null;
        this.applInstName     = null;
        this.srcDevice        = null;
        this.dstDevice        = null;
        this.callerName       = null;
        this.callerOpaqueObj  = null;
        this.vlans=null;
        this.appInstInterfaceName = null;
        this.srcSwId = 0L;
        this.swPort = OFPort.OFPP_NONE.getValue();
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
            default:
                break;
        }
        return priority;
    }

    /**
     * Instantiates a new flow query object
    public FCQueryObj(IFlowQueryHandler fcQueryHandler,
            String        applInstName,
            String        appInstInterfaceName,
            Short         vlan,
            IDevice       srcDevice,
            IDevice       dstDevice,
            IOFSwitch     srcSW,
            short           swPort,
            String        ipSubnet,
            String        callerName,
            FCQueryEvType evType,
            Object        callerOpaqueObj) {
        this.fcQueryHandler    = fcQueryHandler;
        this.applInstName     = applInstName;
        this.srcDevice        = srcDevice;
        this.dstDevice        = dstDevice;
        this.callerName       = callerName;
        this.evType           = evType;
        this.callerOpaqueObj  = callerOpaqueObj;
        if (vlan != null) {
        	this.vlans = new Short[] { vlan };
        } else {
            this.vlans = null;
        }
        if (appInstInterfaceName != null) {
            this.appInstInterfaceName = new String(appInstInterfaceName);
        } else {
            this.appInstInterfaceName = null;
        }
        if (srcSW != null) {
            this.srcSW =  srcSW;
        } else {
            this.srcSW = null;
        }
        if (swPort != OFPort.OFPP_NONE.getValue()) {
            this.swPort =  swPort;
        } else {
            this.swPort = OFPort.OFPP_NONE.getValue();
        }
        if (srcIpsubnet != null) {
            this.srcIpsubnet =new String(ipSubnet);
        } else {
            this.srcIpsubnet =null;
        }
    }
*/
    @Override
    public String toString() {
        return "FCQueryObj [fcQueryCaller=" + fcQueryHandler
                + ", applInstName="
                + applInstName + ", vlans=" + Arrays.toString(vlans)
                + ", dstDevice=" + dstDevice + ", srcDevice="
                + srcDevice + ", callerName=" + callerName + ", evType="
                + evType + ", callerOpaqueObj=" + callerOpaqueObj + "]";
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
        result = prime * result
                 + ((dstDevice == null) ? 0 : dstDevice.hashCode());
        result = prime * result + ((evType == null) ? 0 : evType.hashCode());
        result = prime
                 * result
                 + ((fcQueryHandler == null) ? 0 : fcQueryHandler.hashCode());
        result = prime * result
                 + ((srcDevice == null) ? 0 : srcDevice.hashCode());
        result = prime * result + Arrays.hashCode(vlans);
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
        if (dstDevice == null) {
            if (other.dstDevice != null) return false;
        } else if (!dstDevice.equals(other.dstDevice)) return false;
        if (evType != other.evType) return false;
        if (fcQueryHandler == null) {
            if (other.fcQueryHandler != null) return false;
        } else if (!fcQueryHandler.equals(other.fcQueryHandler))
                                                                return false;
        if (srcDevice == null) {
            if (other.srcDevice != null) return false;
        } else if (!srcDevice.equals(other.srcDevice)) return false;
        if (!Arrays.equals(vlans, other.vlans)) return false;

        if (srcSwId == 0L) {
            if (other.srcSwId != 0L) return false;
        } else if (srcSwId != other.srcSwId) return false;
        if (!Arrays.equals(vlans, other.vlans)) return false;
        if (swPort != other.swPort) return false;
        if (srcIpsubnet == null) {
            if (other.srcIpsubnet != null) return false;
        } else if (!srcIpsubnet.equals(other.srcIpsubnet)) return false;
        if (appInstInterfaceName == null) {
            if (other.appInstInterfaceName != null) return false;
        } else if (!appInstInterfaceName.equals(other.appInstInterfaceName)) return false;
        return true;
    }
}
