package net.floodlightcontroller.flowcache;

import java.util.Arrays;

import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryEvType;


/**
 * The Class FCQueryObj.
 */
public class FCQueryObj {

    /** The caller of the flow cache query. */
    public IFlowQueryHandler fcQueryHandler;
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
    public FCQueryObj(IFlowQueryHandler fcQueryHandler,
            String        applInstName,
            Short         vlan,
            IDevice       srcDevice,
            IDevice       dstDevice,
            String        callerName,
            FCQueryEvType evType,
            Object        callerOpaqueObj) {
        this.fcQueryHandler    = fcQueryHandler;
        this.applInstName     = applInstName;
        this.srcDevice        = srcDevice;
        this.dstDevice        = dstDevice;
        this.callerName       = callerName;
        this.evType           = evType;
        this.callerOpaqueObj  = callerOpaqueObj;
        
        if (vlan != null) {
        	this.vlans = new Short[] { vlan };
        } else {
	        if (srcDevice != null) {
	        	this.vlans = srcDevice.getVlanId();
	        } else if (dstDevice != null) {
	            this.vlans = dstDevice.getVlanId();
	        }
        }
    }

    @Override
    public String toString() {
        return "FCQueryObj [fcQueryCaller=" + fcQueryHandler
                + ", applInstName="
                + applInstName + ", vlans=" + Arrays.toString(vlans)
                + ", dstDevice=" + dstDevice + ", srcDevice="
                + srcDevice + ", callerName=" + callerName + ", evType="
                + evType + ", callerOpaqueObj=" + callerOpaqueObj + "]";
    }
}
