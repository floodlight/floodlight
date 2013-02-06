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

package net.floodlightcontroller.staticflowentry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.web.StaticFlowEntryWebRoutable;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LogMessageCategory("Static Flow Pusher")
/**
 * This module is responsible for maintaining a set of static flows on
 * switches. This is just a big 'ol dumb list of flows and something external
 * is responsible for ensuring they make sense for the network.
 */
public class StaticFlowEntryPusher 
    implements IOFSwitchListener, IFloodlightModule, IStaticFlowEntryPusherService,
        IStorageSourceListener, IOFMessageListener, IHAListener {
    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryPusher.class);
    public static final String StaticFlowName = "staticflowentry";
    
    public static final int STATIC_FLOW_APP_ID = 10;

    public static final String TABLE_NAME = "controller_staticflowtableentry";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_SWITCH = "switch_id";
    public static final String COLUMN_ACTIVE = "active";
    public static final String COLUMN_IDLE_TIMEOUT = "idle_timeout";
    public static final String COLUMN_HARD_TIMEOUT = "hard_timeout";
    public static final String COLUMN_PRIORITY = "priority";
    public static final String COLUMN_COOKIE = "cookie";
    public static final String COLUMN_WILDCARD = "wildcards";
    public static final String COLUMN_IN_PORT = "in_port";
    public static final String COLUMN_DL_SRC = "dl_src";
    public static final String COLUMN_DL_DST = "dl_dst";
    public static final String COLUMN_DL_VLAN = "dl_vlan";
    public static final String COLUMN_DL_VLAN_PCP = "dl_vlan_pcp";
    public static final String COLUMN_DL_TYPE = "dl_type";
    public static final String COLUMN_NW_TOS = "nw_tos";
    public static final String COLUMN_NW_PROTO = "nw_proto";
    public static final String COLUMN_NW_SRC = "nw_src"; // includes CIDR-style
                                                         // netmask, e.g.
                                                         // "128.8.128.0/24"
    public static final String COLUMN_NW_DST = "nw_dst";
    public static final String COLUMN_TP_DST = "tp_dst";
    public static final String COLUMN_TP_SRC = "tp_src";
    public static final String COLUMN_ACTIONS = "actions";
    public static String ColumnNames[] = { COLUMN_NAME, COLUMN_SWITCH,
            COLUMN_ACTIVE, COLUMN_IDLE_TIMEOUT, COLUMN_HARD_TIMEOUT,
            COLUMN_PRIORITY, COLUMN_COOKIE, COLUMN_WILDCARD, COLUMN_IN_PORT,
            COLUMN_DL_SRC, COLUMN_DL_DST, COLUMN_DL_VLAN, COLUMN_DL_VLAN_PCP,
            COLUMN_DL_TYPE, COLUMN_NW_TOS, COLUMN_NW_PROTO, COLUMN_NW_SRC,
            COLUMN_NW_DST, COLUMN_TP_DST, COLUMN_TP_SRC, COLUMN_ACTIONS };


    protected IFloodlightProviderService floodlightProvider;
    protected IStorageSourceService storageSource;
    protected IRestApiService restApi;

    // Map<DPID, Map<Name, FlowMod>>; FlowMod can be null to indicate non-active
    protected Map<String, Map<String, OFFlowMod>> entriesFromStorage;
    // Entry Name -> DPID of Switch it's on
    protected Map<String, String> entry2dpid;

    // Class to sort FlowMod's by priority, from lowest to highest
    class FlowModSorter implements Comparator<String> {
        private String dpid;
        public FlowModSorter(String dpid) {
            this.dpid = dpid;
        }
        @Override
        public int compare(String o1, String o2) {
            OFFlowMod f1 = entriesFromStorage.get(dpid).get(o1);
            OFFlowMod f2 = entriesFromStorage.get(dpid).get(o2);
            if (f1 == null || f2 == null) // sort active=false flows by key
                return o1.compareTo(o2);
            return U16.f(f1.getPriority()) - U16.f(f2.getPriority());
        }
    };

    /**
     * used for debugging and unittests
     * @return the number of static flow entries as cached from storage
     */
    public int countEntries() {
        int size = 0;
        if (entriesFromStorage == null)
            return 0;
        for (String ofswitch : entriesFromStorage.keySet())
            size += entriesFromStorage.get(ofswitch).size();
        return size;
    }

    public IFloodlightProviderService getFloodlightProvider() {
        return floodlightProvider;
    }

    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    public void setStorageSource(IStorageSourceService storageSource) {
        this.storageSource = storageSource;
    }

    /**
     * Reads from our entriesFromStorage for the specified switch and
     * sends the FlowMods down to the controller in <b>sorted</b> order.
     *
     * Sorted is important to maintain correctness of the switch:
     * if a packet would match both a lower and a higher priority
     * rule, then we want it to match the higher priority or nothing,
     * but never just the lower priority one.  Inserting from high to
     * low priority fixes this.
     *
     * TODO consider adding a "block all" flow mod and then removing it
     * while starting up.
     *
     * @param sw The switch to send entries to
     */
    protected void sendEntriesToSwitch(IOFSwitch sw) {
        String dpid = sw.getStringId();

        if ((entriesFromStorage != null) && (entriesFromStorage.containsKey(dpid))) {
            Map<String, OFFlowMod> entries = entriesFromStorage.get(dpid);
            List<String> sortedList = new ArrayList<String>(entries.keySet());
            // weird that Collections.sort() returns void
            Collections.sort( sortedList, new FlowModSorter(dpid));
            for (String entryName : sortedList) {
                OFFlowMod flowMod = entries.get(entryName);
                if (flowMod != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Pushing static entry {} for {}", dpid, entryName);
                    }
                    writeFlowModToSwitch(sw, flowMod);
                }
            }
        }
    }
    
    /**
     * Used only for bundle-local indexing
     * 
     * @param map
     * @return
     */

    protected Map<String, String> computeEntry2DpidMap(
                Map<String, Map<String, OFFlowMod>> map) {
        Map<String, String> ret = new ConcurrentHashMap<String, String>();
        for(String dpid : map.keySet()) {
            for( String entry: map.get(dpid).keySet())
                ret.put(entry, dpid);
        }
        return ret;
    }
    
    /**
     * Read entries from storageSource, and store them in a hash
     * 
     * @return
     */
    @LogMessageDoc(level="ERROR",
            message="failed to access storage: {reason}",
            explanation="Could not retrieve static flows from the system " +
            		"database",
            recommendation=LogMessageDoc.CHECK_CONTROLLER)
    private Map<String, Map<String, OFFlowMod>> readEntriesFromStorage() {
        Map<String, Map<String, OFFlowMod>> entries = new ConcurrentHashMap<String, Map<String, OFFlowMod>>();
        try {
            Map<String, Object> row;
            // null1=no predicate, null2=no ordering
            IResultSet resultSet = storageSource.executeQuery(TABLE_NAME,
                    ColumnNames, null, null);
            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                row = it.next().getRow();
                parseRow(row, entries);
            }
        } catch (StorageException e) {
            log.error("failed to access storage: {}", e.getMessage());
            // if the table doesn't exist, then wait to populate later via
            // setStorageSource()
        }
        return entries;
    }

    /**
     * Take a single row, turn it into a flowMod, and add it to the
     * entries{$dpid}.{$entryName}=FlowMod 
     * 
     * IF an entry is in active, mark it with FlowMod = null
     * 
     * @param row
     * @param entries
     */
    void parseRow(Map<String, Object> row, Map<String, Map<String, OFFlowMod>> entries) {
        String switchName = null;
        String entryName = null;

        StringBuffer matchString = new StringBuffer();

        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory()
                .getMessage(OFType.FLOW_MOD);

        if (!row.containsKey(COLUMN_SWITCH) || !row.containsKey(COLUMN_NAME)) {
            log.debug(
                    "skipping entry with missing required 'switch' or 'name' entry: {}",
                    row);
            return;
        }
        // most error checking done with ClassCastException
        try {
            // first, snag the required entries, for debugging info
            switchName = (String) row.get(COLUMN_SWITCH);
            entryName = (String) row.get(COLUMN_NAME);
            if (!entries.containsKey(switchName))
                entries.put(switchName, new HashMap<String, OFFlowMod>());
            StaticFlowEntries.initDefaultFlowMod(flowMod, entryName);
            
            for (String key : row.keySet()) {
                if (row.get(key) == null)
                    continue;
                if (key.equals(COLUMN_SWITCH) || key.equals(COLUMN_NAME)
                        || key.equals("id"))
                    continue; // already handled
                // explicitly ignore timeouts and wildcards
                if (key.equals(COLUMN_HARD_TIMEOUT) || key.equals(COLUMN_IDLE_TIMEOUT) ||
                        key.equals(COLUMN_WILDCARD))
                    continue;
                if (key.equals(COLUMN_ACTIVE)) {
                    if  (!Boolean.valueOf((String) row.get(COLUMN_ACTIVE))) {
                        log.debug("skipping inactive entry {} for switch {}",
                                entryName, switchName);
                        entries.get(switchName).put(entryName, null);  // mark this an inactive
                        return;
                    }
                } else if (key.equals(COLUMN_ACTIONS)){
                    StaticFlowEntries.parseActionString(flowMod, (String) row.get(COLUMN_ACTIONS), log);
                } else if (key.equals(COLUMN_COOKIE)) {
                    flowMod.setCookie(
                            StaticFlowEntries.computeEntryCookie(flowMod, 
                                    Integer.valueOf((String) row.get(COLUMN_COOKIE)), 
                                    entryName));
                } else if (key.equals(COLUMN_PRIORITY)) {
                    flowMod.setPriority(U16.t(Integer.valueOf((String) row.get(COLUMN_PRIORITY))));
                } else { // the rest of the keys are for OFMatch().fromString()
                    if (matchString.length() > 0)
                        matchString.append(",");
                    matchString.append(key + "=" + row.get(key).toString());
                }
            }
        } catch (ClassCastException e) {
            if (entryName != null && switchName != null) {
                log.warn(
                        "Skipping entry {} on switch {} with bad data : "
                                + e.getMessage(), entryName, switchName);
            } else {
                log.warn("Skipping entry with bad data: {} :: {} ",
                        e.getMessage(), e.getStackTrace());
            }
        }

        OFMatch ofMatch = new OFMatch();
        String match = matchString.toString();
        try {
            ofMatch.fromString(match);
        } catch (IllegalArgumentException e) {
            log.debug(
                    "ignoring flow entry {} on switch {} with illegal OFMatch() key: "
                            + match, entryName, switchName);
            return;
        }
        flowMod.setMatch(ofMatch);

        entries.get(switchName).put(entryName, flowMod);
    }
    
    @Override
    public void addedSwitch(IOFSwitch sw) {
        log.debug("Switch {} connected; processing its static entries", HexString.toHexString(sw.getId()));
        sendEntriesToSwitch(sw);
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        // do NOT delete from our internal state; we're tracking the rules,
        // not the switches
    }
    
    @Override
    public void switchPortChanged(Long switchId) {
        // no-op
    }

    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        // This handles both rowInsert() and rowUpdate()
        log.debug("Modifying Table {}", tableName);
        HashMap<String, Map<String, OFFlowMod>> entriesToAdd = 
            new HashMap<String, Map<String, OFFlowMod>>();
        // build up list of what was added 
        for (Object key: rowKeys) {
            IResultSet resultSet = storageSource.getRow(tableName, key);
            Iterator<IResultSet> it = resultSet.iterator();
            while (it.hasNext()) {
                Map<String, Object> row = it.next().getRow();
                parseRow(row, entriesToAdd);
            }
        }
        // batch updates by switch and blast them out
        for (String dpid : entriesToAdd.keySet()) {
            if (!entriesFromStorage.containsKey(dpid))
                entriesFromStorage.put(dpid, new HashMap<String, OFFlowMod>());
            
            List<OFMessage> outQueue = new ArrayList<OFMessage>();
            for(String entry : entriesToAdd.get(dpid).keySet()) {
                OFFlowMod newFlowMod = entriesToAdd.get(dpid).get(entry);
                //OFFlowMod oldFlowMod = entriesFromStorage.get(dpid).get(entry);
                OFFlowMod oldFlowMod = null;
                String dpidOldFlowMod = entry2dpid.get(entry);
                if (dpidOldFlowMod != null) {
                    oldFlowMod = entriesFromStorage.get(dpidOldFlowMod).remove(entry);
                }
                if (oldFlowMod != null && newFlowMod != null) {  
                    // set the new flow mod to modify a pre-existing rule if these fields match
                    if(oldFlowMod.getMatch().equals(newFlowMod.getMatch())
                            && oldFlowMod.getCookie() == newFlowMod.getCookie()
                            && oldFlowMod.getPriority() == newFlowMod.getPriority()){
                        newFlowMod.setCommand(OFFlowMod.OFPFC_MODIFY_STRICT);
                    // if they don't match delete the old flow 
                    } else{
                        oldFlowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
                        if (dpidOldFlowMod.equals(dpid)) {
                            outQueue.add(oldFlowMod);
                        } else {
                            writeOFMessageToSwitch(HexString.toLong(dpidOldFlowMod), oldFlowMod);
                        }
                    }
                }
                // write the new flow 
                if (newFlowMod != null) {
                    entriesFromStorage.get(dpid).put(entry, newFlowMod);
                    outQueue.add(newFlowMod);
                    entry2dpid.put(entry, dpid);
                } else {
                    entriesFromStorage.get(dpid).remove(entry);
                    entry2dpid.remove(entry);
                }
            }
            writeOFMessagesToSwitch(HexString.toLong(dpid), outQueue);
        }
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting from table {}", tableName);
        }
        
        for(Object obj : rowKeys) {
            if (!(obj instanceof String)) {
                log.debug("Tried to delete non-string key {}; ignoring", obj);
                continue;
            }
            deleteStaticFlowEntry((String) obj);
        }
    }
    
    @LogMessageDoc(level="ERROR",
            message="inconsistent internal state: no switch has rule {rule}",
            explanation="Inconsistent internat state discovered while " +
                    "deleting a static flow rule",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private void deleteStaticFlowEntry(String entryName) {
        String dpid = entry2dpid.remove(entryName);
        
        if (dpid == null) {
            // assume state has been cleared by deleteFlowsForSwitch() or
            // deleteAllFlows()
            return;
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Sending delete flow mod for flow {} for switch {}", entryName, dpid);
        }
        
        // send flow_mod delete
        OFFlowMod flowMod = entriesFromStorage.get(dpid).get(entryName);
        flowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);

        if (entriesFromStorage.containsKey(dpid) && 
                entriesFromStorage.get(dpid).containsKey(entryName)) {
            entriesFromStorage.get(dpid).remove(entryName);
        } else { 
            log.debug("Tried to delete non-existent entry {} for switch {}", 
                    entryName, dpid);
            return;
        }
        
        writeFlowModToSwitch(HexString.toLong(dpid), flowMod);
        return;
    }
    
    /**
     * Writes a list of OFMessages to a switch
     * @param dpid The datapath ID of the switch to write to
     * @param messages The list of OFMessages to write.
     */
    @LogMessageDoc(level="ERROR",
            message="Tried to write to switch {switch} but got {error}",
            explanation="An I/O error occured while trying to write a " +
                    "static flow to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    private void writeOFMessagesToSwitch(long dpid, List<OFMessage> messages) {
        IOFSwitch ofswitch = floodlightProvider.getSwitches().get(dpid);
        if (ofswitch != null) {  // is the switch connected
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Sending {} new entries to {}", messages.size(), dpid);
                }
                ofswitch.write(messages, null);
                ofswitch.flush();
            } catch (IOException e) {
                log.error("Tried to write to switch {} but got {}", dpid, e.getMessage());
            }
        }
    }
    
    /**
     * Writes a single OFMessage to a switch
     * @param dpid The datapath ID of the switch to write to
     * @param message The OFMessage to write.
     */
    @LogMessageDoc(level="ERROR",
            message="Tried to write to switch {switch} but got {error}",
            explanation="An I/O error occured while trying to write a " +
                    "static flow to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    private void writeOFMessageToSwitch(long dpid, OFMessage message) {
        IOFSwitch ofswitch = floodlightProvider.getSwitches().get(dpid);
        if (ofswitch != null) {  // is the switch connected
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Sending 1 new entries to {}", HexString.toHexString(dpid));
                }
                ofswitch.write(message, null);
                ofswitch.flush();
            } catch (IOException e) {
                log.error("Tried to write to switch {} but got {}", dpid, e.getMessage());
            }
        }
    }
    
    /**
     * Writes an OFFlowMod to a switch. It checks to make sure the switch
     * exists before it sends
     * @param dpid The data  to write the flow mod to
     * @param flowMod The OFFlowMod to write
     */
    private void writeFlowModToSwitch(long dpid, OFFlowMod flowMod) {
        Map<Long,IOFSwitch> switches = floodlightProvider.getSwitches();
        IOFSwitch ofSwitch = switches.get(dpid);
        if (ofSwitch == null) {
            if (log.isDebugEnabled()) {
                log.debug("Not deleting key {} :: switch {} not connected", 
                          dpid);
            }
            return;
        }
        writeFlowModToSwitch(ofSwitch, flowMod);
    }
    
    /**
     * Writes an OFFlowMod to a switch
     * @param sw The IOFSwitch to write to
     * @param flowMod The OFFlowMod to write
     */
    @LogMessageDoc(level="ERROR",
            message="Tried to write OFFlowMod to {switch} but got {error}",
            explanation="An I/O error occured while trying to write a " +
                    "static flow to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    private void writeFlowModToSwitch(IOFSwitch sw, OFFlowMod flowMod) {
        try {
            sw.write(flowMod, null);
            sw.flush();
        } catch (IOException e) {
            log.error("Tried to write OFFlowMod to {} but failed: {}", 
                    HexString.toHexString(sw.getId()), e.getMessage());
        }
    }

    @Override
    public String getName() {
        return StaticFlowName;
    }
    
    /**
     * Handles a flow removed message from a switch. If the flow was removed
     * and we did not explicitly delete it we re-install it. If we explicitly
     * removed the flow we stop the processing of the flow removed message.
     * @param sw The switch that sent the flow removed message.
     * @param msg The flow removed message.
     * @param cntx The associated context.
     * @return Whether to continue processing this message.
     */
    public Command handleFlowRemoved(IOFSwitch sw, OFFlowRemoved msg, FloodlightContext cntx) {
        long cookie = msg.getCookie();
        /**
         * This is just to sanity check our assumption that static flows 
         * never expire.
         */
        if (AppCookie.extractApp(cookie) == STATIC_FLOW_APP_ID) {
            if (msg.getReason() != OFFlowRemoved.OFFlowRemovedReason.OFPRR_DELETE)
                log.error("Got a FlowRemove message for a infinite " +
                          "timeout flow: {} from switch {}", msg, sw);
            // Stop the processing chain since we sent the delete.
            return Command.STOP;
        }
        
        return Command.CONTINUE;
    }
    
    @Override
    @LogMessageDoc(level="ERROR",
        message="Got a FlowRemove message for a infinite " +
                "timeout flow: {flow} from switch {switch}",
        explanation="Flows with infinite timeouts should not expire. " +
        		"The switch has expired the flow anyway.",
        recommendation=LogMessageDoc.REPORT_SWITCH_BUG)
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
        case FLOW_REMOVED:
            return handleFlowRemoved(sw, (OFFlowRemoved) msg, cntx);
        default:
            return Command.CONTINUE;
        }
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;  // no dependency for non-packet in
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;  // no dependency for non-packet in
    }

    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStaticFlowEntryPusherService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(IStaticFlowEntryPusherService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStorageSourceService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider =
            context.getServiceImpl(IFloodlightProviderService.class);
        storageSource =
            context.getServiceImpl(IStorageSourceService.class);
        restApi =
            context.getServiceImpl(IRestApiService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {        
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addHAListener(this);
        
        // assumes no switches connected at startup()
        storageSource.createTable(TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_NAME);
        storageSource.addListener(TABLE_NAME, this);
        entriesFromStorage = readEntriesFromStorage(); 
        entry2dpid = computeEntry2DpidMap(entriesFromStorage);
        restApi.addRestletRoutable(new StaticFlowEntryWebRoutable());
    }

    // IStaticFlowEntryPusherService methods
    
    @Override
    public void addFlow(String name, OFFlowMod fm, String swDpid) {
        Map<String, Object> fmMap = StaticFlowEntries.flowModToStorageEntry(fm, swDpid, name);
        storageSource.insertRowAsync(TABLE_NAME, fmMap);
    }

    @Override
    public void deleteFlow(String name) {
        storageSource.deleteRowAsync(TABLE_NAME, name);
    }
    
    @Override
    public void deleteAllFlows() {
        for (String entry : entry2dpid.keySet()) {
            deleteFlow(entry);
        }
        
        /*
        FIXME: Since the OF spec 1.0 is not clear on how
        to match on cookies. Once all switches come to a
        common implementation we can possibly re-enable this
        fix.
        
        // Send a delete for each switch
        Set<String> swSet = new HashSet<String>();
        for (String dpid : entry2dpid.values()) {
            // Avoid sending duplicate deletes
            if (!swSet.contains(dpid)) {
                swSet.add(dpid);
                sendDeleteByCookie(HexString.toLong(dpid));
            }
        }
        
        // Clear our map
        entry2dpid.clear();
        
        // Clear our book keeping map
        for (Map<String, OFFlowMod> eMap : entriesFromStorage.values()) {
            eMap.clear();
        }
        
        // Reset our DB
        storageSource.deleteMatchingRowsAsync(TABLE_NAME, null);
        */
    }
    
    @Override
    public void deleteFlowsForSwitch(long dpid) {
        String sDpid = HexString.toHexString(dpid);
        
        for (Entry<String, String> e : entry2dpid.entrySet()) {
            if (e.getValue().equals(sDpid))
                deleteFlow(e.getKey());
        }
        
        /*
        FIXME: Since the OF spec 1.0 is not clear on how
        to match on cookies. Once all switches come to a
        common implementation we can possibly re-enable this
        fix.
        //sendDeleteByCookie(dpid);
        
        String sDpid = HexString.toHexString(dpid);
        // Clear all internal flows for this switch
        Map<String, OFFlowMod> sMap = entriesFromStorage.get(sDpid);
        if (sMap != null) {
            for (String entryName : sMap.keySet()) {
                entry2dpid.remove(entryName);
                // Delete from DB
                deleteFlow(entryName);
            }
            sMap.clear();
        } else {
            log.warn("Map of storage entries for switch {} was null", sDpid);
        }
        */
    }
    
    /**
     * Deletes all flows installed by static flow pusher on a given switch.
     * We send a delete flow mod with the static flow pusher app ID in the cookie.
     * Since OF1.0 doesn't support masking based on the cookie we have to 
     * disable having flow specific cookies.
     * @param dpid The DPID of the switch to clear all it's flows.
     */
    /*
    FIXME: Since the OF spec 1.0 is not clear on how
    to match on cookies. Once all switches come to a
    common implementation we can possibly re-enable this
    fix.
    private void sendDeleteByCookie(long dpid) {
        if (log.isDebugEnabled())
            log.debug("Deleting all static flows on switch {}", HexString.toHexString(dpid));
        
        IOFSwitch sw = floodlightProvider.getSwitches().get(dpid);
        if (sw == null) {
            log.warn("Tried to delete static flows for non-existant switch {}",
                    HexString.toHexString(dpid));
            return;
        }
        
        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().
                getMessage(OFType.FLOW_MOD);
        OFMatch ofm = new OFMatch();
        fm.setMatch(ofm);
        fm.setCookie(AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, 0));
        fm.setCommand(OFFlowMod.OFPFC_DELETE);
        fm.setOutPort(OFPort.OFPP_NONE);

        try {
            sw.write(fm, null);
            sw.flush();
        } catch (IOException e1) {
            log.error("Error deleting all flows for switch {}:\n {}", 
                    HexString.toHexString(dpid), e1.getMessage());
            return;
        }
    }
    */
    
    @Override
    public Map<String, Map<String, OFFlowMod>> getFlows() {
        return entriesFromStorage;
    }
    
    @Override
    public Map<String, OFFlowMod> getFlows(String dpid) {
        return entriesFromStorage.get(dpid);
    }
    
    // IHAListener
    
    @Override
    public void roleChanged(Role oldRole, Role newRole) {
        switch(newRole) {
            case MASTER:
                if (oldRole == Role.SLAVE) {
                    log.debug("Re-reading static flows from storage due " +
                            "to HA change from SLAVE->MASTER");
                    entriesFromStorage = readEntriesFromStorage(); 
                    entry2dpid = computeEntry2DpidMap(entriesFromStorage);
                }
                break;
            case SLAVE:
                log.debug("Clearing in-memory flows due to " +
                        "HA change to SLAVE");
                entry2dpid.clear();
                entriesFromStorage.clear();
                break;
            default:
            	break;
        }
    }
    
    @Override
    public void controllerNodeIPsChanged(
            Map<String, String> curControllerNodeIPs,
            Map<String, String> addedControllerNodeIPs,
            Map<String, String> removedControllerNodeIPs) {
        // ignore
    }
}
