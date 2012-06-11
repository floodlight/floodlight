package net.floodlightcontroller.flowcache;

import net.floodlightcontroller.flowcache.IFlowCacheService.FCQueryEvType;

/**
 * The Class PendingSwitchResp. This object is used to track the pending
 * responses to switch flow table queries.
 */
public class PendingSwitchResp {
    protected FCQueryEvType evType;

    public PendingSwitchResp(
            FCQueryEvType evType) {
        this.evType      = evType;
    }
    
    public FCQueryEvType getEvType() {
        return evType;
    }

    public void setEvType(FCQueryEvType evType) {
        this.evType = evType;
    }
}
