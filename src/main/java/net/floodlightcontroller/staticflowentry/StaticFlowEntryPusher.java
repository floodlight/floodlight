/**
 *    Copyright 2011, Big Switch Networks, Inc. 
 *    Originally created by David Erickson, Stanford University
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

/**
 * Implements the staticflowentry pusher
 * 
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticFlowEntryPusher implements IStaticFlowEntryPusher, IOFSwitchListener {

    // Utility data structure
    private class FlowModFields {
        public long dpid = 0;
        public String dpidStr = null;
        public String name = null;
        public boolean active = false;
        public OFFlowMod fm = null;
    }

    protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryPusher.class);
    protected IFloodlightProvider floodlightProvider;

    protected ArrayList<String> flowmodList;
    protected ArrayList<IOFSwitch> activeSwitches;
    protected HashMap<Long, HashMap<String, OFFlowMod>> flowmods;
    protected Long pushEntriesFrequency = 25L;
    protected Runnable pushEntriesTimer;

    public StaticFlowEntryPusher() {
        flowmodList = new ArrayList<String>();
        flowmods = new HashMap<Long, HashMap<String, OFFlowMod>>(); 
        activeSwitches = new ArrayList<IOFSwitch>();
    }
    
    @Override
    public String getName() {
        return "staticflowentry";
    }

    public IFloodlightProvider getFloodlightProvider() {
        return floodlightProvider;
    }

    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
    
    public long getFlowPushTimeSeconds() {
        return pushEntriesFrequency;
    }

    @Override
    public void addedSwitch(IOFSwitch sw) {
        log.debug("addedSwitch: {}", sw);
        if (!activeSwitches.contains(sw)) {
            activeSwitches.add(sw);
        }
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        log.debug("removedSwitch: {}", sw);
        if (activeSwitches.contains(sw)) {
            activeSwitches.remove(sw);
        }
    }

    /** 
     * Get the list of all active switches
     */
    public ArrayList<IOFSwitch> getActiveSwitches() {
        return activeSwitches;
    }

    /**
     * Pushes a flow-mod to this switch as a one-time push
     * 
     * @param sw
     * @param fm
     */
    public void pushEntry(IOFSwitch sw, OFFlowMod fm) {
        log.debug("pushEntry: switch: {}, flow-mod: {}", sw, fm);            
    
        // override values with our settings
        fm.setIdleTimeout((short) 0);
        fm.setHardTimeout((short) 30);
        fm.setBufferId(-1);
        fm.setCommand((short) 0);
        fm.setFlags((short) 0);
        
        try {
            sw.write(fm, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Pushes a flow-mod to this switch as a one-time push
     * (alternate form)
     */
    public void pushEntry(long dpid, OFFlowMod fm) {
        IOFSwitch sw = this.floodlightProvider.getSwitches().get(dpid);
        if (sw != null) {
            this.pushEntry(sw, fm);
        }
        else {
            log.error("pushEntry: No such switch:", dpid);
        }
    }

    /**
     * Adds this flow-mod to the list of flow-mods being pushed regularly
     * (and also pushes it right away)
     * 
     * If a flow-mod by same name exists, it is updated with new values
     * 
     */
    public void addEntry(long dpid, String name, boolean active, OFFlowMod fm) {
        log.debug("addEntry: add dpid: {}, name: {}", dpid, name);
        
        /*
         * For now, we do not add inactive flow-mods since they will need to be added again
         * to make them active. In future, this will change when use persistent storage.
         */
        IOFSwitch sw = this.floodlightProvider.getSwitches().get(dpid);
        if (!active || sw==null) {
            if (sw != null) {
                if (this.getEntry(sw, name) != null) {
                    log.debug("addEntry: removing inactive flowmod dpid: {}, name: {} [not active]", dpid, name);
                    this.removeEntry(sw, name);
                }
                else {
                    log.debug("addEntry: ignoring inactive flowmod dpid: {}, name: {} [not active]", dpid, name);
                }
            }
            else {
                log.debug("addEntry: ignoring unknown switch dpid: {}, name: {} [unknown switch]", dpid, name);
            }
            return;
        }
        
        HashMap<String, OFFlowMod> swentries;
        if (this.flowmods.containsKey(dpid)) {
            swentries = this.flowmods.get(dpid);
        }
        else {
            swentries = new HashMap<String, OFFlowMod>();
            this.flowmods.put(dpid, swentries);
        }
        
        if (swentries.containsKey(name)) {
            log.debug("addEntry: updating existing dpid: {}, name: {}", dpid, name);            
        }
        
        swentries.put(name, fm);
        this.pushEntry(dpid, fm);
    }

    /**
     * Get all flow-mod entries that have been pushed previously (for all switches)
     * 
     * returns a HashMap with:
     *             key = switch-id
     *             value = HashMap of entries where each entry in the entry HashMap has
     *                       key = flow-mod-name
     *                       value = OFFlowMod
     * 
     */
    public HashMap<Long, HashMap<String, OFFlowMod>> getEntries() {
            return this.flowmods;
    }

    /**
     * Get all flow-mods that have been pushed previously for a specific switch
     * 
     * @param sw
     */
    public HashMap<String, OFFlowMod> getEntries(IOFSwitch sw) {
        HashMap<String, OFFlowMod> ret = new HashMap<String, OFFlowMod>();
        if (this.flowmods != null && this.flowmods.containsKey(sw.getId())) {
                ret = this.flowmods.get(sw.getId());
        }
        return ret;  
    }

    /**
     * Get a specific flow-mod entry that has been pushed previously
     * 
     * @param sw
     * @param name
     */
    public OFFlowMod getEntry(IOFSwitch sw, String name) {
        OFFlowMod ret = null;
        HashMap<String, OFFlowMod> swentries = this.getEntries(sw);
        if (swentries.containsKey(name)) {
                    ret = swentries.get(name);
        }
        return ret;
    }

    /**
     * Remove a flow-mod entry that has been added previously
     * returns the flow-mod that has just been removed
     * 
     * @param sw
     * @param name
     */
    public OFFlowMod removeEntry(IOFSwitch sw, String name) {
        OFFlowMod ret = null;
        HashMap<String, OFFlowMod> swentries = this.getEntries(sw);
        if (swentries.containsKey(name)) {
            ret = swentries.get(name);
            swentries.remove(name);
        }
        return ret;
    }
    
    /**
     * Remove a flow-mod entry that has been added previously
     * returns the flow-mod that has just been removed
     * 
     * @param sw
     * @param name
     */
    public OFFlowMod removeEntry(long dpid, String name) {
        log.debug("removeEntry: switch {}, name {}", dpid, name);
        
        OFFlowMod ret = null;
        IOFSwitch sw = this.floodlightProvider.getSwitches().get(dpid);
        if (sw != null) {
            return removeEntry(sw, name);
        }
        return ret;
    }
    
    /**
     * Faster bulk remove for all flow-mods that has been pushed previously
     * returns flow-mods that has been removed (in HashMap like getEntries)
     * 
     */
    public HashMap<Long, HashMap<String, OFFlowMod>> removeEntries() {
        log.debug("removeEntries: all");
            HashMap<Long, HashMap<String, OFFlowMod>> ret = this.flowmods;
            this.flowmods = new HashMap<Long, HashMap<String, OFFlowMod>>();
            return ret;
    }
    
    /**
     * Faster bulk remove of all flow-mods that has been pushed previously for a switch
     * returns flow-mods that has been removed (in HashMap with names, flow-mods)
     * 
     * @param sw
     */
    public HashMap<String, OFFlowMod> removeEntries(IOFSwitch sw) {
        log.debug("removeEntries: switch: {}", sw);
            HashMap<String, OFFlowMod> ret;
        if (this.flowmods.containsKey(sw.getId())) {
                ret = this.flowmods.get(sw.getId());
                this.flowmods.remove(sw.getId());
        }
        else {
                // No flows deleted, return an empty hash map
                ret = new HashMap<String, OFFlowMod>();
        }
            return ret;
    }
    
    /**
     * JSON based interface
     */
    
    /**
     * Get an array JSON strings representing of all flow-mod entries that have been pushed previously
     * (for all switches)
     * 
     */
    public ArrayList<String> getEntryList() {
            return flowmodList;
    }

    /**
     * Remove a flow-mod entry that has been added previously.
     * Only the switch name and flow-mod name are read from the input argument fmJson,
     * the full JSON for the flow-mod entry being removed is returned.
     * 
     * @param fmJson
     */
    
    public String findEntry(String dpid, String name) {
        String ret = null;
        
        try {
            ObjectMapper mapper = new ObjectMapper();        
            for (String f : flowmodList) {
                @SuppressWarnings("unchecked")
                HashMap<String, String> fdata = mapper.readValue(f, HashMap.class);
                if (fdata.get("name").equalsIgnoreCase(name) &&
                        fdata.get("switch").equalsIgnoreCase(dpid)) {
                        ret = f;
                        break;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
        return ret;
        
    }
    
    public String removeEntry(String fmJson) {
        String ret = null;
            
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            HashMap<String, String> fmdata = mapper.readValue(fmJson, HashMap.class);
            String name = fmdata.get("name");
            String dpid = fmdata.get("switch");

            ret = this.findEntry(dpid, name);
            if (ret != null) {
                flowmodList.remove(ret);
                this.removeEntry(HexString.toLong(dpid), name);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }        
        return ret;
    }
    
    /**
     * Adds a flow-mod (as JSON string) to the list of flow-mods being pushed regularly.
     * If a flow-mod already exists for that switch/name, update it based on new entry.
     * (and also pushes it right away calling pushEntry, as appropriate) 
     * 
     * @param fmJson
     */
    public void addEntry(String fmJson) {
        try {
            MappingJsonFactory f = new MappingJsonFactory();
            JsonParser jp;
            try {
                jp = f.createJsonParser(fmJson);
            }
            catch (JsonParseException e) {
                log.error("error parsing push flow mod request: " + fmJson, e);
                throw new IOException(e);
            }
            
            FlowModFields fm = this.jsonToFlowMod(jp);
            if (fm.name != null && fm.dpid != 0) {
                String currfm = this.findEntry(fm.dpidStr, fm.name);
                if (currfm != null) {
                    flowmodList.remove(currfm);
               }
               flowmodList.add(fmJson);
               this.addEntry(fm.dpid, fm.name, fm.active, fm.fm);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Utility method to convert a JSON representation to an FlowModFields object representation.
     * 
     * Expects a JsonParser that takes json along the lines of:
     *        {
     *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
     *            "name":         "flow-mod-1",
     *            "cookie":       "0",
     *            "priority":     "32768",
     *            "ingress-port": "1",
     *            "actions":      "output=2",
     *        }
     * 
     * @param jp
     * @return FlowModFields
     */
    public FlowModFields jsonToFlowMod(JsonParser jp) {
        FlowModFields flowmod = new FlowModFields();

        try {
            String name = null;
            String switchDpid = null;
            String a = null;
            StringBuilder m = new StringBuilder();
            String priority = "0";
            String userCookie = "0";
            String wildcards = "0";
            boolean active = false;
        
            jp.nextToken();
            if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }
            
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Expected FIELD_NAME");
                }
                
                String n = jp.getCurrentName();
                jp.nextToken();
                if (jp.getText().equals("")) 
                    continue;
                if (n == "name")
                    name = jp.getText();
                if (n == "switch")
                    switchDpid = jp.getText();
                else if (n == "actions")
                    a = jp.getText();
                else if (n == "priority")
                    priority = jp.getText();
                else if (n == "active")
                    active = jp.getText().equals("true");
                else if (n == "cookie")
                    userCookie = jp.getText();
                else if (n == "wildcards")
                    wildcards = jp.getText();
                else if (n == "ingress-port")
                    m.append("," + OFMatch.STR_IN_PORT + "=" + jp.getText());
                else if (n == "src-mac")
                    m.append("," + OFMatch.STR_DL_SRC + "=" + jp.getText());
                else if (n == "dst-mac")
                    m.append("," + OFMatch.STR_DL_DST + "=" + jp.getText());
                else if (n == "vlan-id")
                    m.append("," + OFMatch.STR_DL_VLAN + "=" + jp.getText());
                else if (n == "vlan-priority")
                    m.append("," + OFMatch.STR_DL_VLAN_PCP + "=" + jp.getText());
                else if (n == "ether-type")
                    m.append("," + OFMatch.STR_DL_TYPE + "=" + jp.getText());
                else if (n == "tos-bits")
                    m.append("," + OFMatch.STR_NW_TOS + "=" + jp.getText());
                else if (n == "protocol")
                    m.append("," + OFMatch.STR_NW_PROTO + "=" + jp.getText());
                else if (n == "src-ip")
                    m.append("," + OFMatch.STR_NW_SRC + "=" + jp.getText());
                else if (n == "dst-ip")
                    m.append("," + OFMatch.STR_NW_DST + "=" + jp.getText());
                else if (n == "src-port")
                    m.append("," + OFMatch.STR_TP_SRC + "=" + jp.getText());
                else if (n == "dst-port")
                    m.append("," + OFMatch.STR_TP_DST + "=" + jp.getText());
            }
            log.debug("jsonToFlowMod: name: {}, dpid: {}", name, switchDpid);

                // NOTE: UI will not allow name=null, but a direct curl request can be malformed
            if (name == null) {
                log.debug("jsonToFlowMod: Attempt to create a flow-mod entry with no name");
                throw new IOException("No name specified for flow-mod");
            }

            OFMatch match = new OFMatch();
            match.setWildcards(Integer.parseInt(wildcards));
            if (m.length() > 0) {
                match.fromString(m.substring(1));
            }
            
            List<OFAction> actions = new ArrayList<OFAction>();
            int actionsLength = 0;
            if (a != null) {
                for (String t : a.split(",")) {
                    Matcher n = Pattern.compile("output=(?:(\\d+)|(all)|(local)|(ingress-port)|(normal)|(flood))").matcher(t);
                    if (n.matches()) {
                        OFActionOutput action = new OFActionOutput();
                        action.setMaxLength((short) 0);
                        short port = OFPort.OFPP_NONE.getValue();
                        if (n.group(1) != null) {
                            try {
                                port = Short.parseShort(n.group(1));
                            }
                            catch (NumberFormatException e) { }
                        }
                        else if (n.group(2) != null)
                            port = OFPort.OFPP_ALL.getValue();
                        else if (n.group(3) != null)
                            port = OFPort.OFPP_LOCAL.getValue();
                        else if (n.group(4) != null)
                            port = OFPort.OFPP_IN_PORT.getValue();
                        else if (n.group(5) != null)
                            port = OFPort.OFPP_NORMAL.getValue();
                        else if (n.group(6) != null)
                            port = OFPort.OFPP_FLOOD.getValue();
                        action.setPort(port);
                        actions.add(action);
                        actionsLength += OFActionOutput.MINIMUM_LENGTH;
                    }
                }
            }
            
            OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
            fm.setMatch(match);
            fm.setActions(actions);
            fm.setPriority((short)Integer.parseInt(priority));
            fm.setCookie(computeEntryCookie(fm, (int)Integer.parseInt(userCookie), name));  
            fm.setLength(U16.t(OFFlowMod.MINIMUM_LENGTH + actionsLength));
            
            flowmod.dpidStr = switchDpid;
            flowmod.dpid = HexString.toLong(switchDpid);
            flowmod.name = name;
            flowmod.fm = fm;
            flowmod.active = active;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return flowmod;
    }

    /**
     * Utility method to compute Cookie for an OFFlowMod object
     * 
     * @param fm
     * @param userCookie
     * @param name
     * @return long
     */
    protected long computeEntryCookie(OFFlowMod fm, int userCookie, String name) {
        // Placeholder for now, but we should do something like this...
        int STATIC_FLOW_APP_ID = 10;

        int APP_ID_BITS = 12;
        int APP_ID_SHIFT = (64 - APP_ID_BITS);
        int FLOW_HASH_BITS = 20;
        int FLOW_HASH_SHIFT = (64 - APP_ID_BITS - FLOW_HASH_BITS);

        // app section is top 12 bits
        long entryCookie =  0;
        entryCookie |= (long) (STATIC_FLOW_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

        // flow-specific hash is next 20 bits 
        int prime = 211;
        int flowHash = 2311;
        for (int i=0; i < name.length(); i++)
            flowHash = flowHash * prime + (int)name.charAt(i);

        entryCookie |= (long) (flowHash & ((1 << FLOW_HASH_BITS) - 1)) << FLOW_HASH_SHIFT;

        // user-specified part of cookie is bottom 32 bits
        // check how this works with signed/unsigned?
        entryCookie |= userCookie;
        
        return entryCookie;
    }

    public void startUp() {
        floodlightProvider.addOFSwitchListener(this);        
        pushEntriesTimer = new Runnable() {
            @Override
            public void run() {
                log.debug("Pushing static flows");
                pushAllEntries();
                if (pushEntriesTimer == this) {
                    ScheduledExecutorService ses = 
                        floodlightProvider.getScheduledExecutor();
                    ses.schedule(this, 
                                pushEntriesFrequency, TimeUnit.SECONDS);
                }
            }
        };
        floodlightProvider.getScheduledExecutor().schedule(pushEntriesTimer, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Pushes all entries associated with all switches (from the store)
     */
    protected void pushAllEntries() {
        for (IOFSwitch sw : activeSwitches) {
            for (OFFlowMod fm : getEntries(sw).values()) {
                pushEntry(sw, fm);
            }
        }
    }
    
    public void shutDown() {
        log.info("shutdown");
        pushEntriesTimer = null;
        floodlightProvider.removeOFSwitchListener(this);
    }
}
