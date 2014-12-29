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

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;

/**
 * The Class for FlowReconcileQuery for device move event.
 */
@Deprecated
public class FlowReconcileQueryDeviceMove extends FlowReconcileQuery {
    /* The moved device. */
    public IDevice deviceMoved;
    /*the oldAP the device moved from*/
    public SwitchPort[] oldAp;
    public FlowReconcileQueryDeviceMove() {
        super(ReconcileQueryEvType.DEVICE_MOVED);
    }

    public FlowReconcileQueryDeviceMove(IDevice deviceMoved, SwitchPort[] oldAp) {
        this();
        this.deviceMoved = deviceMoved;
        this.oldAp = oldAp.clone();
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + deviceMoved.getDeviceKey().hashCode();
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
        if (!(obj instanceof FlowReconcileQueryDeviceMove)) {
            return false;
        }
        FlowReconcileQueryDeviceMove other = (FlowReconcileQueryDeviceMove) obj;
        if (oldAp == null) {
            if (other.oldAp != null) return false;
        } else if (!Arrays.equals(oldAp, other.oldAp)) return false;
        if (deviceMoved == null) {
            if (other.deviceMoved != null) return false;
        } else if (!deviceMoved.equals(other.deviceMoved)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Device: ");
        builder.append(deviceMoved.getMACAddress().toString());
        builder.append(", Old Attachment Points:");
        builder.append(Arrays.toString(oldAp));
        builder.append("]");
        return builder.toString();
    }
}
