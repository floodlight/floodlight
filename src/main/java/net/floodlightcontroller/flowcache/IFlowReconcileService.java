/**
 * Provides Flow Reconcile service to other modules that need to reconcile
 * flows.
 */
package net.floodlightcontroller.flowcache;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFlowReconcileService extends IFloodlightService {
    /**
     * Add a flow reconcile listener
     * @param listener The module that can reconcile flows
     */
    public void addFlowReconcileListener(IFlowReconcileListener listener);

    /**
     * Remove a flow reconcile listener
     * @param listener The module that no longer reconcile flows
     */
    public void removeFlowReconcileListener(IFlowReconcileListener listener);
    
    /**
     * Remove all flow reconcile listeners
     */
    public void clearFlowReconcileListeners();
    
    /**
     * Reconcile flow. Returns false if no modified flow-mod need to be 
     * programmed if cluster ID is providced then pnly flows in the given 
     * cluster are reprogrammed
     *
     * @param ofmRcIn the ofm rc in
     */
    public void reconcileFlow(OFMatchReconcile ofmRcIn);

}
