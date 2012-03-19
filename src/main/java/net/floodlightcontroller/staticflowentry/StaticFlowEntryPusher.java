package net.floodlightcontroller.staticflowentry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.web.StaticFlowEntryWebRoutable;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.IStorageSourceListener;

import net.floodlightcontroller.storage.StorageException;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticFlowEntryPusher 
    implements IOFSwitchListener, IFloodlightModule,
        IStorageSourceListener, IOFMessageListener {
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

    // entriesFromStorage[$dpid]->[$entryName = FlowMod] ; FlowMod can be null to indicate non-active
    protected HashMap<String, Map<String, OFFlowMod>> entriesFromStorage;
    protected Map<String, String> entry2dpid;

    private BasicFactory ofMessageFactory;

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

    protected void initDefaultFlowMod(OFFlowMod fm, String entryName) {
        fm.setIdleTimeout((short) 0);   // infinite
        fm.setHardTimeout((short) 0);   // infinite
        fm.setBufferId(-1);
        fm.setCommand((short) 0);
        fm.setFlags((short) 0);
        fm.setOutPort(OFPort.OFPP_NONE.getValue());
        fm.setCookie(computeEntryCookie(fm, 0, entryName));  
        fm.setPriority(Short.MAX_VALUE);
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
     * @param sw
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
                try {
                    if ( flowMod != null) {
                        log.debug("  push static entry {} for {}", dpid, entryName);
                        sw.write(flowMod, null);
                        sw.flush();
                        // Srini: FlowMod Sent to Switches
                    }
                } catch (IOException e) {
                    log.error("Failed to push static flow entry for {}: {}",
                            dpid, e.getMessage());
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
                HashMap<String, Map<String, OFFlowMod>> map) {
        Map<String, String> ret = new HashMap<String, String>();
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
    
    private HashMap<String, Map<String, OFFlowMod>> readEntriesFromStorage() {
        HashMap<String, Map<String, OFFlowMod>> entries = new HashMap<String, Map<String, OFFlowMod>>();
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

    void parseRow(Map<String, Object> row,
            HashMap<String, Map<String, OFFlowMod>> entries) {
        String switchName = null;
        String entryName = null;

        StringBuffer matchString = new StringBuffer();
        if (ofMessageFactory == null) // lazy init
            ofMessageFactory = new BasicFactory();

        OFFlowMod flowMod = (OFFlowMod) ofMessageFactory
                .getMessage(OFType.FLOW_MOD);

        if (!row.containsKey(COLUMN_SWITCH) || !row.containsKey(COLUMN_NAME)) {
            log.error(
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
            initDefaultFlowMod(flowMod, entryName);
            
            for (String key : row.keySet()) {
                if (row.get(key) == null)
                    continue;
                if ( key.equals(COLUMN_SWITCH) || key.equals(COLUMN_NAME)
                        || key.equals("id")) // "id" seems implicit in cassandra
                    continue; // already handled
                // explicitly ignore timeouts and wildcards
                if ( key.equals(COLUMN_HARD_TIMEOUT) || key.equals(COLUMN_IDLE_TIMEOUT) ||
                        key.equals(COLUMN_WILDCARD))
                    continue;
                if ( key.equals(COLUMN_ACTIVE)) {
                    if  (! Boolean.valueOf((String) row.get(COLUMN_ACTIVE))) {
                        log.debug("skipping inactive entry {} for switch {}",
                                entryName, switchName);
                        entries.get(switchName).put(entryName, null);  // mark this an inactive
                        return;
                    }
                } else if ( key.equals(COLUMN_ACTIONS)){
                    StaticFlowEntries.parseActionString(entryName, switchName, flowMod,
                            (String) row.get(COLUMN_ACTIONS), log);
                } else if ( key.equals(COLUMN_COOKIE)) {
                    flowMod.setCookie(
                            computeEntryCookie(flowMod, 
                                    Integer.valueOf( (String) row.get(COLUMN_COOKIE)), 
                                    entryName)
                        );
                } else if ( key.equals(COLUMN_PRIORITY)) {
                    flowMod.setPriority(U16.t(Integer.valueOf((String) row.get(COLUMN_PRIORITY))));
                } else { // the rest of the keys are for OFMatch().fromString()
                    if (matchString.length() > 0)
                        matchString.append(",");
                    matchString.append(key + "=" + row.get(key).toString());
                }
            }
        } catch (ClassCastException e) {
            if (entryName != null && switchName != null)
                log.error(
                        "skipping entry {} on switch {} with bad data : "
                                + e.getMessage(), entryName, switchName);
            else
                log.error("skipping entry with bad data: {} :: {} ",
                        e.getMessage(), e.getStackTrace());
        }

        OFMatch ofMatch = new OFMatch();
        String match = matchString.toString();
        try {
            ofMatch.fromString(match);
        } catch (IllegalArgumentException e) {
            log.error(
                    "ignoring flow entry {} on switch {} with illegal OFMatch() key: "
                            + match, entryName, switchName);
            return;
        }
        flowMod.setMatch(ofMatch);

        entries.get(switchName).put(entryName, flowMod);
    }
    
    /**
     * This function generates a random hash for the bottom half of the cookie
     * 
     * FIXME Decide if we really want this... it's actually very likely that this was
     * a misunderstanding that cookies needed to be unique, which they definitely don't.
     * 
     * @param fm
     * @param userCookie
     * @param name
     * @return A cookie that encodes the Application ID and a hash
     */
    
    public static long computeEntryCookie(OFFlowMod fm, int userCookie, String name) {
        // flow-specific hash is next 20 bits LOOK! who knows if this 
        int prime = 211;
        int flowHash = 2311;
        for (int i=0; i < name.length(); i++)
            flowHash = flowHash * prime + (int)name.charAt(i);
        
        return AppCookie.makeCookie(STATIC_FLOW_APP_ID, flowHash);
    }
    
    @Override
    public void addedSwitch(IOFSwitch sw) {
        log.debug("addedSwitch {}; processing its static entries", sw);
        sendEntriesToSwitch(sw);
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        log.debug("removedSwitch {}", sw);
        // do NOT delete from our internal state; we're tracking the rules,
        // not the switches
    }

    /**
     * This handles both rowInsert() and rowUpdate()
     */
    
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        log.debug("Modifying Table {}", tableName);

        HashMap<String, Map<String, OFFlowMod>> entriesToAdd = 
            new HashMap<String, Map<String, OFFlowMod>>();
        // build up list of what was added 
        for(Object key: rowKeys) {
            IResultSet resultSet = storageSource.getRow(tableName, key);
            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                Map<String, Object> row = it.next().getRow();
                parseRow(row, entriesToAdd);
            }            
        }
        // batch updates by switch and blast them out
        for (String dpid : entriesToAdd.keySet()) {
            if (!entriesFromStorage.containsKey(dpid))
                entriesFromStorage.put(dpid, new HashMap<String, OFFlowMod>());
            List<OFMessage> outQueue = new ArrayList<OFMessage>();
            for(String entry: entriesToAdd.get(dpid).keySet()) {
                OFFlowMod newFlowMod = entriesToAdd.get(dpid).get(entry);
                OFFlowMod oldFlowMod = entriesFromStorage.get(dpid).get(entry);
                if (oldFlowMod != null) {  // remove any pre-existing rule
                    oldFlowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
                    outQueue.add(oldFlowMod);
                }
                if ( newFlowMod != null) {
                    entriesFromStorage.get(dpid).put(entry, newFlowMod);
                    outQueue.add(newFlowMod);
                    entry2dpid.put(entry, dpid);
                } else {
                    entriesFromStorage.get(dpid).remove(entry);
                    entry2dpid.remove(entry);
                }
            }
            IOFSwitch ofswitch = floodlightProvider.getSwitches().get(HexString.toLong(dpid));
            if (ofswitch != null) {  // is the switch connected
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Sending {} new entries to {}", outQueue.size(), dpid);
                    }
                    ofswitch.write(outQueue, null);
                    ofswitch.flush();
                } catch (IOException e) {
                    log.error("writed to write to switch {} but got {}", 
                            dpid, e.getMessage());
                }
            }
        }
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        if (log.isDebugEnabled()) {
            log.debug("deleting from Table {}", tableName);
        }
        
        for(Object obj : rowKeys) {
            if (!(obj instanceof String)) {
                log.error("tried to delete non-string key {}; ignoring", obj);
                continue;
            }
            String entryName = (String) obj;
            String dpid = entry2dpid.get(entryName);
            if (dpid == null) {
                log.error("inconsistent internal state: no switch has rule {}",
                        entryName);
                continue;
            }
            // send flow_mod delete
            OFFlowMod flowMod = entriesFromStorage.get(dpid).get(entryName);
            flowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
            log.debug("deleting flow {} for switch {}", entryName, dpid);
            Map<Long,IOFSwitch> switches = floodlightProvider.getSwitches();

            if ( entriesFromStorage.containsKey(dpid) && 
                    entriesFromStorage.get(dpid).containsKey(entryName)) 
                entriesFromStorage.get(dpid).remove(entryName);
            else 
                log.error("tried to delete non-existent entry {} for switch {}", 
                        entryName, dpid);
            
            IOFSwitch ofSwitch = switches.get(HexString.toLong(dpid));
            if ( ofSwitch == null) {
                log.debug("Not deleting key {} :: switch {} not connected", dpid);
                continue;
            }
            try {
                ofSwitch.write(flowMod, null);
                ofSwitch.flush();
            } catch (IOException e) {
                log.error("tried to write flow_mod delete to {} but failed: {}", 
                        dpid, e.getMessage());
            }
        }
    }

    @Override
    public String getName() {
        return StaticFlowName;
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
        case FLOW_REMOVED:
            break;
        default:
            log.warn("ignoring unrequested message: {}", msg);
            return Command.CONTINUE;
        }
        OFFlowRemoved flowRemoved = (OFFlowRemoved) msg;
        long cookie = flowRemoved.getCookie();
        /**
         * This is just to sanity check our assumption that static flows 
         * never expire.
         */
        if( AppCookie.extractApp(cookie) == STATIC_FLOW_APP_ID) {
            if ( flowRemoved.getReason() != OFFlowRemoved.OFFlowRemovedReason.OFPRR_DELETE)
                log.error("PANIC -- got a FlowRemove message for a infinite timeout flow: {} from switch {}", 
                        msg, sw);
            return Command.STOP;    // only for us
        } else
            return Command.CONTINUE;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // packet in listener goes after topology
        if (type == OFType.PACKET_IN && "topology".equals(name)) {
            return true;
        }
        return false;  // no dependency for non-packet in
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // packet in listener goes before devicemanager
        if (type == OFType.PACKET_IN && "devicemanager".equals(name)) {
            return true;
        }
        return false;  // no dependency for non-packet in
    }

    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't have any services
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        // We don't have any services
        return null;
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
        // assumes no switches connected at startup()
        
        storageSource.createTable(TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_NAME);
        storageSource.addListener(TABLE_NAME, this);
        entriesFromStorage = readEntriesFromStorage(); 
        entry2dpid = computeEntry2DpidMap(entriesFromStorage);
        restApi.addRestletRoutable(new StaticFlowEntryWebRoutable());
    }
}
