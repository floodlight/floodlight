/**
 * 
 */
package net.floodlightcontroller.flowcache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;

/**
 * @author subrata
 *
 */
public class FlowCache {

    private int hitCnt;

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
}
