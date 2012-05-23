package net.floodlightcontroller.flowcache;

import java.util.ArrayList;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IListener;
import org.openflow.protocol.OFType;

/**
 * The Interface IFlowReconciler.
 *
 * @author subrata
 */
public interface IFlowReconcileListener extends IListener<OFType> {
    /**
     * Given an input OFMatch, this method applies the policy of the reconciler
     * and returns a the same input OFMatch structure modified. Additional
     * OFMatches, if needed, are returned in OFMatch-list. All the OFMatches
     * are assumed to have "PERMIT" action.
     *
     * @param ofmRcList  input flow matches, to be updated to be consistent with
     *                   the policies of this reconciler 
     *                   Additional OFMatch-es can be added to the "list" as
     *                   needed. 
     *                   For example after a new ACL application, one flow-match
     *                   may result in multiple flow-matches
     *                   The method must also update the ReconcileAction
     *                   member in ofmRcList entries to indicate if the
     *                   flow needs to be modified, deleted or left unchanged
     *                   OR of a new entry is to be added after flow 
     *                   reconciliation
     *
     *
     * @return   Command.CONTINUE if the OFMatch should be sent to the
     *           next flow reconciler. 
     *           Command.STOP if the OFMatch shouldn't be processed
     *           further. In this case the no reconciled flow-mods would 
     *           be programmed
     */
    public Command reconcileFlows(ArrayList<OFMatchReconcile> ofmRcList, FloodlightContext cntx);

}
