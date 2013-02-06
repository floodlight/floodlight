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

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryEvType;

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
    public void reconcileFlow(OFMatchReconcile ofmRcIn);
    
    /**
     * Updates the flows to a device after the device moved to a new location
     * <p>
     * Queries the flow-cache to get all the flows destined to the given device.
     * Reconciles each of these flows by potentially reprogramming them to its
     * new attachment point
     *
     * @param device      device that has moved
     * @param handler	  handler to process the flows
     * @param fcEvType    Event type that triggered the update
     *
     */
    public void updateFlowForDestinationDevice(IDevice device,
            IFlowQueryHandler handler,
    		FCQueryEvType fcEvType);
    
    /**
     * Updates the flows from a device
     * <p>
     * Queries the flow-cache to get all the flows source from the given device.
     * Reconciles each of these flows by potentially reprogramming them to its
     * new attachment point
     *
     * @param device      device where the flow originates
     * @param handler	  handler to process the flows
     * @param fcEvType    Event type that triggered the update
     *
     */
    public void updateFlowForSourceDevice(IDevice device,
            IFlowQueryHandler handler,
    		FCQueryEvType fcEvType);

    /**
     * Generic flow query handler to insert FlowMods into the reconcile pipeline.
     * @param flowResp
     */
    public void flowQueryGenericHandler(FlowCacheQueryResp flowResp);
}
