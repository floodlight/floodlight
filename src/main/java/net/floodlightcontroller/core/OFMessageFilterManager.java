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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.BPDU;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLC;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packetstreamer.thrift.*;

public class OFMessageFilterManager implements IOFMessageListener {

    /**
     * @author Srini
     */
    protected static Logger log = LoggerFactory.getLogger(OFMessageFilterManager.class);

    // The port and client reference for packet streaming
    protected int serverPort = 9090;
    protected final int MaxRetry = 1;
    protected static TTransport transport = null;
    protected static PacketStreamer.Client packetClient = null;

    protected IFloodlightProvider floodlightProvider = null;
    // filter List is a key value pair.  Key is the session id, value is the filter rules.
    protected ConcurrentHashMap<String, ConcurrentHashMap<String,String>> filterMap = null;
    protected ConcurrentHashMap<String, Long> filterTimeoutMap = null;
    protected Timer timer = null;

    protected final int MAX_FILTERS=5;
    protected final long MAX_FILTER_TIME= 300000;  // maximum filter time is 5 minutes.
    protected final int TIMER_INTERVAL = 1000;  // 1 second time interval.

    public static final String SUCCESS                     = "0";
    public static final String FILTER_SETUP_FAILED         = "-1001"; 
    public static final String FILTER_NOT_FOUND            = "-1002";
    public static final String FILTER_LIMIT_REACHED        = "-1003";
    public static final String FILTER_SESSION_ID_NOT_FOUND = "-1004";
    public static final String SERVICE_UNAVAILABLE         = "-1005";

    public enum FilterResult {
        /*
         * FILTER_NOT_DEFINED: Filter is not defined
         * FILTER_NO_MATCH:    Filter is defined and the packet doesn't match the filter
         * FILTER_MATCH:       Filter is defined and the packet matches the filter
         */
        FILTER_NOT_DEFINED, FILTER_NO_MATCH, FILTER_MATCH
    }

    public void init (IFloodlightProvider bp) {
        floodlightProvider = bp;
        filterMap = new ConcurrentHashMap<String, ConcurrentHashMap<String,String>>();
        filterTimeoutMap = new ConcurrentHashMap<String, Long>();
        serverPort = Integer.parseInt(System.getProperty("net.floodlightcontroller.packetstreamer.port", "9090"));
    }

    protected String addFilter(ConcurrentHashMap<String,String> f, long delta) {

        // Create unique session ID.  
        int prime = 33791;
        String s = null;
        int i;

        if ((filterMap == null) || (filterTimeoutMap == null))
            return  String.format("%d", FILTER_SETUP_FAILED);

        for (i=0; i<MAX_FILTERS; ++i) {
            Integer x = prime + i;
            s = String.format("%d", x.hashCode());
            if (!filterMap.containsKey(s)) break;  // implies you can use this key for session id.
        }

        if (i==MAX_FILTERS) {
            return FILTER_LIMIT_REACHED;
        }

        filterMap.put(s, f);
        if (filterTimeoutMap.containsKey(s))  filterTimeoutMap.remove(s);
        filterTimeoutMap.put(s, delta);

        if (filterMap.size() == 1) { // set the timer as there will be no existing timers. 
            TimeoutFilterTask task = new TimeoutFilterTask(this);
            Timer timer = new Timer();
            timer.schedule (task, TIMER_INTERVAL);                
            // Keep the listeners to avoid race condition
            //startListening();
        }   
        return s;  // the return string is the session ID.
    }

    public String setupFilter(String sid, ConcurrentHashMap<String,String> f, int deltaInSecond) {

        if (sid == null) {
            // Delta in filter needs to be milliseconds
            log.debug("Adding new filter: {} for {} seconds", f, deltaInSecond);
            return addFilter(f, deltaInSecond * 1000);
        } else {// this is the session id.
            // we will ignore the hash map features.
            if (deltaInSecond > 0)  
                return refreshFilter(sid, deltaInSecond * 1000);
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

    protected String deleteFilter(String sessionId) {

        if (filterMap.containsKey(sessionId)) {
            filterMap.remove(sessionId);
            try {
                if (packetClient != null)
                    packetClient.terminateSession(sessionId);
            } catch (TException e) {
                log.error("terminateSession Texception: {}", e);
            }
            log.debug("Deleted Filter {}.  # of filters remaining: {}", sessionId, filterMap.size());
            return SUCCESS;
        } else return FILTER_SESSION_ID_NOT_FOUND;
    }

    public HashSet<String> getMatchedFilters(OFMessage m, FloodlightContext cntx) {  

        HashSet<String> matchedFilters = new HashSet<String>();

        // This default function is written to match on packet ins and 
        // packet outs.
        Ethernet eth = null;

        if (m.getType() == OFType.PACKET_IN) {
            eth = IFloodlightProvider.bcStore.get(cntx, 
                    IFloodlightProvider.CONTEXT_PI_PAYLOAD);


        } else if (m.getType() == OFType.PACKET_OUT) {
            eth = new Ethernet();
            OFPacketOut p = (OFPacketOut) m;
            eth.deserialize(p.getPacketData(), 0, p.getPacketData().length);

        } else if (m.getType() == OFType.FLOW_MOD) {
            // no action performed.
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
                if (filterFieldType == "mac") {

                    String srcMac = HexString.toHexString(eth.getSourceMACAddress());
                    String dstMac = HexString.toHexString(eth.getDestinationMACAddress());

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


    protected void startListening() {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_OUT, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_MOD, this);
    }

    protected void stopListening() {
        floodlightProvider.removeOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.removeOFMessageListener(OFType.PACKET_OUT, this);
        floodlightProvider.removeOFMessageListener(OFType.FLOW_MOD, this);
    }

    public void startUp() {
        startListening();
        //connectToPSServer();
    }

    public void shutDown() {
        stopListening();
        disconnectFromPSServer();
    }

    public boolean connectToPSServer() {
        int numRetries = 0;
        if (transport != null && transport.isOpen()) {
            return true;
        }

        while (numRetries++ < MaxRetry) {
            try {
                transport = new TFramedTransport(new TSocket("localhost", serverPort));
                transport.open();

                TProtocol protocol = new  TBinaryProtocol(transport);
                packetClient = new PacketStreamer.Client(protocol);

                log.debug("Have a connection to packetstreamer server localhost:{}", serverPort);
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
            log.error("Failed to establish connection with the packetstreamer server.");
            return false;
        }
        return true;
    }

    public void disconnectFromPSServer() {
        if (transport != null && transport.isOpen()) {
            log.debug("Close the connection to packetstreamer server localhost:{}", serverPort);
            transport.close();
        }
    }

    @Override
    public String getName() {
        return "messageFilterManager";
    }
    
    @Override
    public int getId() {
        return FlListenerID.OFMESSAGEFILTERMANAGER;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return (type == OFType.PACKET_IN && name.equals("devicemanager"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

        if (filterMap == null || filterMap.isEmpty()) return Command.CONTINUE;

        HashSet<String> matchedFilters = null;
        if (log.isDebugEnabled()) {
            log.debug("Received packet {} from switch {}", msg, sw.getStringId());
        }

        matchedFilters = getMatchedFilters(msg, cntx);
        if (matchedFilters == null) {
            return Command.CONTINUE;
        } else {
            try {
                sendPacket(matchedFilters, sw, msg, cntx, true);
            } catch (TException e) {
                log.error("sendPacket Texception: {}", e);
            } catch (Exception e) {
                log.error("sendPacket exception: {}", e);
            }
        }

        return Command.CONTINUE;
    }


    public class TimeoutFilterTask extends TimerTask {

        OFMessageFilterManager filterManager;
        ScheduledExecutorService ses = floodlightProvider.getScheduledExecutor();

        public TimeoutFilterTask(OFMessageFilterManager manager) {
            filterManager = manager;
        }

        public void run() {
            int x = filterManager.timeoutFilters();

            if (x > 0) {  // there's at least one filter still active.
                Timer timer = new Timer();
                timer.schedule(new TimeoutFilterTask(filterManager), TIMER_INTERVAL);
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
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), pktIn.getInPort()));
                bb = ChannelBuffers.buffer(pktIn.getLength());
                log.debug("Packet-In length is: {}", pktIn.getLength());
                pktIn.writeTo(bb);
                packet.setData(getData(sw, msg, cntx));
                break;
            case PACKET_OUT:
                OFPacketOut pktOut = (OFPacketOut)msg;
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), pktOut.getInPort()));
                bb = ChannelBuffers.buffer(pktOut.getLength());
                pktOut.writeTo(bb);
                packet.setData(getData(sw, msg, cntx));
                break;
            case FLOW_MOD:
                OFFlowMod offlowMod = (OFFlowMod)msg;
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), offlowMod.getOutPort()));
                bb = ChannelBuffers.buffer(offlowMod.getLength());
                offlowMod.writeTo(bb);
                packet.setData(getData(sw, msg, cntx));
                break;
            default:
                packet.setSwPortTuple(new SwitchPortTuple(sw.getId(), (short)0));
                String strData = "Unknown packet";
                packet.setData(strData.getBytes());
                break;
        }

        try {
            if (transport == null || !transport.isOpen() || packetClient == null) {
                if (!connectToPSServer()) {
                    // No need to sendPacket if can't make connection to the server
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
        } catch (TTransportException e) {
            log.info("Caught TTransportException: {}", e);
            System.out.println(e);
            disconnectFromPSServer();
            connectToPSServer();
        } catch (Exception e) {
            log.info("Caught exception: {}", e);
            System.out.println(e);
            disconnectFromPSServer();
            connectToPSServer();
        }
    }

    private byte[] getData(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

        Ethernet eth;
        StringBuffer sb =  new StringBuffer("");

        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        Date date = new Date();

        sb.append(dateFormat.format(date));
        sb.append("      ");

        switch (msg.getType()) {
            case PACKET_IN:
                OFPacketIn pktIn = (OFPacketIn) msg;
                sb.append("packet_in          [ ");
                sb.append(HexString.toHexString(sw.getId()));
                sb.append(" -> Controller");
                sb.append(" ]");

                sb.append("\ntotal length: ");
                sb.append(pktIn.getTotalLength());
                sb.append("\nin_port: ");
                sb.append(pktIn.getInPort());
                sb.append("\ndata_length: ");
                sb.append(pktIn.getTotalLength() - OFPacketIn.MINIMUM_LENGTH);
                sb.append("\nbuffer: ");
                sb.append(pktIn.getBufferId());

                // packet type  icmp, arp, etc.
                eth = IFloodlightProvider.bcStore.get(cntx,
                        IFloodlightProvider.CONTEXT_PI_PAYLOAD);

                sb.append(getStringFromEthernetPacket(eth));

                break;

            case PACKET_OUT:
                OFPacketOut pktOut = (OFPacketOut) msg;
                sb.append("packet_out         [ ");
                sb.append("Controller -> ");
                sb.append(HexString.toHexString(sw.getId()));
                sb.append(" ]");

                sb.append("\nin_port: ");
                sb.append(pktOut.getInPort());
                sb.append("\nactions_len: ");
                sb.append(pktOut.getActionsLength());
                sb.append("\nactions: ");
                sb.append(pktOut.getActions().toString());
                break;

            case FLOW_MOD:
                OFFlowMod fm = (OFFlowMod) msg;
                sb.append("flow_mod           [ ");
                sb.append("Controller -> ");
                sb.append(HexString.toHexString(sw.getId()));
                sb.append(" ]");

                eth = new Ethernet();

                eth = IFloodlightProvider.bcStore.get(cntx,
                        IFloodlightProvider.CONTEXT_PI_PAYLOAD);
                sb.append(getStringFromEthernetPacket(eth));

                sb.append("ADD: cookie: ");
                sb.append(fm.getCookie());
                sb.append(" idle: ");
                sb.append(fm.getIdleTimeout());
                sb.append(" hard: ");
                sb.append(fm.getHardTimeout());
                sb.append(" pri: ");
                sb.append(fm.getPriority());
                sb.append(" buf: ");
                sb.append(fm.getBufferId());
                sb.append(" flg: ");
                sb.append(fm.getFlags());
                sb.append("\nactions: ");
                sb.append(fm.getActions().toString());
                break;

            default:
                sb.append("[Unknown Packet]");
        }

        sb.append("\n\n");
        return sb.toString().getBytes();
    }

    private String getStringFromEthernetPacket(Ethernet eth) {

        StringBuffer sb = new StringBuffer("\n");

        IPacket pkt = (IPacket) eth.getPayload();

        if (pkt instanceof ARP)
            sb.append("arp");
        else if (pkt instanceof LLDP)
            sb.append("lldp");
        else if (pkt instanceof ICMP)
            sb.append("icmp");
        else if (pkt instanceof IPv4)
            sb.append("ip");
        else if (pkt instanceof DHCP)
            sb.append("dhcp");
        else  sb.append(eth.getEtherType());

        sb.append("\ndl_vlan: ");
        if (eth.getVlanID() == Ethernet.VLAN_UNTAGGED)
            sb.append("untagged");
        else
            sb.append(eth.getVlanID());
        sb.append("\ndl_vlan_pcp: ");
        sb.append(eth.getPriorityCode());
        sb.append("\ndl_src: ");
        sb.append(HexString.toHexString(eth.getSourceMACAddress()));
        sb.append("\ndl_dst: ");
        sb.append(HexString.toHexString(eth.getDestinationMACAddress()));


        if (pkt instanceof ARP) {
            ARP p = (ARP) pkt;
            sb.append("\nnw_src: ");
            sb.append(IPv4.fromIPv4Address(IPv4.toIPv4Address(p.getSenderProtocolAddress())));
            sb.append("\nnw_dst: ");
            sb.append(IPv4.fromIPv4Address(IPv4.toIPv4Address(p.getTargetProtocolAddress())));
        }
        else if (pkt instanceof LLDP) {
            sb.append("lldp packet");
        }
        else if (pkt instanceof ICMP) {
            ICMP icmp = (ICMP) pkt;
            sb.append("\nicmp_type: ");
            sb.append(icmp.getIcmpType());
            sb.append("\nicmp_code: ");
            sb.append(icmp.getIcmpCode());
        }
        else if (pkt instanceof IPv4) {
            IPv4 p = (IPv4) pkt;
            sb.append("\nnw_src: ");
            sb.append(IPv4.fromIPv4Address(p.getSourceAddress()));
            sb.append("\nnw_dst: ");
            sb.append(IPv4.fromIPv4Address(p.getDestinationAddress()));
            sb.append("\nnw_tos: ");
            sb.append(p.getDiffServ());
            sb.append("\nnw_proto: ");
            sb.append(p.getProtocol());

            if (pkt instanceof TCP) {
                sb.append("\ntp_src: ");
                sb.append(((TCP) pkt).getSourcePort());
                sb.append("\ntp_dst: ");
                sb.append(((TCP) pkt).getDestinationPort());

            } else if (pkt instanceof UDP) {
                sb.append("\ntp_src: ");
                sb.append(((UDP) pkt).getSourcePort());
                sb.append("\ntp_dst: ");
                sb.append(((UDP) pkt).getDestinationPort());
            }

            if (pkt instanceof ICMP) {
                ICMP icmp = (ICMP) pkt;
                sb.append("\nicmp_type: ");
                sb.append(icmp.getIcmpType());
                sb.append("\nicmp_code: ");
                sb.append(icmp.getIcmpCode());
            }

        }
        else if (pkt instanceof DHCP) {
            sb.append("\ndhcp packet");
        }
        else if (pkt instanceof Data) {
            sb.append("\ndata packet");
        }
        else if (pkt instanceof LLC) {
            sb.append("\nllc packet");
        }
        else if (pkt instanceof BPDU) {
            sb.append("\nbpdu packet");
        }
        else sb.append("\nunknwon packet");

        return sb.toString();
    }

}
