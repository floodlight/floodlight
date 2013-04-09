/**
*    Copyright 2013, YAMAMOTO Takashi
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

/*
 * a module merely for a benchmark.
 * intended to be comparable with cbench RyuApp.
 * https://github.com/osrg/ryu/blob/master/ryu/app/cbench.py
 */

package net.floodlightcontroller.cbench;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.routing.ForwardingBase;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CBench implements IFloodlightModule, IOFMessageListener {
    private final short APP_ID = 999; // XXX

    protected static Logger log = LoggerFactory.getLogger(CBench.class);

    protected IFloodlightProviderService floodlightProvider;

    // IOFMessageListener

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
            FloodlightContext cntx) {
        assert msg instanceof OFPacketIn;
        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory()
                .getMessage(OFType.FLOW_MOD);
        OFMatch match = new OFMatch();
        List<OFAction> actions = new ArrayList<OFAction>();
        long cookie = AppCookie.makeCookie(APP_ID, 0);

        fm.setCookie(cookie)
        .setIdleTimeout(ForwardingBase.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
        .setHardTimeout(ForwardingBase.FLOWMOD_DEFAULT_HARD_TIMEOUT)
        .setBufferId(OFPacketOut.BUFFER_ID_NONE)
        .setMatch(match)
        .setActions(actions)
        .setLengthU(OFFlowMod.MINIMUM_LENGTH);
        fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
        try {
            sw.write(fm, cntx);
        } catch (IOException e) {
            log.error("Failure writing FlowMod", e);
        }
        return Command.CONTINUE;
    }

    // IListener

    @Override
    public String getName() {
        return CBench.class.getPackage().getName();
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
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
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
