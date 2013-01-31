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

package net.floodlightcontroller.perfmon;

import java.util.List;

import org.openflow.protocol.OFMessage;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IPktInProcessingTimeService extends IFloodlightService {

    /**
     * Creates time buckets for a set of modules to measure their performance
     * @param listeners The message listeners to create time buckets for
     */
    public void bootstrap(List<IOFMessageListener> listeners);
    
    /**
     * Stores a timestamp in ns. Used right before a service handles an
     * OF message. Only stores if the service is enabled.
     */
    public void recordStartTimeComp(IOFMessageListener listener);
    
    public void recordEndTimeComp(IOFMessageListener listener);
    
    public void recordStartTimePktIn();
    
    public void recordEndTimePktIn(IOFSwitch sw, OFMessage m, FloodlightContext cntx);
    
    public boolean isEnabled();
    
    public void setEnabled(boolean enabled);
    
    public CumulativeTimeBucket getCtb();
}
