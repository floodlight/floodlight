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

/**
 * Provides Flow Reconcile service to other modules that need to reconcile
 * flows.
 */
package net.floodlightcontroller.flowcache;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.flowcache.PriorityPendingQueue.EventPriority;

@Deprecated
public interface IFlowReconcileService extends IFloodlightService {
    /**
     * Add a flow reconcile listener
     * @param listener The module that can reconcile flows
     */
    public void addFlowReconcileListener(IFlowReconcileListener listener);

    /**
     * Remove a flow reconcile listener
     * @param listener The module that no longer reconcile flows
     */
    public void removeFlowReconcileListener(IFlowReconcileListener listener);
    
    /**
     * Remove all flow reconcile listeners
     */
    public void clearFlowReconcileListeners();
    
    /**
     * Reconcile flow. Returns false if no modified flow-mod need to be 
     * programmed if cluster ID is providced then pnly flows in the given 
     * cluster are reprogrammed
     *
     * @param ofmRcIn the ofm rc in
     */
    public void reconcileFlow(OFMatchReconcile ofmRcIn, EventPriority priority) ;

    public void init(FloodlightModuleContext context)  throws FloodlightModuleException ;
    public void startUp(FloodlightModuleContext context)  throws FloodlightModuleException ;
}
