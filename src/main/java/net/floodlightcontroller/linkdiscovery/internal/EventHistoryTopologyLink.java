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

package net.floodlightcontroller.linkdiscovery.internal;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/***
 * Topology link up/down event history related classes and members
 * @author subrata
 *
 */
public class EventHistoryTopologyLink {
    // The following fields are not stored as String to save memory
    // They should be converted to appropriate human-readable strings by 
    // the front end (e.g. in cli in Python)
    public long     srcSwDpid;
    public long     dstSwDpid;
    public int      srcSwport;
    public int      dstSwport;
    public String   linkType;
    public String   reason;
    
    @JsonProperty("Source-Switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getSrcSwDpid() {
        return srcSwDpid;
    }
    @JsonProperty("Dest-Switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getDstSwDpid() {
        return dstSwDpid;
    }
    @JsonProperty("SrcPort")
    public int getSrcSwport() {
        return srcSwport;
    }
    @JsonProperty("DstPort")
    public int getDstSwport() {
        return dstSwport;
    }
    @JsonProperty("LinkType")
    public String getLinkType() {
        return linkType;
    }
    @JsonProperty("Reason")
    public String getReason() {
        return reason;
    }
    
    
}
