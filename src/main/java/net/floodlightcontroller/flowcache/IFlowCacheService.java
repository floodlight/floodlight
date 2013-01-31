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

import org.openflow.protocol.OFMatchWithSwDpid;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.FloodlightContextStore;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * The Interface IFlowCache.
 * <p>
 * public interface APIs to Big Switch Flow-Cache Service. Flow-Cache maintains
 * the network-level flows that are currently deployed in the underlying 
 * network. The flow cache can be queried using various filters by using the
 * corresponding APIs.
 * 
 * @author subrata
 *
 */
public interface IFlowCacheService extends IFloodlightService {

    public static final String FLOWCACHE_APP_NAME = 
        "net.floodlightcontroller.flowcache.appName";
    public static final String FLOWCACHE_APP_INSTANCE_NAME = 
        "net.floodlightcontroller.flowcache.appInstanceName";

    /**
     * The flow cache query event type indicating the event that triggered the
     * query. The callerOpaqueObj can be keyed based on this event type
     */
    public static enum FCQueryEvType {
        /** The GET query. Flows need not be reconciled for this query type */
        GET,
        /** A new App was added. */
        APP_ADDED,
        /** An App was deleted. */
        APP_DELETED,
        /** Interface rule of an app was modified */
        APP_INTERFACE_RULE_CHANGED,
        /** Some App configuration was changed */
        APP_CONFIG_CHANGED,
        /** An ACL was added */
        ACL_ADDED,
        /** An ACL was deleted */
        ACL_DELETED,
        /** An ACL rule was added */
        ACL_RULE_ADDED,
        /** An ACL rule was deleted */
        ACL_RULE_DELETED,
        /** ACL configuration was changed */
        ACL_CONFIG_CHANGED,
        /** device had moved to a different port in the network */
        DEVICE_MOVED,
        /** device's property had changed, such as tag assignment */
        DEVICE_PROPERTY_CHANGED,
        /** Link down */
        LINK_DOWN,
        /** Periodic scan of switch flow table */
        PERIODIC_SCAN,
    }
    
    /**
     * A FloodlightContextStore object that can be used to interact with the 
     * FloodlightContext information about flowCache.
     */
    public static final FloodlightContextStore<String> fcStore = 
        new FloodlightContextStore<String>();
    
    /**
     * Submit a flow cache query with query parameters specified in FCQueryObj
     * object. The query object can be created using one of the newFCQueryObj 
     * helper functions in IFlowCache interface. 
     * <p>
     * The queried flows are returned via the flowQueryRespHandler() callback 
     * that the caller must implement. The caller can match the query with
     * the response using unique callerOpaqueData which remains unchanged
     * in the request and response callback.
     *
     * @see  com.bigswitch.floodlight.flowcache#flowQueryRespHandler
     * @param query the flow cache query object as input
     * 
     */
    public void submitFlowCacheQuery(FCQueryObj query);

    /**
     * Deletes all flows in the flow cache for which the source switch
     * matches the given switchDpid. 
     * 
     * @param switchDpid Data-path identifier of the source switch
     */
    public void deleteFlowCacheBySwitch(long switchDpid);

    /**
     * Flush Local Counter Updates
     *
     */
    public void updateFlush();
    
    /**
     * Add a flow to the flow-cache - called when a flow-mod is about to be
     * written to a set of switches. If it returns false then it should not
     * be written to the switches. If it returns true then the cookie returned
     * should be used for the flow mod sent to the switches.
     *
     * @param appInstName Application instance name
     * @param ofm openflow match object
     * @param cookie openflow-mod cookie
     * @param swPort SwitchPort object
     * @param priority openflow match priority
     * @param action action taken on the matched packets (PERMIT or DENY)
     * @return true:  flow should be written to the switch(es)
     *         false: flow should not be written to the switch(es). false is
     *                returned, for example, when the flow was recently
     *                written to the flow-cache and hence it is dampened to
     *                avoid frequent writes of the same flow to the switches
     *                This case can typically arise for the flows written at the
     *                internal ports as they are heavily wild-carded.
     */
    public boolean addFlow(String appInstName, OFMatchWithSwDpid ofm, 
                           Long cookie, long srcSwDpid, 
                           short inPort, short priority, byte action);

    /**
     * Add a flow to the flow-cache - called when a flow-mod is about to be
     * written to a set of switches. If it returns false then it should not
     * be written to the switches. If it returns true then the cookie returned
     * should be used for the flow mod sent to the switches.
     *
     * @param cntx the cntx
     * @param ofm the ofm
     * @param cookie the cookie
     * @param swPort the sw port
     * @param priority the priority
     * @param action the action
     * @return true:  flow should be written to the switch(es)
     * false: flow should not be written to the switch(es). false is
     * returned, for example, when the flow was recently
     * written to the flow-cache and hence it is dampened to
     * avoid frequent writes of the same flow to the switches
     * This case can typically arise for the flows written at the
     * internal ports as they are heavily wild-carded.
     */
    public boolean addFlow(FloodlightContext cntx, OFMatchWithSwDpid ofm, 
                           Long cookie, SwitchPort swPort, 
                           short priority, byte action);

    /**
     * Move the specified flow from its current application instance to a 
     * different application instance. This API can be used when a flow moves
     * to a different application instance when the application instance
     * configuration changes or when a device moves to a different part in
     * the network that belongs to a different application instance.
     * <p>
     * Note that, if the flow was not found in the current application 
     * instance then the flow is not moved to the new application instance.
     * 
     * @param ofMRc the object containing the flow match and new application
     * instance name.
     * @return true is the flow was found in the flow cache in the current 
     * application instance; false if the flow was not found in the flow-cache
     * in the current application instance.
     */
    public boolean moveFlowToDifferentApplInstName(OFMatchReconcile ofMRc);

    /**
     * Delete all flow from the specified switch
     * @param sw
     */
    public void deleteAllFlowsAtASourceSwitch(IOFSwitch sw);
    
    /**
     * Post a request to update flowcache from a switch.
     * This is an asynchronous operation.
     * It queries the switch for stats and updates the flowcache asynchronously
     * with the response.
     * @param swDpid
     * @param delay_ms
     */
    public void querySwitchFlowTable(long swDpid);
}
