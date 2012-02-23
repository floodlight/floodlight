/**
*    Copyright 2011,2012, Big Switch Networks, Inc. 
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

package net.floodlightcontroller.devicemanager;

import java.util.Collection;

import net.floodlightcontroller.core.FloodlightContextStore;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Device manager allows interacting with devices on the network.  Note
 * that under normal circumstances, {@link Device} objects should be retrieved
 * from the {@link FloodlightContext} rather than from {@link IDeviceManager}.
 */
public interface IDeviceManagerService extends IFloodlightService {
    /**
     * The source device for the current packet-in, if applicable.
     */
    public static final String CONTEXT_SRC_DEVICE = 
            "com.bigswitch.floodlight.devicemanager.srcDevice"; 

    /**
     * The destination device for the current packet-in, if applicable.
     */
    public static final String CONTEXT_DST_DEVICE = 
            "com.bigswitch.floodlight.devicemanager.dstDevice"; 

    /**
     * A FloodlightContextStore object that can be used to interact with the 
     * FloodlightContext information created by BVS manager.
     */
    public static final FloodlightContextStore<IDevice> fcStore = 
        new FloodlightContextStore<IDevice>();

    /**
     * Set the entity classifier for the device manager to use to
     * differentiate devices on the network.  If no classifier is set,
     * the {@link DefaultEntityClassifer} will be used.  This should be 
     * registered in the application initialization phase before startup.
     * @param classifier the classifier to set.
     */
    public void setEntityClassifier(IEntityClassifier classifier);

    /**
     * Flush and/or reclassify all entities in a class
     *
     * @param entityClass the class to flush.  If null, flush all classes
     * @param reclassify if true, begin an asynchronous task to reclassify the
     * flushed entities
     */
    public void flushEntityCache(IEntityClass entityClass, boolean reclassify);

    /**
     * Search for a device using entity fields.  Only the key fields as
     * defined by the {@link IEntityClassifier} will be important in this
     * search.
     * @param macAddress The MAC address
     * @param vlan the VLAN
     * @param ipv4Address the ipv4 address
     * @param switchDPID the switch DPID
     * @param switchPort the switch port
     * @return an {@link IDevice} or null if no device is found.
     * @see IDeviceManager#setEntityClassifier(IEntityClassifier)
     */
    public IDevice findDevice(long macAddress, Short vlan,
                              Integer ipv4Address, Long switchDPID,
                              Integer switchPort);
    
    /**
     * Get a destination device using entity fields that corresponds with
     * the given source device.  The source device is important since
     * there could be ambiguity in the destination device without the
     * attachment point information.
     * @param source the source device.  The returned destination will be
     * in the same entity class as the source.
     * @param macAddress The MAC address for the destination
     * @param vlan the VLAN if available
     * @param ipv4Address The IP address if available.
     * @return an {@link IDevice} or null if no device is found.
     * @see IDeviceManagerService#findDevice(long, Short, Integer, Long, 
     * Integer)
     */
    public IDevice findDestDevice(IDevice source,
                                  long macAddress, Short vlan,
                                  Integer ipv4Address);

    /**
     * Get an unmodifiable collection view over all devices currently known.
     * @return the collection of all devices
     */
    public Collection<? extends IDevice> getAllDevices();

    /**
     * Adds a listener to listen for IDeviceManagerServices notifications
     * @param listener The listener that wants the notifications
     */
    public void addListener(IDeviceManagerAware listener);
}
