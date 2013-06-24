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

import java.util.Set;

/**
 * The Class for FlowReconcileQuery for VRS routing rule change event .
 */
public class FRQueryVRSRuleChange extends FlowReconcileQuery {
    /* The list of impacted bvs names. */
    public Set<String> bvsNames;
    public FRQueryVRSRuleChange() {
        super(ReconcileQueryEvType.VRS_ROUTING_RULE_CHANGED);
    }

    public FRQueryVRSRuleChange(Set<String> bvsNames) {
        this();
        this.bvsNames = bvsNames;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + bvsNames.hashCode();
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
        if (!(obj instanceof FRQueryVRSRuleChange)) {
            return false;
        }
        FRQueryVRSRuleChange other = (FRQueryVRSRuleChange) obj;
        if (! bvsNames.equals(other.bvsNames)) return false;
        return true;
    }
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("BVS Names: ");
        builder.append(bvsNames);
        builder.append("]");
        return builder.toString();
    }
}
