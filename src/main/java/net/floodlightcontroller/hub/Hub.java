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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.awt.OrientableFlowLayout;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu) - 04/04/10
 */
public class Hub implements IFloodlightModule, IOFMessageListener {
    protected static Logger log = LoggerFactory.getLogger(Hub.class);

    protected IFloodlightProviderService floodlightProvider;

    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }

    @Override
    public String getName() {
        return Hub.class.getPackage().getName();
    }

    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        //OFMessage outMessage = createHubPacketOut(sw, msg);
        OFMessage outMessage = createHubFlowMod(sw, msg);
        sw.write(outMessage);

        return Command.CONTINUE;
    }
    
    private OFMessage createHubFlowMod(IOFSwitch sw, OFMessage msg) {
    	OFPacketIn pi = (OFPacketIn) msg;
        OFFlowAdd.Builder fmb = /*sw.getOFFactory()*/OFFactories.getFactory(OFVersion.OF_13).buildFlowAdd();
        //Match.Builder mb = OFFactories.getFactory(OFVersion.OF_13).buildMatch();
        
        fmb.setBufferId(pi.getBufferId())
        .setXid(pi.getXid())
        /*.setMatch(pi.getMatch())*/;

        // set actions
        OFActionOutput.Builder actionBuilder = /*sw.getOFFactory()*/OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput();
        actionBuilder.setPort(OFPort.ALL);
        fmb.setActions(Collections.singletonList((OFAction) actionBuilder.build()));
        fmb.setOutPort(OFPort.ALL);

        return fmb.build();
    }
    
    private OFMessage createHubPacketOut(IOFSwitch sw, OFMessage msg) {
    	OFPacketIn pi = (OFPacketIn) msg;
        OFPacketOut.Builder pob = /*sw.getOFFactory()*/OFFactories.getFactory(OFVersion.OF_13).buildPacketOut();
        pob.setBufferId(pi.getBufferId()).setXid(pi.getXid()).setInPort(pi.getMatch().get(MatchField.IN_PORT));

        // set actions
        OFActionOutput.Builder actionBuilder = /*sw.getOFFactory()*/OFFactories.getFactory(OFVersion.OF_13).actions().buildOutput();
        actionBuilder.setPort(OFPort.FLOOD);
        pob.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

        // set data if is is included in the packetin
        if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
            byte[] packetData = pi.getData();
            pob.setData(packetData);
        }
        return pob.build();  	
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't provide any services, return null
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // We don't provide any services, return null
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider =
                context.getServiceImpl(IFloodlightProviderService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
}
