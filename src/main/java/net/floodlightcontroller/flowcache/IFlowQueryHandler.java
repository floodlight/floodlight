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
