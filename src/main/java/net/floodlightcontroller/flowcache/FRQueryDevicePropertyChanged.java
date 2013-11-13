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

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.util.MACAddress;

/**
 * The Class for FlowReconcileQuery for device property changed event.
 */
public class FRQueryDevicePropertyChanged extends FlowReconcileQuery {
    public IDevice device;
    public FRQueryDevicePropertyChanged() {
        super(ReconcileQueryEvType.DEVICE_PROPERTY_CHANGED);
    }

    public FRQueryDevicePropertyChanged(IDevice device) {
        this();
        this.device = device;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + device.getDeviceKey().hashCode();
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
        if (!(obj instanceof FRQueryDevicePropertyChanged)) {
            return false;
        }
        FRQueryDevicePropertyChanged other = (FRQueryDevicePropertyChanged) obj;
        if (! device.equals(other.device)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Device: ");
        builder.append(MACAddress.valueOf(device.getMACAddress()));
        builder.append("]");
        return builder.toString();
    }
}
