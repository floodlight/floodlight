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

import org.projectfloodlight.openflow.types.DatapathId;
/**
 * The Class for FlowReconcileQuery for BVS config interface match switch port.
 */
@Deprecated
public class FRQueryBvsMatchSwitchPort extends FlowReconcileQuery {
    /*switch DPID*/
    public DatapathId swId;
    /*List of ports:*/
    public List<String> matchPortList;

    public FRQueryBvsMatchSwitchPort() {
        super(ReconcileQueryEvType.BVS_INTERFACE_RULE_CHANGED_MATCH_SWITCH_PORT);
    }

    public FRQueryBvsMatchSwitchPort(DatapathId swId, List<String> portList) {
        this();
        this.swId = swId;
        this.matchPortList = portList;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = (int) (prime * result + swId.getLong());
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
        if (!(obj instanceof FRQueryBvsMatchSwitchPort)) {
            return false;
        }
        FRQueryBvsMatchSwitchPort other = (FRQueryBvsMatchSwitchPort) obj;
        if (swId.equals(other.swId)) return false;
        if (!matchPortList.equals(other.matchPortList)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Switch: ");
        builder.append(swId.toString());
        builder.append(", Match Port List:");
        builder.append(matchPortList);
        builder.append("]");
        return builder.toString();
    }
}
