/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

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

import org.openflow.protocol.OFError;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
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
 *   the same order as they are submitted. If a request times out we assume
 *   the switch doesn't support HA role (the same as receiving an error reply). 
 * - Since we decouple submission of role change requests and actually sending
 *   them we cannot check a received role reply against the controller's current 
 *   role because the controller's current role could have changed again. 
 * - Receiving Role Reply messages is received by OFChannelHandler and
 *   delivered here. We call switch's setHARole() to mark the switch role and
 *   indicate that a reply was received. Next, we call addSwitch(),
 *   removeSwitch() to update the list of active switches if appropriate.
 * - If we receive an Error indicating that roles are not supported by the 
 *   switch, we set the SWITCH_SUPPORTS_NX_ROLE to false. We call switch's
 *   setHARole(), indicating no reply was received. We keep the switch
 *   connection alive while in MASTER and EQUAL role. 
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
 *   and we send the first role request message. If role is disabled, we assume
 *   the role is MASTER.
 * - When we receive the first reply we proceed as above. In addition, if
 *   the role request is for MASTER we wipe the flow table. 
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
                LinkedList<PendingRoleRequestEntry> pendingList
                    = pendingRequestMap.get(sw);
                if (pendingList == null) {
                    pendingList = new LinkedList<PendingRoleRequestEntry>();
                    LinkedList<PendingRoleRequestEntry> r = 
                            pendingRequestMap.putIfAbsent(sw, pendingList);
                    if (r != null) pendingList = r;
                }
                int xid = sendHARoleRequest(sw, role, cookie);
                PendingRoleRequestEntry entry =
                        new PendingRoleRequestEntry(xid, role, cookie);
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
                } else {
                    sw.setHARole(role, false);
                    controller.addSwitch(sw, true);
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
                setSwitchHARole(sw, entry.role, false);
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
     * role because there's no synchronization but that's fine.
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
                recommendation=LogMessageDoc.HA_CHECK_SWITCH),                           
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: expected role reply with " +
                        "Xid {xid}, got {xid}. Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=LogMessageDoc.HA_CHECK_SWITCH),                           
        @LogMessageDoc(level="ERROR",
                message="Switch {switch}: expected role reply with " +
                        "Role {role}, got {role}. Disconnecting switch",
                explanation="The switch sent an unexpected HA role reply",
                recommendation=LogMessageDoc.HA_CHECK_SWITCH)                           
    })
    
    protected void deliverRoleReply(IOFSwitch sw, int xid, Role role) {
        LinkedList<PendingRoleRequestEntry> pendingRoleRequests =
                pendingRequestMap.get(sw);
        if (pendingRoleRequests == null) {
            log.debug("Switch {}: received unexpected role reply for Role {}" + 
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
                setSwitchHARole(sw, role, true);
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
        LinkedList<PendingRoleRequestEntry> pendingRoleRequests;
        if (sw == null) {
            return false;
        }
        pendingRoleRequests = pendingRequestMap.get(sw);
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
    private void deliverRoleRequestNotSupported(IOFSwitch sw, int xid) {
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
                setSwitchHARole(sw, head.role, false);
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
    
    /* Handle a role reply message we received from the switch. Since
     * netty serializes message dispatch we don't need to synchronize 
     * against other receive operations from the same switch, so no need
     * to synchronize addSwitch(), removeSwitch() operations from the same
     * connection. 
     * FIXME: However, when a switch with the same DPID connects we do
     * need some synchronization. However, handling switches with same
     * DPID needs to be revisited anyways (get rid of r/w-lock and synchronous
     * removedSwitch notification):1
     * 
     */
    @LogMessageDoc(level="ERROR",
            message="Invalid role value in role reply message",
            explanation="Was unable to set the HA role (master or slave) " +
                    "for the controller.",
            recommendation=LogMessageDoc.CHECK_CONTROLLER)
    protected void handleRoleReplyMessage(IOFSwitch sw, OFVendor vendorMessage,
                                OFRoleReplyVendorData roleReplyVendorData) {
        // Map from the role code in the message to our role enum
        int nxRole = roleReplyVendorData.getRole();
        Role role = null;
        switch (nxRole) {
            case OFRoleVendorData.NX_ROLE_OTHER:
                role = Role.EQUAL;
                break;
            case OFRoleVendorData.NX_ROLE_MASTER:
                role = Role.MASTER;
                break;
            case OFRoleVendorData.NX_ROLE_SLAVE:
                role = Role.SLAVE;
                break;
            default:
                log.error("Invalid role value in role reply message");
                sw.disconnectOutputStream();
                return;
        }
        
        log.debug("Handling role reply for role {} from {}. " +
                  "Controller's role is {} ", 
                  new Object[] { role, sw, controller.role} 
                  );
        
        deliverRoleReply(sw, vendorMessage.getXid(), role);
    }

    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
            message="Received ERROR from sw {switch} that indicates roles " +
                    "are not supported but we have received a valid role " +
                    "reply earlier",
            explanation="Switch is responding to HA role request in an " +
                        "inconsistent manner.",
            recommendation=LogMessageDoc.CHECK_SWITCH),
        @LogMessageDoc(level="ERROR",
            message="Unexpected error {error} from switch {switch} " +
                    "disconnecting",
            explanation="Swith sent an error reply to request, but the error " +
                        "type is not OPET_BAD_REQUEST as required by the protocol",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    })
    protected void deliverRoleRequestError(IOFSwitch sw, OFError error) {
        // We expect to receive a bad request error when
        // we're connected to a switch that doesn't support
        // the Nicira vendor extensions (i.e. not OVS or
        // derived from OVS).  By protocol, it should also be
        // BAD_VENDOR, but too many switch implementations
        // get it wrong and we can already check the xid()
        // so we can ignore the type with confidence that this
        // is not a spurious error
        boolean isBadRequestError =
                (error.getErrorType() == OFError.OFErrorType.
                OFPET_BAD_REQUEST.getValue());
        if (isBadRequestError) {
            if (sw.getHARole() != null) {
                log.warn("Received ERROR from sw {} that "
                        +"indicates roles are not supported "
                        +"but we have received a valid "
                        +"role reply earlier", sw);
            }
            // First, clean up pending requests
            deliverRoleRequestNotSupported(sw, error.getXid());
        } else {
            // TODO: Is this the right thing to do if we receive
            // some other error besides a bad request error?
            // Presumably that means the switch did actually
            // understand the role request message, but there
            // was some other error from processing the message.
            // OF 1.2 specifies a OFPET_ROLE_REQUEST_FAILED
            // error code, but it doesn't look like the Nicira
            // role request has that. Should check OVS source
            // code to see if it's possible for any other errors
            // to be returned.
            // If we received an error the switch is not
            // in the correct role, so we need to disconnect it.
            // We could also resend the request but then we need to
            // check if there are other pending request in which
            // case we shouldn't resend. If we do resend we need
            // to make sure that the switch eventually accepts one
            // of our requests or disconnect the switch. This feels
            // cumbersome.
            log.error("Unexpected error {} from switch {}, disconnecting",
                    error, sw);
            sw.disconnectOutputStream();
        }
    }

    /**
     * Set switch's HA role and adjust switch's list membership
     * based on the new role and switch's HA capability. This is
     * a synchronized method to keep role handling consistent.
     * @param sw
     * @param role
     * @param replyReceived
     */
    private synchronized void setSwitchHARole(IOFSwitch sw, Role role,
            boolean replyReceived) {
        // Record previous role and set the current role
        // We tell switch whether a correct reply was received.
        // The switch may deduce if HA role request is supported
        // (if it doesn't already know).
        Role oldRole = sw.getHARole();
        sw.setHARole(role,  replyReceived);
        
        // Adjust the active switch list based on the role
        if (role != Role.SLAVE) {
            // SLAVE->MASTER, need to add switch to active list.
            // If this is the initial connection, clear flodmods.
            boolean clearFlowMods = (oldRole == null) ||
                    controller.getAlwaysClearFlowsOnSwAdd();
            controller.addSwitch(sw, clearFlowMods);
        } else {
            // Initial SLAVE setting. Nothing to do if role reply received.
            // Else disconnect the switch.
            if (oldRole == null && !replyReceived) {
                sw.disconnectOutputStream();
            } else {
                // MASTER->SLAVE transition. If switch supports HA roles,
                // remove it from active list, but still leave it connected.
                // Otherwise, disconnect it. Cleanup happens after channel
                // handler receives the disconnect notification.
                if (replyReceived) {
                    controller.removeSwitch(sw);
                } else {
                    sw.disconnectOutputStream();
                }
            }
        }
    }

    /**
     * Cleanup pending requests associated witch switch. Called when
     * switch disconnects.
     * @param sw
     */
    public void removePendingRequests(IOFSwitch sw) {
        pendingRequestMap.remove(sw);
    }
}
