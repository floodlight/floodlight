package net.floodlightcontroller.flowcache;

import java.util.ArrayList;

/**
 * Object to return flows in response to a query message to BigFlowCache.
 * This object is passed in the flowQueryRespHandler() callback.
 */
public class FlowCacheQueryResp {

    /** query object provided by the caller, returned unchanged. */
    public FCQueryObj  queryObj;
    /** 
     * Set to true if more flows could be returned for this query in
     * additional callbacks. Set of false in the last callback for the
     * query. 
     */
    public boolean     moreFlag;
    /** 
     * The flow list. If there are large number of flows to be returned
     * then they may be returned in multiple callbacks.
     */
    public ArrayList<QRFlowCacheObj> qrFlowCacheObjList;

    /**
     * Instantiates a new big flow cache query response.
     *
     * @param query the flow cache query object as given by the caller of
     * flow cache submit query API.
     */
    public FlowCacheQueryResp(FCQueryObj query) {
        qrFlowCacheObjList = new ArrayList<QRFlowCacheObj>();
        queryObj    = query;
        moreFlag    = false;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String s = queryObj.toString();
        if (moreFlag) {
            s += "; moreFlasg=True";
        } else {
            s += "; moreFlag=False";
        }
        s += "; FlowCount=" + Integer.toString(qrFlowCacheObjList.size());
        return s;
    }
}
