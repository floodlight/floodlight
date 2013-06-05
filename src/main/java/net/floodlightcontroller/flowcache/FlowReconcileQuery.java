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

import net.floodlightcontroller.flowcache.PriorityPendingQueue.EventPriority;

/**
 * The base Class for FlowReconcileQuery.
 */
public class FlowReconcileQuery {
    public ReconcileQueryEvType evType;
    public EventPriority evPriority;
    public static enum ReconcileQueryEvType {
        /* Interface rule of a bvs was modified */
        BVS_INTERFACE_RULE_CHANGED(EventPriority.LOW),
        BVS_INTERFACE_RULE_CHANGED_MATCH_SWITCH_PORT(EventPriority.LOW),
        BVS_INTERFACE_RULE_CHANGED_MATCH_MAC(EventPriority.LOW),
        BVS_INTERFACE_RULE_CHANGED_MATCH_VLAN(EventPriority.LOW),
        BVS_INTERFACE_RULE_CHANGED_MATCH_IPSUBNET(EventPriority.LOW),
        BVS_INTERFACE_RULE_CHANGED_MATCH_TAG(EventPriority.LOW),
        /* Some bvs configuration was changed */
        BVS_PRIORITY_CHANGED(EventPriority.LOW),
        /* ACL configuration was changed */
        ACL_CONFIG_CHANGED(EventPriority.LOW),
        /* VRS routing rule was changed */
        VRS_ROUTING_RULE_CHANGED(EventPriority.LOW),
        /* VRS static ARP table was changed*/
        VRS_STATIC_ARP_CHANGED(EventPriority.LOW),
        /* device had moved to a different port in the network */
        DEVICE_MOVED(EventPriority.HIGH),
        /* device's property had changed, such as tag assignment */
        DEVICE_PROPERTY_CHANGED(EventPriority.LOW),
        /* Link down */
        LINK_DOWN(EventPriority.MEDIUM);

        private EventPriority priority;
        private ReconcileQueryEvType(EventPriority priority) {
            this.priority = priority;
        }
        public EventPriority getPriority() {
             return this.priority;
       }
    }
    public FlowReconcileQuery(ReconcileQueryEvType evType) {
        this.evType = evType;
        this.evPriority = evType.getPriority();
    }

    @Override
    public String toString() {
        return "FlowReconcileQuery [evType="
                + evType + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
                result = prime * result + ((evType == null) ? 0 : evType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        FlowReconcileQuery other = (FlowReconcileQuery) obj;
        if (evType != other.evType) return false;
                return true;
    }
}
