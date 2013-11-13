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
 * The Class for FlowReconcileQuery for BVS config interface match tag.
 */
public class FRQueryBvsMatchTag extends FlowReconcileQuery {
    public List<String> tag;

    public FRQueryBvsMatchTag() {
        super(ReconcileQueryEvType.BVS_INTERFACE_RULE_CHANGED_MATCH_TAG);
    }

    public FRQueryBvsMatchTag(List<String> tag) {
        this();
        this.tag = tag;
    }

    @Override
    public int hashCode() {
        final int prime = 347;
        int result = super.hashCode();
        result = prime * result + tag.hashCode();
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
        if (!(obj instanceof FRQueryBvsMatchTag)) {
            return false;
        }
        FRQueryBvsMatchTag other = (FRQueryBvsMatchTag) obj;
        if (! tag.equals(other.tag)) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("Tags: ");
        builder.append(tag);
        builder.append("]");
        return builder.toString();
    }
}
