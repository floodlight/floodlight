package net.floodlightcontroller.core.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;

/** 
 * This class handles sending of RoleRequest messages to all connected switches.
 * 
 * Handling Role Requests is tricky. Roles are hard state on the switch and
 * we can't query it so we need to make sure that we have consistent states
 * on the switches. Whenever we send a role request to the set of connected 
 * switches we need to make sure that we've sent the request to all of them 
 * before we process the next change request. If a new switch connects, we 
 * need to send it the current role and need to make sure that the current
 * role doesn't change while we are doing it. We achieve this by synchronizing
 * all these actions on Controller.roleChanger
 * On the receive side: we need to make sure that we receive a reply for each 
 * request we send and that the reply is consistent with the request we sent. 
 * We'd also like to send the role request to the switch asynchronously in a
 * separate thread so we don't block the REST API or other callers.
 * 
 * There are potential ways to relax these synchronization requirements:
 * - "Generation ID" for each role request. However, this would be most useful
 *   if it were global for the whole cluster
 * - Regularly resend the controller's current role. Don't know whether this
 *   might have adverse effects on the switch. 
 *   
 * Caveats:
 * - No way to know if another controller (not in our controller cluster) 
 *   sends MASTER requests to connected switches. Then we would drop to
 *   slave role without knowing it. Could regularly resend the current role. 
 *   Ideally the switch would notify us if it demoted us. What happens if
 *   the other controller also regularly resends the same role request? 
 *   Or if the health check determines that
 *   a controller is dead but the controller is still talking to switches (maybe
 *   just its health check failed) and resending the master role request.... 
 *   We could try to detect if a switch demoted us to slave even if we think
 *   we are master (error messages on packet outs, e.g., when sending LLDPs)
 * 
 *
 * The general model of Role Request handling is as follows:
 * 
 * - All role request messages are handled by this class. Class Controller 
 *   submits a role change request and the request gets queued. submitRequest
 *   takes a Collection of switches to which to send the request. We make a copy
 *   of this list. 
 * - A thread takes these change requests from the queue and sends them to 
 *   all the switches (using our copy of the switch list). 
 * - The OFSwitchImpl sends the request over the wire and puts the request
 *   into a queue of pending request (storing xid and role). We start a timeout 
 *   to make sure we eventually receive a reply from the switch. We use a single
 *   timeout for each request submitted using submitRequest()
 * - After the timeout triggers we go over the list of switches again and
 *   check that a response has been received (by checking the head of the 
 *   OFSwitchImpl's queue of pending requests)
 * - We handle requests and timeouts in the same thread. We use a priority queue
 *   to schedule them so we are guaranteed that they are processed in 
 *   the same order as they are submitted. If a request times out we drop
 *   the connection to this switch. 
 * - Since we decouple submission of role change requests and actually sending
 *   them we cannot check a received role reply against the controller's current 
 *   role because the controller's current role could have changed again. 
 * - Receiving Role Reply messages is handled by OFChannelHandler and
 *   OFSwitchImpl directly. The OFSwitchImpl checks if the received request 
 *   is as expected (xid and role match the head of the pending queue in 
 *   OFSwitchImpl). If so
 *   the switch updates its role. Otherwise the connection is dropped. If this
 *   is the first reply, the SWITCH_SUPPORTS_NX_ROLE attribute is set.
 *   Next, we call addSwitch(), removeSwitch() to update the list of active
 *   switches if appropriate.
 * - If we receive an Error indicating that roles are not supported by the 
 *   switch, we set the SWITCH_SUPPORTS_NX_ROLE to false. We keep the 
 *   switch connection alive while in MASTER and EQUAL role. 
 *   (TODO: is this the right behavior for EQUAL??). If the role changes to
 *   SLAVE the switch connection is dropped (remember: only if the switch
 *   doesn't support role requests)  
 *   The expected behavior is that the switch will probably try to reconnect
 *   repeatedly (with some sort of exponential backoff), but after a  while 
 *   will give-up and move on to the next controller-IP configured on the 
 *   switch. This is the serial failover mechanism from OpenFlow spec v1.0.
 *   
 * New switch connection:
 * - Switch handshake is done without sending any role request messages.
 * - After handshake completes, switch is added to the list of connected switches
 *   and we send the first role request message if role
 *   requests are enabled. If roles are disabled automatically promote switch to
 *   active switch list and clear FlowTable.
 * - When we receive the first reply we proceed as above. In addition, if
 *   the role request is for MASTER we wipe the flow table. We do not wipe
 *   the flow table if the switch connected while role supported was disabled
 *   on the controller. 
 *
 */
public class RoleChanger {
    // FIXME: Upon closer inspection DelayQueue seems to be somewhat broken. 
    // We are required to implement a compareTo based on getDelay() and 
    // getDelay() must return the remaining delay, thus it needs to use the 
    // current time. So x1.compareTo(x1) can never return 0 as some time
    // will have passed between evaluating both getDelays(). This is even worse
    // if the thread happens to be preempted between calling the getDelay()
    // For the time being we enforce a small delay between subsequent
    // role request messages and hope that's long enough to not screw up
    // ordering. In the long run we might want to use two threads and two queues
    // (one for requests, one for timeouts)
    // Sigh. 
    protected DelayQueue<RoleChangeTask> pendingTasks;
    protected long lastSubmitTime;
    protected Thread workerThread;
    protected long timeout;
    protected ConcurrentHashMap<IOFSwitch, LinkedList<PendingRoleRequestEntry>>
                pendingRequestMap;
    private Controller controller;
    
    protected static long DEFAULT_TIMEOUT = 5L*1000*1000*1000L; // 5s
    protected static Logger log = LoggerFactory.getLogger(RoleChanger.class);
    private static final String HA_CHECK_SWITCH = 
            "Check the health of the indicated switch.  If the problem " +
            "persists or occurs repeatedly, it likely indicates a defect " +
            "in the switch HA implementation.";
    
    /** 
     * A queued task to be handled by the Role changer thread. 
     */
    protected static class RoleChangeTask implements Delayed {
        protected enum Type { 
            /** This is a request. Dispatch the role update to switches */
            REQUEST,
            /** This is a timeout task. Check if all switches have 
                correctly replied to the previously dispatched role request */
            TIMEOUT
        }
        // The set of switches to work on
        public Collection<IOFSwitch> switches;
        public Role role;
        public Type type;
        // the time when the task should run as nanoTime() 
        public long deadline;
        public RoleChangeTask(Collection<IOFSwitch> switches, Role role, long deadline) {
            this.switches = switches;
            this.role = role;
            this.type = Type.REQUEST;
            this.deadline = deadline;
        }
        @Override
        public int compareTo(Delayed o) {
            Long timeRemaining = getDelay(TimeUnit.NANOSECONDS);
            return timeRemaining.compareTo(o.getDelay(TimeUnit.NANOSECONDS));
        }
        @Override
        public long getDelay(TimeUnit tu) {
            long timeRemaining = deadline - System.nanoTime();
            return tu.convert(timeRemaining, TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Per-switch list of pending HA role requests.
     * @author shudongz
     */
    protected static class PendingRoleRequestEntry {
        protected int xid;
        protected Role role;
        // cookie is used to identify the role "generation". roleChanger uses
        protected long cookie;
        public PendingRoleRequestEntry(int xid, Role role, long cookie) {
            this.xid = xid;
            this.role = role;
            this.cookie = cookie;
        }
    }
    
    @LogMessageDoc(level="ERROR",
                   message="RoleRequestWorker task had an uncaught exception.",
                   explanation="An unknown occured while processing an HA " +
                   		"role change event.",
                   recommendation=LogMessageDoc.GENERIC_ACTION)                              
    protected class RoleRequestWorker extends Thread  {
        @Override
        public void run() {
            RoleChangeTask t;
            boolean interrupted = false;
            log.trace("RoleRequestWorker thread started");
            try {
                while (true) {
                    try {
                        t = pendingTasks.poll();
                        if (t == null) {
                            // Notify when there is no immediate tasks to run
                            // For the convenience of RoleChanger unit tests
                            synchronized (pendingTasks) {
                                pendingTasks.notifyAll();
                            }
                            t = pendingTasks.take();
                        }
                    } catch (InterruptedException e) {
                        // see http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html
                        interrupted = true;
                        continue;
                    }
                    if (t.type == RoleChangeTask.Type.REQUEST) {
                        t.deadline += timeout;
                        sendRoleRequest(t.switches, t.role, t.deadline);
                        // Queue the timeout
                        t.type = RoleChangeTask.Type.TIMEOUT;
                        pendingTasks.put(t);
                    }
                    else {
                        verifyRoleReplyReceived(t.switches, t.deadline);
                    }
                }
            }
            catch (Exception e) {
                // Should never get here
                log.error("RoleRequestWorker task had an uncaught exception. ", 
                          e);
            }
            finally {
                // Be nice in case we earlier caught InterruptedExecution
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
        } // end loop
    }
    
    protected class HARoleUnsupportedException extends Exception {
        
        private static final long serialVersionUID = -6854500893864114158L;
        
    }

    public RoleChanger(Controller controller) {
        this.controller = controller;
        this.pendingRequestMap = new ConcurrentHashMap<IOFSwitch,
                LinkedList<PendingRoleRequestEntry>>();
        this.pendingTasks = new DelayQueue<RoleChangeTask>();
        this.workerThread = new Thread(new RoleRequestWorker());
        this.timeout = DEFAULT_TIMEOUT;
        this.workerThread.start();
    }
    
    
    public synchronized void submitRequest(Collection<IOFSwitch> switches, Role role) {
        long deadline = System.nanoTime();
        // Grrr. stupid DelayQueue. Make sre we have at least 10ms between 
        // role request messages.
        if (deadline - lastSubmitTime < 10 * 1000*1000) 
            deadline = lastSubmitTime + 10 * 1000*1000;
        // make a copy of the list 
        ArrayList<IOFSwitch> switches_copy = new ArrayList<IOFSwitch>(switches);
        RoleChangeTask req = new RoleChangeTask(switches_copy, role, deadline);
        pendingTasks.put(req);
        lastSubmitTime = deadline;
    }
    
    /**
     * Send a role request message to switches. The sw implementation throws
     * HARoleUnsupportedException if HA is not supported. Otherwise, it
     * returns the transaction id of the request message.
     * @param switches the collection of switches to send the request too
     * @param role the role to request
     */
    @LogMessageDoc(level="WARN",
            message="Failed to send role request message " + 
                    "to switch {switch}: {message}. Disconnecting",
            explanation="An I/O error occurred while attempting to change " +
            		"the switch HA role.",
            recommendation=LogMessageDoc.CHECK_SWITCH)                              
    protected void sendRoleRequest(Collection<IOFSwitch> switches,
                                   Role role, long cookie) {
        Iterator<IOFSwitch> iter = switches.iterator();
        while(iter.hasNext()) {
            IOFSwitch sw = iter.next();
            try {
                int xid = sendHARoleRequest(sw, role, cookie);
                PendingRoleRequestEntry entry =
                        new PendingRoleRequestEntry(xid, role, cookie);
                LinkedList<PendingRoleRequestEntry> pendingList
                    = pendingRequestMap.get(sw);
                if (pendingList == null) {
                    pendingList = new LinkedList<PendingRoleRequestEntry>();
                    pendingRequestMap.put(sw, pendingList);
                }
                // Need to synchronize against removal from list
                synchronized(pendingList) {
                    pendingList.add(entry);
                }
            } catch (IOException e) {
                log.warn("Failed to send role request message " + 
                         "to switch {}: {}. Disconnecting",
                         sw, e);
                sw.disconnectOutputStream();
                iter.remove();
            } catch (HARoleUnsupportedException e) {
                // Switch doesn't support HA role, remove if role is slave
                if (role == Role.SLAVE) {
                    log.debug("Disconnecting switch {} that doesn't support " +
                    "role request messages from a controller that went to SLAVE mode");
                    // Closing the channel should result in a call to
                    // channelDisconnect which updates all state 
                    sw.disconnectOutputStream();
                    iter.remove();
                }
            }
        }
    }
    
    /**
     * Verify that switches have received a role reply message we sent earlier
     * @param switches the collection of switches to send the request too
     * @param cookie the cookie of the request
     */
    @LogMessageDoc(level="WARN",
            message="Timeout while waiting for role reply from switch {switch}."
                    + " Disconnecting",
            explanation="Timed out waiting for the switch to respond to " +
            		"a request to change the HA role.",
            recommendation=LogMessageDoc.CHECK_SWITCH)                              
    protected void verifyRoleReplyReceived(Collection<IOFSwitch> switches,
                                   long cookie) {
        for (IOFSwitch sw: switches) {
            PendingRoleRequestEntry entry =
                    checkFirstPendingRoleRequestCookie(sw, cookie);
            if (entry != null){
                log.warn("Timeout while waiting for role reply from switch {}"
                         + " with datapath {}", sw,
                         sw.getAttribute(IOFSwitch.SWITCH_DESCRIPTION_DATA));
                sw.setHARole(entry.role, false);
                if (entry.role == Role.SLAVE) {
                    sw.disconnectOutputStream();
                } else {
                    controller.addSwitch(sw, true);
                }
            }
        }
    }
    
    /** 
     * Deliver a RoleReply message for a switch. Checks if the reply 
     * message matches the expected reply (head of the pending request queue). 
     * We require in-order delivery of replies. If there's any deviation from
     * our expectations we disconnect the switch. 
     * 
     * We must not check the received role against the controller's current
     * role because there's no synchronization but that's fine @see RoleChanger
     * 
     * Will be called by the OFChannelHandler's receive loop
     * 
     * @param xid Xid of the reply message
     * @param role The Role in the the reply message
     */
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: received unexpected role reply for " +
                        "Role {role}" + 
                        " Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=HA_CHECK_SWITCH),                           
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: expected role reply with " +
                        "Xid {xid}, got {xid}. Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=HA_CHECK_SWITCH),                           
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: expected role reply with " +
                        "Role {role}, got {role}. Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=HA_CHECK_SWITCH)                           
    })
    
    public void deliverRoleReply(IOFSwitch sw, int xid, Role role) {
        LinkedList<PendingRoleRequestEntry> pendingRoleRequests =
                pendingRequestMap.get(sw);
        if (pendingRoleRequests == null) {
            log.warn("Switch {}: received unexpected role reply for Role {}" + 
                    ", ignored", sw, role );
            return;
        }
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.poll();
            if (head == null) {
                // Maybe don't disconnect if the role reply we received is 
                // for the same role we are already in. 
                log.error("Switch {}: received unexpected role reply for Role {}" + 
                          " Disconnecting switch", sw, role );
                sw.disconnectOutputStream();
            }
            else if (head.xid != xid) {
                // check xid before role!!
                log.error("Switch {}: expected role reply with " +
                       "Xid {}, got {}. Disconnecting switch",
                       new Object[] { this, head.xid, xid } );
                sw.disconnectOutputStream();
            }
            else if (head.role != role) {
                log.error("Switch {}: expected role reply with " +
                       "Role {}, got {}. Disconnecting switch",
                       new Object[] { this, head.role, role } );
                sw.disconnectOutputStream();
            }
            else {
                log.debug("Received role reply message from {}, setting role to {}",
                          this, role);
                sw.setHARole(role, true);
            }
        }
    }
    
    /** 
     * Checks whether the given xid matches the xid of the first pending
     * role request. 
     * @param xid
     * @return 
     */
    public boolean checkFirstPendingRoleRequestXid (IOFSwitch sw, int xid) {
        LinkedList<PendingRoleRequestEntry> pendingRoleRequests =
                pendingRequestMap.get(sw);
        if (pendingRoleRequests == null) {
            return false;
        }
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.peek();
            if (head == null)
                return false;
            else 
                return head.xid == xid;
        }
    }
    
    /**
     * Checks whether the given request cookie matches the cookie of the first 
     * pending request. If so, return the entry
     * @param cookie
     * @return
     */
    protected PendingRoleRequestEntry checkFirstPendingRoleRequestCookie(
            IOFSwitch sw, long cookie)
    {
        LinkedList<PendingRoleRequestEntry> pendingRoleRequests =
                pendingRequestMap.get(sw);
        if (pendingRoleRequests == null) {
            return null;
        }
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.peek();
            if (head == null)
                return null;
            if (head.cookie == cookie) {
                return pendingRoleRequests.poll();
            }
        }
        return null;
    }
    
    /**
     * Called if we receive a vendor error message indicating that roles
     * are not supported by the switch. If the xid matches the first pending
     * one, we'll mark the switch as not supporting roles and remove the head.
     * Otherwise we ignore it.
     * @param xid
     */
    public void deliverRoleRequestNotSupported(IOFSwitch sw, int xid) {
        LinkedList<PendingRoleRequestEntry> pendingRoleRequests =
                pendingRequestMap.get(sw);
        if (pendingRoleRequests == null) {
            log.warn("Switch {}: received unexpected error for xid {}" + 
                    ", ignored", sw, xid);
            return;
        }
        synchronized(pendingRoleRequests) {
            PendingRoleRequestEntry head = pendingRoleRequests.poll();
            if (head != null && head.xid == xid) {
                sw.setHARole(head.role, false);
            } else {
                sw.disconnectOutputStream();
            }
        }
    }
    
    /**
     * Send NX role request message to the switch requesting the specified role.
     * 
     * @param sw switch to send the role request message to
     * @param role role to request
     * @param cookie an opaque value that will be stored in the pending queue so
     *        RoleChanger can check for timeouts.
     * @return transaction id of the role request message that was sent
     */
    protected int sendHARoleRequest(IOFSwitch sw, Role role, long cookie)
            throws IOException, HARoleUnsupportedException {
        // There are three cases to consider:
        //
        // 1) If the controller role at the point the switch connected was
        //    null/disabled, then we never sent the role request probe to the
        //    switch and therefore never set the SWITCH_SUPPORTS_NX_ROLE
        //    attribute for the switch, so supportsNxRole is null. In that
        //    case since we're now enabling role support for the controller
        //    we should send out the role request probe/update to the switch.
        //
        // 2) If supportsNxRole == Boolean.TRUE then that means we've already
        //    sent the role request probe to the switch and it replied with
        //    a role reply message, so we know it supports role request
        //    messages. Now we're changing the role and we want to send
        //    it another role request message to inform it of the new role
        //    for the controller.
        //
        // 3) If supportsNxRole == Boolean.FALSE, then that means we sent the
        //    role request probe to the switch but it responded with an error
        //    indicating that it didn't understand the role request message.
        //    In that case, we simply throw an unsupported exception.
        Boolean supportsNxRole = (Boolean)
                sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE);
        if ((supportsNxRole != null) && !supportsNxRole) {
            throw new HARoleUnsupportedException();
        }

        int xid = sw.getNextTransactionId();
        // Convert the role enum to the appropriate integer constant used
        // in the NX role request message
        int nxRole = 0;
        switch (role) {
        case EQUAL:
            nxRole = OFRoleVendorData.NX_ROLE_OTHER;
            break;
        case MASTER:
            nxRole = OFRoleVendorData.NX_ROLE_MASTER;
            break;
        case SLAVE:
            nxRole = OFRoleVendorData.NX_ROLE_SLAVE;
            break;
        default:
            log.error("Invalid Role specified for switch {}."
                    + " Disconnecting.", sw);
            throw new HARoleUnsupportedException();
        }

        // Construct the role request message
        OFVendor roleRequest = (OFVendor)controller.
                getOFMessageFactory().getMessage(OFType.VENDOR);
        roleRequest.setXid(xid);
        roleRequest.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
        OFRoleRequestVendorData roleRequestData = new OFRoleRequestVendorData();
        roleRequestData.setRole(nxRole);
        roleRequest.setVendorData(roleRequestData);
        roleRequest.setLengthU(OFVendor.MINIMUM_LENGTH + 
                roleRequestData.getLength());

        // Send it to the switch
        List<OFMessage> msgList = new ArrayList<OFMessage>(1);
        msgList.add(roleRequest);
        sw.write(msgList, new FloodlightContext());

        return xid;
    }
    

}
