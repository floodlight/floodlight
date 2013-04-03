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
import net.floodlightcontroller.flowcache.PriorityPendingQueue.EventPriority;
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

/*    public static final String FLOWCACHE_APP_NAME =
        "net.floodlightcontroller.flowcache.appName";
    public static final String FLOWCACHE_APP_INSTANCE_NAME =
        "net.floodlightcontroller.flowcache.appInstanceName";
*/
    /**
     * The flow reconcile engine trigger event type indicating the event that triggered the
     * query. The callerOpaqueObj can be keyed based on this event type
     */
    public static enum FCQueryEvType {
        /** The GET query. Flows need not be reconciled for this query type */
        GET,
        /** A new App was added. */
        BVS_ADDED,
        /** An App was deleted. */
        BVS_DELETED,
        /** Interface rule of an app was modified */
        BVS_INTERFACE_RULE_CHANGED,
        BVS_INTERFACE_RULE_CHANGED_MATCH_SWITCH_PORT,
        BVS_INTERFACE_RULE_CHANGED_MATCH_MAC,
        BVS_INTERFACE_RULE_CHANGED_MATCH_VLAN,
        BVS_INTERFACE_RULE_CHANGED_MATCH_IPSUBNET,
        BVS_INTERFACE_RULE_CHANGED_MATCH_TAG,
        /** Some App configuration was changed */
        BVS_PRIORITY_CHANGED,
        /** ACL configuration was changed */
        ACL_CONFIG_CHANGED,
        /** VRS routing rule was changed */
        VRS_ROUTING_RULE_CHANGED,
        /** device had moved to a different port in the network */
        DEVICE_MOVED,
        /** device's property had changed, such as tag assignment */
        DEVICE_PROPERTY_CHANGED,
        /** Link down */
        LINK_DOWN,
        /** second round query caused by rewrite flags set */
        REWRITE_QUERY,
    }
    /**
     * A FloodlightContextStore object that can be used to interact with the
     * FloodlightContext information about flowCache.
     */
    public static final FloodlightContextStore<String> fcStore =
        new FloodlightContextStore<String>();
    /**
     * Submit a network flow query with query parameters specified in FCQueryObj
     * object. The query object can be created using one of the newFCQueryObj
     * helper functions in IFlowCache interface.
     *
     * The queried flows are returned via the flowQueryRespHandler() callback
     * that the caller must implement. The caller can match the query with
     * the response using unique callerOpaqueData which remains unchanged
     * in the request and response callback.
     *
     * @see  com.bigswitch.floodlight.flowcache#flowQueryRespHandler
     * @param query the flow cache query object as input
     */
    public void submitFlowQueryEvent(FCQueryObj query, EventPriority priority);

    /**
     * Flush Local Counter Updates
     *
     */
    public void updateFlush();
    public void querySwitchFlowTable(long swDpid);
}
