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
 * The Class for FlowReconcileQuery for BVS config priority change .
 */
public class FRQueryBvsPriority extends FlowReconcileQuery {
    /*BVS priority change*/
    public int lowP;   //lower priority
    public int highP;  //higher priority

    public FRQueryBvsPriority() {
        super(ReconcileQueryEvType.BVS_PRIORITY_CHANGED);
    }

    public FRQueryBvsPriority(int lowP, int highP) {
        this();
        this.lowP = lowP;
        this.highP = highP;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + lowP;
        result = prime * result + highP;
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
        if (!(obj instanceof FRQueryBvsPriority)) {
            return false;
        }
        FRQueryBvsPriority other = (FRQueryBvsPriority) obj;
        if (lowP != other.lowP) return false;
        if (highP != other.highP) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Lower Priority: ");
        builder.append(lowP);
        builder.append("Higher Priority: ");
        builder.append(highP);
        builder.append("]");
        return builder.toString();
    }
}
