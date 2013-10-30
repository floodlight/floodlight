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

package net.floodlightcontroller.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packetstreamer.thrift.*;
import net.floodlightcontroller.threadpool.IThreadPoolService;

@LogMessageCategory("OpenFlow Message Tracing")
public class OFMessageFilterManager 
        implements IOFMessageListener, IFloodlightModule, IOFMessageFilterManagerService {

    /**
     * @author Srini
     */
    protected static Logger log = LoggerFactory.getLogger(OFMessageFilterManager.class);

    // The port and client reference for packet streaming
    protected int serverPort = 9090;
    protected final int MaxRetry = 1;
    protected static volatile TTransport transport = null;
    protected static volatile PacketStreamer.Client packetClient = null;

    protected IFloodlightProviderService floodlightProvider = null;
    protected IThreadPoolService threadPool = null;
    // filter List is a key value pair.  Key is the session id, 
    // value is the filter rules.
    protected ConcurrentHashMap<String, 
                                ConcurrentHashMap<String,
                                                  String>> filterMap = null;
    protected ConcurrentHashMap<String, Long> filterTimeoutMap = null;
    protected Timer timer = null;

    protected int MAX_FILTERS=5;
    protected long MAX_FILTER_TIME= 300000; // maximum filter time is 5 minutes.
    protected int TIMER_INTERVAL = 1000;  // 1 second time interval.

    public static final String SUCCESS                     = "0";
    public static final String FILTER_SETUP_FAILED         = "-1001"; 
    public static final String FILTER_NOT_FOUND            = "-1002";
    public static final String FILTER_LIMIT_REACHED        = "-1003";
    public static final String FILTER_SESSION_ID_NOT_FOUND = "-1004";
    public static final String SERVICE_UNAVAILABLE         = "-1005";

    public enum FilterResult {
        /*
         * FILTER_NOT_DEFINED: Filter is not defined
         * FILTER_NO_MATCH:    Filter is defined and the packet doesn't 
         *                     match the filter
         * FILTER_MATCH:       Filter is defined and the packet matches
         *                     the filter
         */
        FILTER_NOT_DEFINED, FILTER_NO_MATCH, FILTER_MATCH
    }

    protected String addFilter(ConcurrentHashMap<String,String> f, long delta) {

        // Create unique session ID.  
        int prime = 33791;
        String s = null;
        int i;

        if ((filterMap == null) || (filterTimeoutMap == null))
            return String.format("%s", FILTER_SETUP_FAILED);

        for (i=0; i<MAX_FILTERS; ++i) {
            Integer x = prime + i;
            s = String.format("%d", x.hashCode());
            // implies you can use this key for session id.    
            if (!filterMap.containsKey(s)) break; 
        }

        if (i==MAX_FILTERS) {
            return FILTER_LIMIT_REACHED;
        }

        filterMap.put(s, f);
        if (filterTimeoutMap.containsKey(s))  filterTimeoutMap.remove(s);
        filterTimeoutMap.put(s, delta);

        // set the timer as there will be no existing timers. 
        if (filterMap.size() == 1) { 
            TimeoutFilterTask task = new TimeoutFilterTask(this);
            Timer timer = new Timer();
            timer.schedule (task, TIMER_INTERVAL);                
            // Keep the listeners to avoid race condition
            //startListening();
        }   
        return s;  // the return string is the session ID.
    }

    @Override
    public String setupFilter(String sid, 
                              ConcurrentHashMap<String,String> f, 
                              int deltaInMilliSeconds) {

        if (sid == null) {
            // Delta in filter needs to be milliseconds
            log.debug("Adding new filter: {} for {} ms", f, deltaInMilliSeconds);
            return addFilter(f, deltaInMilliSeconds);
        } else {// this is the session id.
            // we will ignore the hash map features.
            if (deltaInMilliSeconds > 0)  
                return refreshFilter(sid, deltaInMilliSeconds);
            else 
                return deleteFilter(sid);
        }
    }

    public int timeoutFilters() {                
        Iterator<String> i = filterTimeoutMap.keySet().iterator();

        while(i.hasNext()) {
            String s = i.next();

            Long t = filterTimeoutMap.get(s);
            if (t != null) {
                i.remove();
                t -= TIMER_INTERVAL;
                if (t > 0) {
                    filterTimeoutMap.put(s, t);
                } else deleteFilter(s);
            } else deleteFilter(s);
        }
        return filterMap.size();
    }

    protected String refreshFilter(String s, int delta) {
        Long t = filterTimeoutMap.get(s);
        if (t != null) {
            filterTimeoutMap.remove(s);
            t += delta;  // time is in milliseconds
            if (t > MAX_FILTER_TIME) t = MAX_FILTER_TIME;
            filterTimeoutMap.put(s, t);
            return SUCCESS;
        } else return FILTER_SESSION_ID_NOT_FOUND;
    }

    @LogMessageDoc(level="ERROR",
                   message="Error while terminating packet " +
                           "filter session",
                   explanation="An unknown error occurred while terminating " +
                   		"a packet filter session.",
                   recommendation=LogMessageDoc.GENERIC_ACTION)
    protected String deleteFilter(String sessionId) {

        if (filterMap.containsKey(sessionId)) {
            filterMap.remove(sessionId);
            try {
                if (packetClient != null)
                    packetClient.terminateSession(sessionId);
            } catch (TException e) {
                log.error("Error while terminating packet " +
                		  "filter session", e);
            }
            log.debug("Deleted Filter {}.  # of filters" +
            		 " remaining: {}", sessionId, filterMap.size());
            return SUCCESS;
        } else return FILTER_SESSION_ID_NOT_FOUND;
    }

    public HashSet<String> getMatchedFilters(OFMessage m, FloodlightContext cntx) {  

        HashSet<String> matchedFilters = new HashSet<String>();

        // This default function is written to match on packet ins and 
        // packet outs.
        Ethernet eth = null;

        if (m.getType() == OFType.PACKET_IN) {
            eth = IFloodlightProviderService.bcStore.get(cntx, 
                    IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        } else if (m.getType() == OFType.PACKET_OUT) {
            eth = new Ethernet();
            OFPacketOut p = (OFPacketOut) m;
            
            // No MAC match if packetOut doesn't have the packet.
            if (p.getPacketData() == null) return null;
            
            eth.deserialize(p.getPacketData(), 0, p.getPacketData().length);
        } else if (m.getType() == OFType.FLOW_MOD) {
            // flow-mod can't be matched by mac.
            return null;
        }

        if (eth == null) return null;

        Iterator<String> filterIt = filterMap.keySet().iterator();
        while (filterIt.hasNext()) {   // for every filter
            boolean filterMatch = false;
            String filterSessionId = filterIt.next();
            Map<String,String> filter = filterMap.get(filterSessionId);

            // If the filter has empty fields, then it is not considered as a match.
            if (filter == null || filter.isEmpty()) continue;                  
            Iterator<String> fieldIt = filter.keySet().iterator();
            while (fieldIt.hasNext()) {   
                String filterFieldType = fieldIt.next();
                String filterFieldValue = filter.get(filterFieldType);
                if (filterFieldType.equals("mac")) {

                    String srcMac = HexString.toHexString(eth.getSourceMACAddress());
                    String dstMac = HexString.toHexString(eth.getDestinationMACAddress());
                    log.debug("srcMac: {}, dstMac: {}", srcMac, dstMac);

                    if (filterFieldValue.equals(srcMac) || 
                            filterFieldValue.equals(dstMac)){
                        filterMatch = true; 
                    } else {
                        filterMatch = false;
                        break;
                    }
                }
            }
            if (filterMatch) {
                matchedFilters.add(filterSessionId);
            }
        }

        if (matchedFilters.isEmpty())
            return null;    
        else 
            return matchedFilters;
    }
    
    @LogMessageDoc(level="ERROR",
                   message="Failed to establish connection with the " +
                           "packetstreamer server.",
                   explanation="The message tracing server is not running " +
                   		"or otherwise unavailable.",
                   recommendation=LogMessageDoc.CHECK_CONTROLLER)
    public boolean connectToPSServer() {
        int numRetries = 0;
        if (transport != null && transport.isOpen()) {
            return true;
        }

        while (numRetries++ < MaxRetry) {
            try {
                TFramedTransport t = 
                        new TFramedTransport(new TSocket("localhost", 
                                                         serverPort));
                t.open();

                TProtocol protocol = new  TBinaryProtocol(t);
                packetClient = new PacketStreamer.Client(protocol);

                log.debug("Have a connection to packetstreamer server " +
                		  "localhost:{}", serverPort);
                transport = t;
                break;
            } catch (TException x) {
                try {
                    // Wait for 1 second before retry
                    if (numRetries < MaxRetry) {
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {}
            } 
        }

        if (numRetries > MaxRetry) {
            log.error("Failed to establish connection with the " +
            		  "packetstreamer server.");
            return false;
        }
        return true;
    }

    public void disconnectFromPSServer() {
        if (transport != null && transport.isOpen()) {
            log.debug("Close the connection to packetstreamer server" +
            		  " localhost:{}", serverPort);
            transport.close();
        }
    }

    @Override
    public String getName() {
        return "messageFilterManager";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("devicemanager"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("learningswitch"));
    }

    @Override
    @LogMessageDoc(level="ERROR",
                   message="Error while sending packet",
                   explanation="Failed to send a message to the message " +
                   		"tracing server",
                   recommendation=LogMessageDoc.CHECK_CONTROLLER)
    public Command receive(IOFSwitch sw, OFMessage msg, 
                           FloodlightContext cntx) {

        if (filterMap == null || filterMap.isEmpty()) return Command.CONTINUE;

        HashSet<String> matchedFilters = null;
        if (log.isDebugEnabled()) {
            log.debug("Received packet {} from switch {}", 
                      msg, sw.getStringId());
        }

        matchedFilters = getMatchedFilters(msg, cntx);
        if (matchedFilters == null) {
            return Command.CONTINUE;
        } else {
            try {
                sendPacket(matchedFilters, sw, msg, cntx, true);
            } catch (Exception e) {
                log.error("Error while sending packet", e);
            }
        }
        
        return Command.CONTINUE;
    }


    public class TimeoutFilterTask extends TimerTask {

        OFMessageFilterManager filterManager;
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();

        public TimeoutFilterTask(OFMessageFilterManager manager) {
            filterManager = manager;
        }

        public void run() {
            int x = filterManager.timeoutFilters();

            if (x > 0) {  // there's at least one filter still active.
                Timer timer = new Timer();
                timer.schedule(new TimeoutFilterTask(filterManager), 
                               TIMER_INTERVAL);
            } else {
                // Don't stop the listener to avoid race condition
                //stopListening();
            }
        }
    }

    public int getNumberOfFilters() {
        return filterMap.size();
    }

    public int getMaxFilterSize() {
        return MAX_FILTERS;
    }

    protected void sendPacket(HashSet<String> matchedFilters, IOFSwitch sw, 
            OFMessage msg, FloodlightContext cntx, boolean sync) 
                    throws TException {
        Message sendMsg = new Message();
        Packet packet = new Packet();
        ChannelBuffer bb;
        sendMsg.setPacket(packet);

        List<String> sids = new ArrayList<String>(matchedFilters);

        sendMsg.setSessionIDs(sids);
        packet.setMessageType(OFMessageType.findByValue((msg.getType().ordinal())));

        switch (msg.getType()) {
            case PACKET_IN:
                OFPacketIn pktIn = (OFPacketIn)msg;
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), 
                                                          pktIn.getInPort()));
                bb = ChannelBuffers.buffer(pktIn.getLength());
                pktIn.writeTo(bb);
                packet.setData(OFMessage.getData(sw, msg, cntx));
                break;
            case PACKET_OUT:
                OFPacketOut pktOut = (OFPacketOut)msg;
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), 
                                                          pktOut.getInPort()));
                bb = ChannelBuffers.buffer(pktOut.getLength());
                pktOut.writeTo(bb);
                packet.setData(OFMessage.getData(sw, msg, cntx));
                break;
            case FLOW_MOD:
                OFFlowMod offlowMod = (OFFlowMod)msg;
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), 
                                                          offlowMod.
                                                          getOutPort()));
                bb = ChannelBuffers.buffer(offlowMod.getLength());
                offlowMod.writeTo(bb);
                packet.setData(OFMessage.getData(sw, msg, cntx));
                break;
            default:
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), 
                                                          (short)0));
                String strData = "Unknown packet";
                packet.setData(strData.getBytes());
                break;
        }

        try {
            if (transport == null || 
                !transport.isOpen() || 
                packetClient == null) {
                if (!connectToPSServer()) {
                    // No need to sendPacket if can't make connection to 
                    // the server
                    return;
                }
            }
            if (sync) {
                log.debug("Send packet sync: {}", packet.toString());
                packetClient.pushMessageSync(sendMsg);
            } else {
                log.debug("Send packet sync: ", packet.toString());
                packetClient.pushMessageAsync(sendMsg);
            }
        } catch (Exception e) {
            log.error("Error while sending packet", e);
            disconnectFromPSServer();
            connectToPSServer();
        }
    }

    // IFloodlightModule methods
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IOFMessageFilterManagerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(IOFMessageFilterManagerService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        this.floodlightProvider = 
                context.getServiceImpl(IFloodlightProviderService.class);
        this.threadPool =
                context.getServiceImpl(IThreadPoolService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // This is our 'constructor'
        
        filterMap = new ConcurrentHashMap<String, ConcurrentHashMap<String,String>>();
        filterTimeoutMap = new ConcurrentHashMap<String, Long>();
        serverPort = 
                Integer.parseInt(System.getProperty("net.floodlightcontroller." +
                		"packetstreamer.port", "9090"));
        
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_OUT, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_MOD, this);
    }
}
