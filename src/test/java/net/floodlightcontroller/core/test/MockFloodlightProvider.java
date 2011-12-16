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

package net.floodlightcontroller.core.test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchFilter;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IOFMessageListener.Command;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class MockFloodlightProvider implements IFloodlightProvider {
    protected Map<OFType, List<IOFMessageListener>> listeners;
    protected List<IOFSwitchListener> switchListeners;
    protected Map<Long, IOFSwitch> switches;
    protected BasicFactory factory;
    
    protected ScheduledExecutorService mockExecutor = 
        new MockScheduledExecutor();

    /**
     * 
     */
    public MockFloodlightProvider() {
        listeners = new ConcurrentHashMap<OFType, List<IOFMessageListener>>();
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        switchListeners = new CopyOnWriteArrayList<IOFSwitchListener>();
        factory = new BasicFactory();
    }

    public void addOFMessageListener(OFType type, IOFMessageListener listener) {
        if (!listeners.containsKey(type)) {
            listeners.put(type, new ArrayList<IOFMessageListener>());
        }
        listeners.get(type).add(listener);
    }

    public void removeOFMessageListener(OFType type, IOFMessageListener listener) {
        listeners.get(type).remove(listener);
    }

    /**
     * @return the listeners
     */
    public Map<OFType, List<IOFMessageListener>> getListeners() {
        return listeners;
    }

    /**
     * @param listeners the listeners to set
     */
    public void setListeners(Map<OFType, List<IOFMessageListener>> listeners) {
        this.listeners = listeners;
    }

    public void clearListeners() {
        this.listeners.clear();
    }
    
    @Override
    public Map<Long, IOFSwitch> getSwitches() {
        return this.switches;
    }

    public void setSwitches(Map<Long, IOFSwitch> switches) {
        this.switches = switches;
    }

    @Override
    public void addOFSwitchListener(IOFSwitchListener listener) {
        switchListeners.add(listener);
    }

    @Override
    public void removeOFSwitchListener(IOFSwitchListener listener) {
        switchListeners.remove(listener);
    }

    public void dispatchMessage(IOFSwitch sw, OFMessage msg) {
        dispatchMessage(sw, msg, new FloodlightContext());
    }
    
    public void dispatchMessage(IOFSwitch sw, OFMessage msg, FloodlightContext bc) {
        List<IOFMessageListener> listeners = this.listeners.get(msg.getType());
        if (listeners != null) {
            Command result = Command.CONTINUE;
            Iterator<IOFMessageListener> it = listeners.iterator();
            if (OFType.PACKET_IN.equals(msg.getType())) {
                OFPacketIn pi = (OFPacketIn)msg;
                Ethernet eth = new Ethernet();
                eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
                IFloodlightProvider.bcStore.put(bc, 
                        IFloodlightProvider.CONTEXT_PI_PAYLOAD, 
                        eth);
            }
            while (it.hasNext() && !Command.STOP.equals(result)) {
                result = it.next().receive(sw, msg, bc);
            }
        }
    }
    
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m, FloodlightContext bc) {
        List<IOFMessageListener> msgListeners = null;
        if (listeners.containsKey(m.getType())) {
            msgListeners = listeners.get(m.getType());
        }
            
        if (msgListeners != null) {                
            for (IOFMessageListener listener : msgListeners) {
                if (listener instanceof IOFSwitchFilter) {
                    if (!((IOFSwitchFilter)listener).isInterested(sw)) {
                        continue;
                    }
                }
                if (Command.STOP.equals(listener.receive(sw, m, bc))) {
                    break;
                }
            }
        }
    }
    
    public void handleOutgoingMessages(IOFSwitch sw, List<OFMessage> msglist, FloodlightContext bc) {
        for (OFMessage m:msglist) {
            handleOutgoingMessage(sw, m, bc);
        }
    }

    /**
     * @return the switchListeners
     */
    public List<IOFSwitchListener> getSwitchListeners() {
        return switchListeners;
    }
    
    public void terminate() {
    }

    /**
     * Return a mock executor that will simply execute each task 
     * synchronously once.
     */
    @Override
    public ScheduledExecutorService getScheduledExecutor() {
        return mockExecutor;
    }

    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg) {
        dispatchMessage(sw, msg);
        return true;
    }
    
    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg, FloodlightContext bContext) {        
        dispatchMessage(sw, msg);        
        return true;
    }
    
    @Override
    public BasicFactory getOFMessageFactory() {
        return factory;
    }
}
