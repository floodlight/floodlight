package net.floodlightcontroller.core.internal;

import java.util.Map.Entry;

import javax.annotation.Nonnull;

import java.util.Date;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.internal.Controller.IUpdate;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A utility class to manage the <i>controller roles</i>.
 *
 * A utility class to manage the <i>controller roles</i>  as opposed
 * to the switch roles. The class manages the controllers current role,
 * handles role change requests, and maintains the list of connected
 * switch(-channel) so it can notify the switches of role changes.
 *
 * We need to ensure that every connected switch is always send the
 * correct role. Therefore, switch add, sending of the initial role, and
 * changing role need to use mutexes to ensure this. This has the ugly
 * side-effect of requiring calls between controller and OFChannelHandler
 *
 * This class is fully thread safe. Its method can safely be called from
 * any thread.
 *
 * @author gregor
 *
 */
public class RoleManager {
    private volatile RoleInfo currentRoleInfo;
    private final Controller controller;
    private final IShutdownService shutdownService;
    private final RoleManagerCounters counters;

    private static final Logger log =
            LoggerFactory.getLogger(RoleManager.class);

    /**
     * @param role initial role
     * @param roleChangeDescription initial value of the change description
     * @throws NullPointerException if role or roleChangeDescription is null
     */
    public RoleManager(@Nonnull Controller controller,
            @Nonnull IShutdownService shutdownService,
            @Nonnull HARole role,
            @Nonnull String roleChangeDescription) {
        Preconditions.checkNotNull(controller, "controller must not be null");
        Preconditions.checkNotNull(role, "role must not be null");
        Preconditions.checkNotNull(roleChangeDescription, "roleChangeDescription must not be null");
        Preconditions.checkNotNull(shutdownService, "shutdownService must not be null");

        this.currentRoleInfo = new RoleInfo(role,
                                       roleChangeDescription,
                                       new Date());
        this.controller = controller;
        this.shutdownService = shutdownService;
        this.counters = new RoleManagerCounters(controller.getDebugCounter());
    }

    /**
     * Re-assert a role for the given channel handler.
     *
     * The caller specifies the role that should be reasserted. We only
     * reassert the role if the controller's current role matches the
     * reasserted role and there is no role request for the reasserted role
     * pending.
     * @param ofSwitchHandshakeHandler The OFChannelHandler on which we should reassert.
     * @param role The role to reassert
     */
    public synchronized void reassertRole(OFSwitchHandshakeHandler ofSwitchHandshakeHandler, HARole role) {
        // check if the requested reassertion actually makes sense
        if (this.getRole() != role)
            return;
        ofSwitchHandshakeHandler.sendRoleRequestIfNotPending(this.getRole().getOFRole());
    }

    /**
     * Set the controller's new role and notify switches.
     *
     * This method updates the controllers current role and notifies all
     * connected switches of the new role is different from the current
     * role. We dampen calls to this method. See class description for
     * details.
     *
     * @param role The new role.
     * @param roleChangeDescription A textual description of why the role
     * was changed. For information purposes only.
     * @throws NullPointerException if role or roleChangeDescription is null
     */
    public synchronized void setRole(@Nonnull HARole role, @Nonnull String roleChangeDescription) {
        Preconditions.checkNotNull(role, "role must not be null");
        Preconditions.checkNotNull(roleChangeDescription, "roleChangeDescription must not be null");

        if (role == getRole()) {
            counters.setSameRole.increment();
            log.debug("Received role request for {} but controller is "
                    + "already {}. Ignoring it.", role, this.getRole());
            return;
        }

        if (this.getRole() == HARole.STANDBY && role == HARole.ACTIVE) {
            // At this point we are guaranteed that we will execute the code
            // below exactly once during the lifetime of this process! And
            // it will be a to MASTER transition
            counters.setRoleMaster.increment();
        }

        log.info("Received role request for {} (reason: {})."
                + " Initiating transition", role, roleChangeDescription);

        currentRoleInfo =
                new RoleInfo(role, roleChangeDescription, new Date());

        controller.addUpdateToQueue(new HARoleUpdate(role));
        controller.addUpdateToQueue(new SwitchRoleUpdate(role));

    }

    @SuppressFBWarnings(value="UG_SYNC_SET_UNSYNC_GET",
                        justification = "setter is synchronized for mutual exclusion, "
                                + "currentRoleInfo is volatile, so no sync on getter needed")
    public synchronized HARole getRole() {
        return currentRoleInfo.getRole();
    }

    public synchronized OFControllerRole getOFControllerRole() {
        return getRole().getOFRole();
    }

    /**
     * Return the RoleInfo object describing the current role.
     *
     * Return the RoleInfo object describing the current role. The
     * RoleInfo object is used by REST API users.
     * @return the current RoleInfo object
     */
    public RoleInfo getRoleInfo() {
        return currentRoleInfo;
    }

    private void attemptActiveTransition() {
         if(!switchesHaveAnotherMaster()){
             // No valid cluster controller connections found, become ACTIVE!
             setRole(HARole.ACTIVE, "Leader election assigned ACTIVE role");
         }
     }

    /**
     * Iterates over all the switches and checks to see if they have controller
     * connections that points towards another master controller.
     * @return
     */
    private boolean switchesHaveAnotherMaster() {
        IOFSwitchService switchService = controller.getSwitchService();

        for(Entry<DatapathId, IOFSwitch> switchMap : switchService.getAllSwitchMap().entrySet()){
            IOFSwitchBackend sw = (IOFSwitchBackend) switchMap.getValue();
            if(sw.hasAnotherMaster()){
                return true;
            }
        }
        return false;
    }

    public void notifyControllerConnectionUpdate() {
        if(currentRoleInfo.getRole() != HARole.ACTIVE) {
            attemptActiveTransition();
        }
    }

    /**
     * Update message indicating controller's role has changed.
     * RoleManager, which enqueues these updates guarantees that we will
     * only have a single transition from SLAVE to MASTER.
     *
     * When the role update from master to slave is complete, the HARoleUpdate
     * will terminate floodlight.
     */
    private class HARoleUpdate implements IUpdate {
        private final HARole newRole;
        public HARoleUpdate(HARole newRole) {
            this.newRole = newRole;
        }

        @Override
        public void dispatch() {
            if (log.isDebugEnabled()) {
                log.debug("Dispatching HA Role update newRole = {}",
                          newRole);
            }
            for (IHAListener listener : Controller.haListeners.getOrderedListeners()) {
                if (log.isTraceEnabled()) {
                    log.trace("Calling HAListener {} with transitionTo{}",
                              listener.getName(), newRole);
                }
                switch(newRole) {
                    case ACTIVE:
                        listener.transitionToActive();
                        break;
                    case STANDBY:
                        listener.transitionToStandby();
                        break;
                }
           }

           controller.setNotifiedRole(newRole);

           if (newRole == HARole.STANDBY && Controller.shutdownOnTransitionToStandby) {
               String reason = String.format("Received role request to "
                       + "transition from ACTIVE to STANDBY (reason: %s)",
                       getRoleInfo().getRoleChangeDescription());
               shutdownService.terminate(reason, 0);
           }
        }
    }

    public class SwitchRoleUpdate implements IUpdate {
        private final HARole role;

        public SwitchRoleUpdate(HARole role) {
            this.role = role;
        }

        @Override
        public void dispatch() {
            if (log.isDebugEnabled()) {
                log.debug("Dispatching switch role update newRole = {}, switch role = {}",
                          this.role, this.role.getOFRole());
            }

            for (OFSwitchHandshakeHandler h: controller.getSwitchService().getSwitchHandshakeHandlers())
                h.sendRoleRequest(this.role.getOFRole());
        }
    }

    public RoleManagerCounters getCounters() {
        return this.counters;
    }
}
