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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchFilter;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class MockFloodlightProvider implements IFloodlightModule, IFloodlightProviderService {
    protected Map<OFType, List<IOFMessageListener>> listeners;
    protected List<IOFSwitchListener> switchListeners;
    protected List<IHAListener> haListeners;
    protected Map<Long, IOFSwitch> switches;
    protected BasicFactory factory;

    /**
     * 
     */
    public MockFloodlightProvider() {
        listeners = new ConcurrentHashMap<OFType, List<IOFMessageListener>>();
        switches = new ConcurrentHashMap<Long, IOFSwitch>();
        switchListeners = new CopyOnWriteArrayList<IOFSwitchListener>();
        haListeners = new CopyOnWriteArrayList<IHAListener>();
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
                IFloodlightProviderService.bcStore.put(bc, 
                        IFloodlightProviderService.CONTEXT_PI_PAYLOAD, 
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

    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg) {
        dispatchMessage(sw, msg);
        return true;
    }
    
    @Override
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg, 
                                   FloodlightContext bContext) {        
        dispatchMessage(sw, msg, bContext);     
        return true;
    }
    
    @Override
    public BasicFactory getOFMessageFactory() {
        return factory;
    }

    @Override
    public void run() {
        // no-op
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public
            void
            init(FloodlightModuleContext context)
                                                 throws FloodlightModuleException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // TODO Auto-generated method stub
        
    }

	@Override
	public void addInfoProvider(String type, IInfoProvider provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeInfoProvider(String type, IInfoProvider provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Map<String, Object> getControllerInfo(String type) {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public void addHAListener(IHAListener listener) {
        haListeners.add(listener);
    }

    @Override
    public void removeHAListener(IHAListener listener) {
        haListeners.remove(listener);
    }
    
    @Override
    public Role getRole() {
        return null;
    }
    
    @Override
    public void setRole(Role role) {
        
    }
    
    /**
     * Dispatches a new role change notification
     * @param oldRole
     * @param newRole
     */
    public void dispatchRoleChanged(Role oldRole, Role newRole) {
        for (IHAListener rl : haListeners) {
            rl.roleChanged(oldRole, newRole);
        }
    }

    @Override
    public String getControllerId() {
        return "localhost";
    }

	@Override
	public Map<String, String> getControllerNodeIPs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getSystemUptime() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
}
