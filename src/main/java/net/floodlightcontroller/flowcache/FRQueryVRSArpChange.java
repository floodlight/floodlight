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
/**
 * The Class for FlowReconcileQuery for VRS routing rule change event .
 */
public class FRQueryVRSArpChange extends FlowReconcileQuery {
    /* The list of impacted bvs names. */
    public String tenant;
    public FRQueryVRSArpChange() {
        super(ReconcileQueryEvType.VRS_STATIC_ARP_CHANGED);
    }

    public FRQueryVRSArpChange(String tenant) {
        this();
        this.tenant = tenant;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + tenant.hashCode();
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
        if (!(obj instanceof FRQueryVRSArpChange)) {
            return false;
        }
        FRQueryVRSArpChange other = (FRQueryVRSArpChange) obj;
        if (! tenant.equals(other.tenant)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Tenant: ");
        builder.append(tenant);
        builder.append("]");
        return builder.toString();
    }
}
