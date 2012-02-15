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

/**
 *
 */
package net.floodlightcontroller.devicemanager.internal;

import java.util.Map;
import java.util.Set;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.devicemanager.IDeviceManager;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.topology.ITopologyAware;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeviceManager creates Devices based upon MAC addresses seen in the network.
 * It tracks any network addresses mapped to the Device, and its location
 * within the network.
 * @author readams
 */
public class DeviceManagerImpl implements IDeviceManager, IOFMessageListener,
        IOFSwitchListener, ITopologyAware, IStorageSourceListener {  
    protected static Logger logger = 
        LoggerFactory.getLogger(DeviceManagerImpl.class);

    protected IFloodlightProvider floodlightProvider;
    
    /**
     * This is the master device map that maps device IDs to {@link Device}
     * objects.
     */
    protected Map<Long, Device> deviceMap;
    
    /**
     * This is the primary entity index that maps entities to device IDs.
     */
    protected Map<IndexedEntity, Long> primaryIndex;
    
    /**
     * This map contains secondary indices for each of the configured {@ref IEntityClass} 
     * that exist
     */
    protected Map<IEntityClass, Map<IndexedEntity, Long>> secondaryIndexMap;

    // **************
    // IDeviceManager
    // **************
    
    // None for now
    
    // ******************
    // IOFMessageListener
    // ******************
    
    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getId() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        // TODO Auto-generated method stub
        return null;
    }
    
    // **********************
    // IStorageSourceListener
    // **********************
    
    @Override
    public void rowsModified(String tableName, Set<Object> rowKeys) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void rowsDeleted(String tableName, Set<Object> rowKeys) {
        // TODO Auto-generated method stub
        
    }

    // **************
    // ITopologyAware
    // **************
    
    @Override
    public void addedLink(IOFSwitch srcSw, short srcPort, int srcPortState,
                          IOFSwitch dstSw, short dstPort, int dstPortState) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updatedLink(IOFSwitch srcSw, short srcPort,
                            int srcPortState, IOFSwitch dstSw,
                            short dstPort, int dstPortState) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removedLink(IOFSwitch srcSw, short srcPort, IOFSwitch dstSw,
                            short dstPort) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clusterMerged() {
        // TODO Auto-generated method stub
        
    }
    
    // *****************
    // IOFSwitchListener
    // *****************
    
    @Override
    public void updatedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void addedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removedSwitch(IOFSwitch sw) {
        // TODO Auto-generated method stub
        
    }

    // ****************
    // Internal methods
    // ****************
    
    public Command processPortStatusMessage(IOFSwitch sw, OFPortStatus ps) {
        
    }

}
