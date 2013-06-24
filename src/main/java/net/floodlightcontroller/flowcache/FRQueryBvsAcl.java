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
 * The Class for FlowReconcileQuery for BVS config ACL application change .
 */
public class FRQueryBvsAcl extends FlowReconcileQuery {
    /* The BVS name. */
    public String bvsName;
    /*BVS interface name*/
    public String bvsInterfaceName;
    /*ACL applied direction*/
    public DIRECTION direction;
    public enum DIRECTION {
        INGRESS,
        EGRESS,
    };
    public FRQueryBvsAcl() {
        super(ReconcileQueryEvType.ACL_CONFIG_CHANGED);
    }

    public FRQueryBvsAcl(String bvsName, String bvsInterfaceName, DIRECTION direction) {
        this();
        this.bvsName = bvsName;
        this.bvsInterfaceName = bvsInterfaceName;
        this.direction = direction;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + bvsName.hashCode();
        result = prime * result + bvsInterfaceName.hashCode();
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
        if (!(obj instanceof FRQueryBvsAcl)) {
            return false;
        }
        FRQueryBvsAcl other = (FRQueryBvsAcl) obj;
        if (! bvsName.equals(other.bvsName)) return false;
        if (! bvsInterfaceName.equals(other.bvsInterfaceName)) return false;
        if (! direction.equals(other.direction)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("BVS Name: ");
        builder.append(bvsName);
        builder.append(", BVS Interface Name: ");
        builder.append(bvsInterfaceName);
        builder.append(", ACL Direction: ");
        builder.append(direction);
        builder.append("]");
        return builder.toString();
    }
}
