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

package net.floodlightcontroller.hub;

import java.io.IOException;
import java.util.Collections;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu) - 04/04/10
 */
public class Hub implements IOFMessageListener {
    protected static Logger log = LoggerFactory.getLogger(Hub.class);

    protected IFloodlightProvider floodlightProvider;

    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    public void startUp() {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    public void shutDown() {
        floodlightProvider.removeOFMessageListener(OFType.PACKET_IN, this);
    }

    @Override
    public String getName() {
        return Hub.class.getPackage().getName();
    }
    
    @Override
    public int getId() {
        return FlListenerID.HUB;
    }


    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        OFPacketIn pi = (OFPacketIn) msg;
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                .getMessage(OFType.PACKET_OUT);
        po.setBufferId(pi.getBufferId())
            .setInPort(pi.getInPort());

        // set actions
        OFActionOutput action = new OFActionOutput()
            .setPort((short) OFPort.OFPP_FLOOD.getValue());
        po.setActions(Collections.singletonList((OFAction)action));
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set data if is is included in the packetin
        if (pi.getBufferId() == 0xffffffff) {
            byte[] packetData = pi.getPacketData();
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength() + packetData.length));
            po.setPacketData(packetData);
        } else {
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength()));
        }
        try {
            sw.write(po, cntx);
        } catch (IOException e) {
            log.error("Failure writing PacketOut", e);
        }

        return Command.CONTINUE;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }
}
