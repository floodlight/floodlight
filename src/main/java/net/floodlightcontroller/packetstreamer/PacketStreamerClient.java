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

package net.floodlightcontroller.packetstreamer;

import net.floodlightcontroller.packetstreamer.thrift.*;

import java.util.List;
import java.util.ArrayList;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PacketStreamer Sample Client.
 */
public class PacketStreamerClient {
    protected static Logger log = LoggerFactory.getLogger(PacketStreamerClient.class);

    /** 
     * Main function entry point;
     * @param args
     */
    public static void main(String [] args) {
        try {
            int serverPort = Integer.parseInt(System.getProperty("net.floodlightcontroller.packetstreamer.port", "9090"));
            TTransport transport;
            transport = new TFramedTransport(new TSocket("localhost", serverPort));
            transport.open();
  

            TProtocol protocol = new  TBinaryProtocol(transport);
            PacketStreamer.Client client = new PacketStreamer.Client(protocol);

            sendPackets(client, (short)2, OFMessageType.PACKET_IN, true);
            log.debug("Terminate session1");
            client.terminateSession("session1");

            transport.close();
        } catch (TException x) {
            x.printStackTrace();
        } 
    }

    /** 
     * Send test packets of the given OFMessageType to the packetstreamer server;
     * @param client Packetstreamer client object
     * @param numPackets number of test packets to be sent
     * @param ofType OFMessageType of the test packets
     * @param sync true if send with synchronous interface, false for asynchronous interface
     * @throws TException
     */
    private static void sendPackets(PacketStreamer.Client client, short numPackets, OFMessageType ofType, boolean sync) 
    throws TException {
        while (numPackets-- > 0) {
            Message msg = new Message();
            Packet packet = new Packet();
    
            List<String> sids = new ArrayList<String>();
            sids.add("session1");
            sids.add("session2");
            msg.setSessionIDs(sids);
            packet.setMessageType(ofType);
            long sw_dpid = numPackets/40 + 1;
            packet.setSwPortTuple(new SwitchPortTuple(sw_dpid, (short)(numPackets - (sw_dpid-1)*40)));
    
            String strData = "New data, sequence " + numPackets;
            packet.setData(strData.getBytes());
            msg.setPacket(packet);

            try {
                if (sync) {
                      client.pushMessageSync(msg);
                      log.debug("Send packet sync: " + msg.toString());
                } else {
                      client.pushMessageAsync(msg);
                      log.debug("Send packet sync: " + msg.toString());
                }
            } catch (TTransportException e) {
                log.error(e.toString());
            }
            
            try {
                Thread.sleep(100);
            } catch (Exception e) {}
        }
    }
}
