package net.floodlightcontroller.firewall;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Firewall implements IFirewallService, IOFMessageListener, IFloodlightModule {

    protected IFloodlightProviderService floodlightProvider;
    protected IRoutingService routingEngine;
    protected IStorageSourceService storageSource;
    protected IRestApiService restApi;
    protected static Logger logger;
    protected List<FirewallRule> rules; // protected by synchornized
    protected boolean enabled;
    protected int subnet_mask = IPv4.toIPv4Address("255.255.255.0");

    public static final String TABLE_NAME = "controller_firewallrules";
    public static final String COLUMN_RULEID = "ruleid";
    public static final String COLUMN_SWITCHID = "switchid";
    public static final String COLUMN_SRC_INPORT = "src_inport";
    public static final String COLUMN_SRC_IP_PREFIX = "src_ip_prefix";
    public static final String COLUMN_SRC_IP_BITS = "src_ip_bits";
    public static final String COLUMN_SRC_MAC = "src_mac";
    public static final String COLUMN_PROTO_TYPE = "proto_type";
    public static final String COLUMN_PROTO_SRCPORT = "proto_srcport";
    public static final String COLUMN_PROTO_DSTPORT = "proto_dstport";
    public static final String COLUMN_DST_IP_PREFIX = "dst_ip_prefix";
    public static final String COLUMN_DST_IP_BITS = "dst_ip_bits";
    public static final String COLUMN_DST_MAC = "dst_mac";
    public static final String COLUMN_WILDCARD_SWITCHID = "wildcard_switchid";
    public static final String COLUMN_WILDCARD_SRC_INPORT = "wildcard_src_inport";
    public static final String COLUMN_WILDCARD_SRC_MAC = "wildcard_src_mac";
    public static final String COLUMN_WILDCARD_SRC_IP = "wildcard_src_ip";
    public static final String COLUMN_WILDCARD_PROTO_TYPE = "wildcard_proto_type";
    public static final String COLUMN_WILDCARD_DST_MAC = "wildcard_dst_mac";
    public static final String COLUMN_WILDCARD_DST_IP = "wildcard_dst_ip";
    public static final String COLUMN_PRIORITY = "priority";
    public static final String COLUMN_IS_DENYRULE = "is_denyrule";
    public static String ColumnNames[] = { COLUMN_RULEID, COLUMN_SWITCHID,
        COLUMN_SRC_INPORT, COLUMN_SRC_MAC, COLUMN_SRC_IP_PREFIX, COLUMN_SRC_IP_BITS,
        COLUMN_PROTO_TYPE, COLUMN_PROTO_SRCPORT, COLUMN_PROTO_DSTPORT,
        COLUMN_DST_MAC, COLUMN_DST_IP_PREFIX, COLUMN_DST_IP_BITS,
        COLUMN_WILDCARD_SWITCHID, COLUMN_WILDCARD_SRC_INPORT, COLUMN_WILDCARD_SRC_MAC,
        COLUMN_WILDCARD_SRC_IP, COLUMN_WILDCARD_PROTO_TYPE, COLUMN_WILDCARD_DST_MAC,
        COLUMN_WILDCARD_DST_IP, COLUMN_PRIORITY, COLUMN_IS_DENYRULE };


    @Override
    public String getName() {
        return "firewall";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFirewallService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
        new HashMap<Class<? extends IFloodlightService>,
        IFloodlightService>();
        // We are the class that implements the service
        m.put(IFirewallService.class, this);
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
    
    /**
     * Reads the rules from the storage and creates a sorted arraylist of FirewallRule
     * from them.
     * @return the sorted arraylist of FirewallRule instances (rules from storage)
     */
    protected ArrayList<FirewallRule> readRulesFromStorage() {
        ArrayList<FirewallRule> l = new ArrayList<FirewallRule>();

        try {
            Map<String, Object> row;
            // null1=no predicate, null2=no ordering
            IResultSet resultSet = storageSource.executeQuery(TABLE_NAME,
                    ColumnNames, null, null);
            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                row = it.next().getRow();
                // now, parse row
                FirewallRule r = new FirewallRule();
                if (!row.containsKey(COLUMN_RULEID) || !row.containsKey(COLUMN_SWITCHID)) {
                    logger.error(
                            "skipping entry with missing required 'ruleid' or 'switchid' entry: {}",
                            row);
                    return l;
                }
                // most error checking done with ClassCastException
                try {
                    // first, snag the required entries, for debugging info
                    r.ruleid = Integer.parseInt((String)row.get(COLUMN_RULEID));
                    r.switchid = Long.parseLong((String)row.get(COLUMN_SWITCHID));

                    for (String key : row.keySet()) {
                        if (row.get(key) == null)
                            continue;
                        if ( key.equals(COLUMN_RULEID) || key.equals(COLUMN_SWITCHID) || key.equals("id")) {
                            continue; // already handled
                        } else if ( key.equals(COLUMN_SRC_INPORT)) {
                            r.src_inport = Short.parseShort((String)row.get(COLUMN_SRC_INPORT));
                        } else if ( key.equals(COLUMN_SRC_MAC)) {
                            r.src_mac = Long.parseLong((String)row.get(COLUMN_SRC_MAC));
                        } else if ( key.equals(COLUMN_SRC_IP_PREFIX)) {
                            r.src_ip_prefix = Integer.parseInt((String)row.get(COLUMN_SRC_IP_PREFIX));
                        } else if ( key.equals(COLUMN_SRC_IP_BITS)) {
                            r.src_ip_bits = Integer.parseInt((String)row.get(COLUMN_SRC_IP_BITS));
                        } else if ( key.equals(COLUMN_PROTO_TYPE)) {
                            r.proto_type = Short.parseShort((String)row.get(COLUMN_PROTO_TYPE));
                        } else if ( key.equals(COLUMN_PROTO_SRCPORT)) {
                            r.proto_srcport = Short.parseShort((String)row.get(COLUMN_PROTO_SRCPORT));
                        } else if ( key.equals(COLUMN_PROTO_DSTPORT)) {
                            r.proto_dstport = Short.parseShort((String)row.get(COLUMN_PROTO_DSTPORT));
                        } else if ( key.equals(COLUMN_DST_MAC)) {
                            r.dst_mac = Long.parseLong((String)row.get(COLUMN_DST_MAC));
                        } else if ( key.equals(COLUMN_DST_IP_PREFIX)) {
                            r.dst_ip_prefix = Integer.parseInt((String)row.get(COLUMN_DST_IP_PREFIX));
                        } else if ( key.equals(COLUMN_DST_IP_BITS)) {
                            r.dst_ip_bits = Integer.parseInt((String)row.get(COLUMN_DST_IP_BITS));
                        } else if ( key.equals(COLUMN_WILDCARD_SWITCHID)) {
                            r.wildcard_switchid = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SWITCHID));
                        } else if ( key.equals(COLUMN_WILDCARD_SRC_INPORT)) {
                            r.wildcard_src_inport = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SRC_INPORT));
                        } else if ( key.equals(COLUMN_WILDCARD_SRC_MAC)) {
                            r.wildcard_src_mac = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SRC_MAC));
                        } else if ( key.equals(COLUMN_WILDCARD_SRC_IP)) {
                            r.wildcard_src_ip = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_SRC_IP));
                        } else if ( key.equals(COLUMN_WILDCARD_PROTO_TYPE)) {
                            r.wildcard_proto_type = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_PROTO_TYPE));
                        } else if ( key.equals(COLUMN_WILDCARD_DST_MAC)) {
                            r.wildcard_dst_mac = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_DST_MAC));
                        } else if ( key.equals(COLUMN_WILDCARD_DST_IP)) {
                            r.wildcard_dst_ip = Boolean.parseBoolean((String)row.get(COLUMN_WILDCARD_DST_IP));
                        } else if ( key.equals(COLUMN_PRIORITY)) {
                            r.priority = Integer.parseInt((String)row.get(COLUMN_PRIORITY));
                        } else if ( key.equals(COLUMN_IS_DENYRULE)) {
                            r.is_denyrule = Boolean.parseBoolean((String)row.get(COLUMN_IS_DENYRULE));
                        }
                    }
                } catch (ClassCastException e) {
                    logger.error("skipping rule {} with bad data : " + e.getMessage(), r.ruleid);
                }
                l.add(r);
            }
        } catch (StorageException e) {
            logger.error("failed to access storage: {}", e.getMessage());
            // if the table doesn't exist, then wait to populate later via
            // setStorageSource()
        }

        // now, sort the list based on priorities
        Collections.sort(l);

        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        storageSource = context.getServiceImpl(IStorageSourceService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        rules = new ArrayList<FirewallRule>();
        logger = LoggerFactory.getLogger(Firewall.class);
        // start disabled
        enabled = false;
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // initialize REST interface
        restApi.addRestletRoutable(new FirewallWebRoutable());
        // start firewall if enabled at bootup
        if (this.enabled == true) {
            floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        }
        // storage, create table and read rules
        storageSource.createTable(TABLE_NAME, null);
        storageSource.setTablePrimaryKeyName(TABLE_NAME, COLUMN_RULEID);
        synchronized (rules) {
            this.rules = readRulesFromStorage();
        }
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        if (!this.enabled) return Command.CONTINUE;

        switch (msg.getType()) {
            case PACKET_IN:
                IRoutingDecision decision = null;
                if (cntx != null) {
                    decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);

                    return this.processPacketInMessage(sw,
                            (OFPacketIn) msg,
                            decision,
                            cntx);
                }
                break;
            default:
            	break;
        }

        return Command.CONTINUE;
    }

    @Override
    public void enableFirewall() {
        // check if the firewall module is not listening for events, if not, then start listening (enable it)
        List<IOFMessageListener> listeners = floodlightProvider.getListeners().get(OFType.PACKET_IN);
        if ((listeners != null) && (!listeners.contains(this))) {
            // enable firewall, i.e. listen for packet-in events
            floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        }
        this.enabled = true;
    }

    @Override
    public void disableFirewall() {
        // check if the firewall module is listening for events, if yes, then remove it from listeners (disable it)
        List<IOFMessageListener> listeners = floodlightProvider.getListeners().get(OFType.PACKET_IN);
        if ((listeners != null) && (listeners.contains(this))) {
            // disable firewall, i.e. stop listening for packet-in events
            floodlightProvider.removeOFMessageListener(OFType.PACKET_IN, this);
        }
        this.enabled = false;
    }

    @Override
    public List<FirewallRule> getRules() {
        return this.rules;
    }

    @Override
    public List<Map<String, Object>> getStorageRules() {
        ArrayList<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
        try {
            // null1=no predicate, null2=no ordering
            IResultSet resultSet = storageSource.executeQuery(TABLE_NAME, ColumnNames, null, null);
            for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
                l.add(it.next().getRow());
            }
        } catch (StorageException e) {
            logger.error("failed to access storage: {}", e.getMessage());
            // if the table doesn't exist, then wait to populate later via
            // setStorageSource()
        }
        return l;
    }
    
    @Override
    public String getSubnetMask() {
        return IPv4.fromIPv4Address(this.subnet_mask);
    }
    
    @Override
    public void setSubnetMask(String newMask) {
        if (newMask.trim().isEmpty()) return;
        this.subnet_mask = IPv4.toIPv4Address(newMask.trim());
    }

    @Override
    public synchronized void addRule(FirewallRule rule) {
        rule.ruleid = rule.genID();
        int i = 0;
        // locate the position of the new rule in the sorted arraylist
        for (i = 0; i < this.rules.size(); i++) {
            if (this.rules.get(i).priority >= rule.priority) break;
        }
        // now, add rule to the list
        if (i <= this.rules.size()) {
            this.rules.add(i, rule);
        } else {
            this.rules.add(rule);
        }
        // add rule to database
        Map<String, Object> entry = new HashMap<String, Object>();
        entry.put(COLUMN_RULEID, rule.ruleid);
        entry.put(COLUMN_SWITCHID, Long.toString(rule.switchid));
        entry.put(COLUMN_SRC_INPORT, Short.toString(rule.src_inport));
        entry.put(COLUMN_SRC_MAC, Long.toString(rule.src_mac));
        entry.put(COLUMN_SRC_IP_PREFIX, Integer.toString(rule.src_ip_prefix));
        entry.put(COLUMN_SRC_IP_BITS, Integer.toString(rule.src_ip_bits));
        entry.put(COLUMN_PROTO_TYPE, Short.toString(rule.proto_type));
        entry.put(COLUMN_PROTO_SRCPORT, Integer.toString(rule.proto_srcport));
        entry.put(COLUMN_PROTO_DSTPORT, Integer.toString(rule.proto_dstport));
        entry.put(COLUMN_DST_MAC, Long.toString(rule.dst_mac));
        entry.put(COLUMN_DST_IP_PREFIX, Integer.toString(rule.dst_ip_prefix));
        entry.put(COLUMN_DST_IP_BITS, Integer.toString(rule.dst_ip_bits));
        entry.put(COLUMN_WILDCARD_SWITCHID, Boolean.toString(rule.wildcard_switchid));
        entry.put(COLUMN_WILDCARD_SRC_INPORT, Boolean.toString(rule.wildcard_src_inport));
        entry.put(COLUMN_WILDCARD_SRC_MAC, Boolean.toString(rule.wildcard_src_mac));
        entry.put(COLUMN_WILDCARD_SRC_IP, Boolean.toString(rule.wildcard_src_ip));
        entry.put(COLUMN_WILDCARD_PROTO_TYPE, Boolean.toString(rule.wildcard_proto_type));
        entry.put(COLUMN_WILDCARD_DST_MAC, Boolean.toString(rule.wildcard_dst_mac));
        entry.put(COLUMN_WILDCARD_DST_IP, Boolean.toString(rule.wildcard_dst_ip));
        entry.put(COLUMN_PRIORITY, Integer.toString(rule.priority));
        entry.put(COLUMN_IS_DENYRULE, Boolean.toString(rule.is_denyrule));
        storageSource.insertRow(TABLE_NAME, entry);
    }

    @Override
    public synchronized void deleteRule(int ruleid) {
        Iterator<FirewallRule> iter = this.rules.iterator();
        while (iter.hasNext()) {
            FirewallRule r = iter.next();
            if (r.ruleid == ruleid) {
                // found the rule, now remove it
                iter.remove();
                break;
            }
        }
        // delete from database
        storageSource.deleteRow(TABLE_NAME, ruleid);
    }

    /**
     * Iterates over the firewall rules and tries to match them with the incoming packet (flow).
     * Uses the FirewallRule class's matchWithFlow method to perform matching. It maintains a
     * pair of wildcards (allow and deny) which are assigned later to the firewall's decision,
     * where 'allow' wildcards are applied if the matched rule turns out to be an ALLOW rule
     * and 'deny' wildcards are applied otherwise. Wildcards are applied to firewall decision
     * to optimize flows in the switch, ensuring least number of flows per firewall rule.
     * So, if a particular field is not "ANY" (i.e. not wildcarded) in a higher priority rule,
     * then if a lower priority rule matches the packet and wildcards it, it can't be wildcarded
     * in the switch's flow entry, because otherwise some packets matching the higher priority rule
     * might escape the firewall.
     * The reason for keeping different two different wildcards is that if a field is not wildcarded
     * in a higher priority allow rule, the same field shouldn't be wildcarded for packets matching
     * the lower priority deny rule (non-wildcarded fields in higher priority rules override the
     * wildcarding of those fields in lower priority rules of the opposite type). So, to ensure that
     * wildcards are appropriately set for different types of rules (allow vs. deny), separate wildcards
     * are maintained.
     * Iteration is performed on the sorted list of rules (sorted in decreasing order of priority).
     * @param sw the switch instance
     * @param pi the incoming packet data structure
     * @param cntx the floodlight context
     * @return an instance of RuleWildcardsPair that specify rule that matches and the wildcards
     * for the firewall decision
     */
    protected RuleWildcardsPair matchWithRule(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        FirewallRule matched_rule = null;
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        WildcardsPair wildcards = new WildcardsPair();

        synchronized (rules) {
            Iterator<FirewallRule> iter = this.rules.iterator();
            FirewallRule rule = null;
            // iterate through list to find a matching firewall rule
            while (iter.hasNext()) {
                // get next rule from list
                rule = iter.next();
    
                // check if rule matches
                if (rule.matchesFlow(sw.getId(), pi.getInPort(), eth, wildcards) == true) {
                    matched_rule = rule;
                    break;
                }
            }
        }

        // make a pair of rule and wildcards, then return it
        RuleWildcardsPair ret = new RuleWildcardsPair();
        ret.rule = matched_rule;
        if (matched_rule == null || matched_rule.is_denyrule) {
            ret.wildcards = wildcards.drop;
        } else {
            ret.wildcards = wildcards.allow;
        }
        return ret;
    }

    /**
     * Checks whether an IP address is a broadcast address or not (determines using subnet mask)
     * @param IPAddress the IP address to check
     * @return true if it is a broadcast address, false otherwise
     */
    protected boolean IPIsBroadcast(int IPAddress) {
        // inverted subnet mask
        int inv_subnet_mask = ~this.subnet_mask;
        return ((IPAddress & inv_subnet_mask) == inv_subnet_mask);
    }

    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // Allowing L2 broadcast + ARP broadcast request (also deny malformed broadcasts -> L2 broadcast + L3 unicast)
        if (eth.isBroadcast() == true) {
            boolean allowBroadcast = true;
            // the case to determine if we have L2 broadcast + L3 unicast
            // don't allow this broadcast packet if such is the case (malformed packet)
            if (eth.getEtherType() == Ethernet.TYPE_IPv4 &&
                    this.IPIsBroadcast(((IPv4)eth.getPayload()).getDestinationAddress()) == false) {
                allowBroadcast = false;
            }
            if (allowBroadcast == true) {
                if (logger.isTraceEnabled())
                    logger.trace("Allowing broadcast traffic for PacketIn={}", pi);
                
                decision = new FirewallDecision(IRoutingDecision.RoutingAction.MULTICAST);
                decision.addToContext(cntx);
            } else {
                if (logger.isTraceEnabled())
                    logger.trace("Blocking malformed broadcast traffic for PacketIn={}", pi);
                
                decision = new FirewallDecision(IRoutingDecision.RoutingAction.DROP);
                decision.addToContext(cntx);
            }
            return Command.CONTINUE;
        }
        /* ARP response (unicast) can be let through without filtering through rules by uncommenting the code below */
        /*
        else if (eth.getEtherType() == Ethernet.TYPE_ARP) {
            logger.info("allowing ARP traffic");
            decision = new FirewallDecision(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
            decision.addToContext(cntx);
            return Command.CONTINUE;
        }
        */

        // check if we have a matching rule for this packet/flow
        // and no decision is taken yet
        if (decision == null) {
            RuleWildcardsPair match_ret = this.matchWithRule(sw, pi, cntx);
            FirewallRule rule = match_ret.rule;

            if (rule == null || rule.is_denyrule) {
                decision = new FirewallDecision(IRoutingDecision.RoutingAction.DROP);
                decision.setWildcards(match_ret.wildcards);
                decision.addToContext(cntx);
                if (logger.isTraceEnabled()) {
                    if (rule == null)
                        logger.trace("No firewall rule found for PacketIn={}, blocking flow",
                                    pi);
                    else if (rule.is_denyrule) {
                        logger.trace("Deny rule={} match for PacketIn={}",
                                     rule, pi);
                    }
                }
            } else {
                decision = new FirewallDecision(IRoutingDecision.RoutingAction.FORWARD_OR_FLOOD);
                decision.setWildcards(match_ret.wildcards);
                decision.addToContext(cntx);
                if (logger.isTraceEnabled())
                    logger.trace("Allow rule={} match for PacketIn={}", rule, pi);
            }
        }

        return Command.CONTINUE;
    }

}
