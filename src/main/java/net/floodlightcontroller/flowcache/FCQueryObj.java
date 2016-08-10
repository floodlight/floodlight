package net.floodlightcontroller.flowcache;

import java.util.Arrays;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryEvType;
import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryType;


/**
 * The Class FCQueryObj.
 */
public class FCQueryObj {

    /** The caller of the flow cache query. */
    public IFlowQueryHandler fcQueryHandler;
    /** The query type. */
    public FCQueryType queryType;
    /** The application instance name. */
    public String applInstName;
    /** The vlan Id. */
    public Short[] vlans;
    /** The destination device. */
    public IDevice dstDevice;
    /** The source device. */
    public IDevice srcDevice;
    /** The caller name */
    public String callerName;
    /** Event type that triggered this flow query submission */
    public FCQueryEvType evType;
    /** The caller opaque data. Returned unchanged in the query response
     * via the callback. The type of this object could be different for
     * different callers */
    public Object callerOpaqueObj;

    /**
     * Instantiates a new flow cache query object
     */
    public FCQueryObj() {
        queryType = FCQueryType.UNKNOWN;
    }

    @Override
    public String toString() {
        return "FCQueryObj [fcQueryCaller=" + fcQueryHandler
                + ", queryType=" + queryType + ", applInstName="
                + applInstName + ", vlans=" + Arrays.toString(vlans)
                + ", dstDevice=" + dstDevice + ", srcDevice="
                + srcDevice + ", callerName=" + callerName + ", evType="
                + evType + ", callerOpaqueObj=" + callerOpaqueObj + "]";
    }
}
