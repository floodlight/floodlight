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
import net.floodlightcontroller.core.FloodlightContextStore;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * The Interface IFlowReconcileEngine.
 *
 * public interface APIs to Big Switch Flow-Reconcile Service. FlowReconcileEngine queries
 * the network-level flows that are currently deployed in the underlying
 * network. The flow reconcile engine can be triggered using various filters by using the
 * corresponding APIs.
 *
 * @author MeiYang
 */
public interface IFlowReconcileEngineService extends IFloodlightService {
    /**
     * A FloodlightContextStore object that can be used to interact with the
     * FloodlightContext information about flowCache.
     */
    public static final FloodlightContextStore<String> fcStore =
        new FloodlightContextStore<String>();
    public static final String FLOWRECONCILE_APP_INSTANCE_NAME = "net.floodlightcontroller.flowcache.appInstanceName";
    /**
     * Submit a network flow query with query parameters specified in ReconcileQueryObj
     * object. The query object can be created using one of the new ReconcileQueryObj
     * helper functions in IFlowCache interface.
     *
     * @param query the flow cache query object as input
     */
    public void submitFlowQueryEvent(FlowReconcileQuery query);

    /**
     * Flush Local Counter Updates
     *
     */
    public void updateFlush();

    public void init(FloodlightModuleContext fmc) throws FloodlightModuleException;

    public void startUp(FloodlightModuleContext fmc);
}
