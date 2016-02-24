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

package net.floodlightcontroller.devicemanager.test;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.sdnplatform.sync.test.MockSyncService;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.AttachmentPoint;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;

/**
 * Mock device manager useful for unit tests
 * @author readams
 */
public class MockDeviceManager extends DeviceManagerImpl {
	/**
	 * Set a new IEntityClassifier
	 * Use this as a quick way to use a particular entity classifier in a
	 * single test without having to setup the full FloodlightModuleContext
	 * again.
	 * @param ecs
	 */
	public void setEntityClassifier(IEntityClassifierService ecs) {
		this.entityClassifier = ecs;
		try {
			this.startUp(null);
		} catch (FloodlightModuleException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Learn a device using the given characteristics.
	 * @param macAddress the MAC
	 * @param vlan the VLAN (can be VlanVid.ZERO for untagged)
	 * @param ipv4Address the IPv4 (can be IPv4Address.NONE)
	 * @param ipv6Address the IPv6 (can be IPv6Address.NONE)
	 * @param switchDPID the attachment point switch DPID (can be DatapathId.NONE)
	 * @param switchPort the attachment point switch port (can be OFPort.ZERO)
	 * @param processUpdates if false, will not send updates.  Note that this
	 * method is not thread safe if this is false
	 * @return the device, either new or not
	 */
	public IDevice learnEntity(MacAddress macAddress, VlanVid vlan,
			IPv4Address ipv4Address, IPv6Address ipv6Address, DatapathId switchDPID,
			OFPort switchPort,
			boolean processUpdates) {
		List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
		if (!processUpdates) {
			deviceListeners.clearListeners();
		}
		
		/* Entity will enforce all but VLAN be non-null */
		IDevice res =  learnDeviceByEntity(new Entity(macAddress, 
				vlan, ipv4Address, ipv6Address, switchDPID, switchPort, new Date()));
		// Restore listeners
		if (listeners != null) {
			for (IDeviceListener listener : listeners) {
				deviceListeners.addListener("device", listener);
			}
		}
		return res;
	}

	@Override 
	public void deleteDevice(Device device) {
		super.deleteDevice(device);
	}

	/**
	 * Learn a device using the given characteristics.
	 * @param macAddress the MAC
	 * @param vlan the VLAN (can be VlanVid.ZERO for untagged)
	 * @param ipv4Address the IPv4 (can be IPv4Address.NONE)
	 * @param ipv6Address the IPv6 (can be IPv6Address.NONE)
	 * @param switchDPID the attachment point switch DPID (can be DatapathId.NONE)
	 * @param switchPort the attachment point switch port (can be OFPort.ZERO)
	 * @return the device, either new or not
	 */
	public IDevice learnEntity(MacAddress macAddress, VlanVid vlan,
			IPv4Address ipv4Address, IPv6Address ipv6Address, DatapathId switchDPID,
			OFPort switchPort) {
		return learnEntity(macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort, true);
	}

	@Override
	protected Device allocateDevice(Long deviceKey,
			Entity entity,
			IEntityClass entityClass) {
		return new MockDevice(this, deviceKey, entity, entityClass);
	}

	@Override
	protected Device allocateDevice(Long deviceKey,
			String dhcpClientName,
			List<AttachmentPoint> aps,
			List<AttachmentPoint> trueAPs,
			Collection<Entity> entities,
			IEntityClass entityClass) {
		return new MockDevice(this, deviceKey, aps, trueAPs, entities, entityClass);
	}

	@Override
	protected Device allocateDevice(Device device,
			Entity entity,
			int insertionpoint) {
		return new MockDevice(device, entity, insertionpoint);
	}

	@Override
	public void init(FloodlightModuleContext fmc) throws FloodlightModuleException {
		super.init(fmc);
		setSyncServiceIfNotSet(new MockSyncService());
	}
}