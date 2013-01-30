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

public interface IFlowQueryHandler {
    /**
     * This callback function is called in response to a flow query request
     * submitted to the flow cache service. The module handling this callback
     * can be different from the one that submitted the query. In the flow
     * query object used for submitting the flow query, the identity of the
     * callback handler is passed. When flow cache service has all or some
     * of the flows that needs to be returned then this callback is called
     * for the appropriate module. The respone contains a boolean more flag 
     * that indicates if there are additional flows that may be returned
     * via additional callback calls.
     *
     * @param resp the response object containing the original flow query 
     * object, partial or complete list of flows that we queried and some 
     * metadata such as the more flag described aboce.
     *
     */
    public void flowQueryRespHandler(FlowCacheQueryResp resp);
}
