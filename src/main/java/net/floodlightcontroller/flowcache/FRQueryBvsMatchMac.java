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

import org.projectfloodlight.openflow.types.MacAddress;

/**
 * The Class for FlowReconcileQuery for link down event.
 */
@Deprecated
public class FRQueryBvsMatchMac extends FlowReconcileQuery {
    /*the match mac*/
    public String mac;

    public FRQueryBvsMatchMac() {
        super(ReconcileQueryEvType.BVS_INTERFACE_RULE_CHANGED_MATCH_MAC);
    }

    public FRQueryBvsMatchMac(String mac) {
        this();
        this.mac = mac;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + mac.hashCode();
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
        if (!(obj instanceof FRQueryBvsMatchMac)) {
            return false;
        }
        FRQueryBvsMatchMac other = (FRQueryBvsMatchMac) obj;
        if (! mac.equals(other.mac)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("MAC: ");
        builder.append(MacAddress.of(mac).toString());
        builder.append("]");
        return builder.toString();
    }
}
