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

/**
 * The Class for FlowReconcileQuery for BVS config interface match VLAN.
 */
@Deprecated
public class FRQueryBvsMatchVlan extends FlowReconcileQuery {
    /* The match vlan IDs. */
    public List<Integer> vlans;

    public FRQueryBvsMatchVlan() {
        super(ReconcileQueryEvType.BVS_INTERFACE_RULE_CHANGED_MATCH_VLAN);
    }

    public FRQueryBvsMatchVlan(List<Integer> vlans) {
        this();
        this.vlans = vlans;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + vlans.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof FRQueryBvsMatchVlan)) {
            return false;
        }
        FRQueryBvsMatchVlan other = (FRQueryBvsMatchVlan) obj;
        if (! vlans.equals(other.vlans)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Vlans: ");
        builder.append(vlans);
        builder.append("]");
        return builder.toString();
    }
}
