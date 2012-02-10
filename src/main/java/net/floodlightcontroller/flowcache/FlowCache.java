/**
 * 
 */
package net.floodlightcontroller.flowcache;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;


/**
 * @author subrata
 *
 */
public class FlowCache {

    public int     hitCnt;
    public boolean flowCacheServiceOn;

    // Constructor
    public FlowCache() {
        flowCacheServiceOn = false;
    }

    public boolean isFlowCacheServiceOn() {
        return flowCacheServiceOn;
    }

    public void setFlowCacheServiceOn(boolean flowCacheServiceOn) {
        this.flowCacheServiceOn = flowCacheServiceOn;
    }

    public int getHitCnt() {
        return hitCnt;
    }

    public boolean addFlow(String applInst, OFMatch ofm, Long cookie,
            short priority, byte action) {
        hitCnt++;
        return true;
    }

    public boolean addFlow(String string, OFFlowMod fm) {
        hitCnt++;
        return true;
    }

    public void startUp() {
        // Service is off by default
        flowCacheServiceOn = false;
    }

    public void shutDown() {
        flowCacheServiceOn = false;
    }
}
