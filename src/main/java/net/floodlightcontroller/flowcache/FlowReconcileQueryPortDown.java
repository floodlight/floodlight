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

import org.openflow.util.HexString;

/**
 * The Class for FlowReconcileQuery for link down event.
 */
public class FlowReconcileQueryPortDown extends FlowReconcileQuery {
    /*down port switch DPID*/
    public long swId;
    /*down port ID */
    public short port;

    public FlowReconcileQueryPortDown() {
        super(ReconcileQueryEvType.LINK_DOWN);
    }

    public FlowReconcileQueryPortDown(long swId, short portDown) {
        this();
        this.swId = swId;
        this.port = portDown;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + (int)swId;
        result = prime * result + port;
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
        if (!(obj instanceof FlowReconcileQueryPortDown)) {
            return false;
        }
        FlowReconcileQueryPortDown other = (FlowReconcileQueryPortDown) obj;
        if (swId != other.swId) return false;
        if (port != other.port) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Switch: ");
        builder.append(HexString.toHexString(swId));
        builder.append(", Port: ");
        builder.append(port);
        builder.append("]");
        return builder.toString();
    }
}
