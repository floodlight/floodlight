/**
 *    Copyright 2012, Big Switch Networks, Inc.
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.internal.IOFSwitchManager;
import net.floodlightcontroller.core.internal.TableFeatures;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.core.util.URIUtil;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnection;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionState;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionsReply;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFlowWildcards;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortReason;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

import net.floodlightcontroller.util.LinkedHashSetWrapper;
import net.floodlightcontroller.util.OrderedCollection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This is the internal representation of an openflow switch.
 */
public class OFSwitch implements IOFSwitchBackend {
	protected static final Logger log =
			LoggerFactory.getLogger(OFSwitch.class);

	protected final ConcurrentMap<Object, Object> attributes;
	protected final IOFSwitchManager switchManager;

	/* Switch features from initial featuresReply */
	protected Set<OFCapabilities> capabilities;
	protected long buffers;
	protected Set<OFActionType> actions;
	protected short tables;
	protected final DatapathId datapathId;

	private Map<TableId, TableFeatures> tableFeaturesByTableId;

	private boolean startDriverHandshakeCalled = false;
	private final Map<OFAuxId, IOFConnectionBackend> connections;
	private volatile Map<URI, Map<OFAuxId, OFBsnControllerConnection>> controllerConnections;
	protected OFFactory factory;

	/**
	 * Members hidden from subclasses
	 */
	private final PortManager portManager;

	private volatile boolean connected;

	private volatile OFControllerRole role;

	private boolean flowTableFull = false;

	protected SwitchDescription description;

	private SwitchStatus status;

	public static final int OFSWITCH_APP_ID = ident(5);
	
	private TableId maxTableToGetTableMissFlow = TableId.of(4); /* this should cover most HW switches that have a couple SW flow tables */

	static {
		AppCookie.registerApp(OFSwitch.OFSWITCH_APP_ID, "switch");
	}

	public OFSwitch(IOFConnectionBackend connection, @Nonnull OFFactory factory, @Nonnull IOFSwitchManager switchManager,
			@Nonnull DatapathId datapathId) {
		if(connection == null)
			throw new NullPointerException("connection must not be null");
		if(!connection.getAuxId().equals(OFAuxId.MAIN))
			throw new IllegalArgumentException("connection must be the main connection");
		if(factory == null)
			throw new NullPointerException("factory must not be null");
		if(switchManager == null)
			throw new NullPointerException("switchManager must not be null");

		this.connected = true;
		this.factory = factory;
		this.switchManager = switchManager;
		this.datapathId = datapathId;
		this.attributes = new ConcurrentHashMap<Object, Object>();
		this.role = null;
		this.description = new SwitchDescription();
		this.portManager = new PortManager();
		this.status = SwitchStatus.HANDSHAKE;

		// Connections
		this.connections = new ConcurrentHashMap<OFAuxId, IOFConnectionBackend>();
		this.connections.put(connection.getAuxId(), connection);

		// Switch's controller connection
		this.controllerConnections = ImmutableMap.of();

		// Defaults properties for an ideal switch
		this.setAttribute(PROP_FASTWILDCARDS, EnumSet.allOf(OFFlowWildcards.class));
		this.setAttribute(PROP_SUPPORTS_OFPP_FLOOD, Boolean.TRUE);
		this.setAttribute(PROP_SUPPORTS_OFPP_TABLE, Boolean.TRUE);

		this.tableFeaturesByTableId = new HashMap<TableId, TableFeatures>();
	}

	private static int ident(int i) {
		return i;
	}

	@Override
	public OFFactory getOFFactory() {
		return factory;
	}

	/**
	 * Manages the ports of this switch.
	 *
	 * Provides methods to query and update the stored ports. The class ensures
	 * that every port name and port number is unique. When updating ports
	 * the class checks if port number <-> port name mappings have change due
	 * to the update. If a new port P has number and port that are inconsistent
	 * with the previous mapping(s) the class will delete all previous ports
	 * with name or number of the new port and then add the new port.
	 *
	 * Port names are stored as-is but they are compared case-insensitive
	 *
	 * The methods that change the stored ports return a list of
	 * PortChangeEvents that represent the changes that have been applied
	 * to the port list so that IOFSwitchListeners can be notified about the
	 * changes.
	 *
	 * Implementation notes:
	 * - We keep several different representations of the ports to allow for
	 *   fast lookups
	 * - Ports are stored in unchangeable lists. When a port is modified new
	 *   data structures are allocated.
	 * - We use a read-write-lock for synchronization, so multiple readers are
	 *   allowed.
	 */
	protected static class PortManager {
		private final ReentrantReadWriteLock lock;
		private List<OFPortDesc> portList;
		private List<OFPortDesc> enabledPortList;
		private List<OFPort> enabledPortNumbers;
		private Map<OFPort,OFPortDesc> portsByNumber;
		private Map<String,OFPortDesc> portsByName;

		public PortManager() {
			this.lock = new ReentrantReadWriteLock();
			this.portList = Collections.emptyList();
			this.enabledPortList = Collections.emptyList();
			this.enabledPortNumbers = Collections.emptyList();
			this.portsByName = Collections.emptyMap();
			this.portsByNumber = Collections.emptyMap();
		}

		/**
		 * Set the internal data structure storing this switch's port
		 * to the ports specified by newPortsByNumber
		 *
		 * CALLER MUST HOLD WRITELOCK
		 *
		 * @param newPortsByNumber
		 * @throws IllegaalStateException if called without holding the
		 * writelock
		 */
		private void updatePortsWithNewPortsByNumber(
				Map<OFPort,OFPortDesc> newPortsByNumber) {
			if (!lock.writeLock().isHeldByCurrentThread()) {
				throw new IllegalStateException("Method called without " +
						"holding writeLock");
			}
			Map<String,OFPortDesc> newPortsByName =
					new HashMap<String, OFPortDesc>();
			List<OFPortDesc> newPortList =
					new ArrayList<OFPortDesc>();
			List<OFPortDesc> newEnabledPortList =
					new ArrayList<OFPortDesc>();
			List<OFPort> newEnabledPortNumbers = new ArrayList<OFPort>();

			for(OFPortDesc p: newPortsByNumber.values()) {
				newPortList.add(p);
				newPortsByName.put(p.getName().toLowerCase(), p);
				if (!p.getState().contains(OFPortState.LINK_DOWN) 
						&& !p.getConfig().contains(OFPortConfig.PORT_DOWN)) {
					newEnabledPortList.add(p);
					newEnabledPortNumbers.add(p.getPortNo());
				}
			}
			portsByName = Collections.unmodifiableMap(newPortsByName);
			portsByNumber =
					Collections.unmodifiableMap(newPortsByNumber);
			enabledPortList =
					Collections.unmodifiableList(newEnabledPortList);
			enabledPortNumbers =
					Collections.unmodifiableList(newEnabledPortNumbers);
			portList = Collections.unmodifiableList(newPortList);
		}

		/**
		 * Handle a OFPortStatus delete message for the given port.
		 * Updates the internal port maps/lists of this switch and returns
		 * the PortChangeEvents caused by the delete. If the given port
		 * exists as it, it will be deleted. If the name<->number for the
		 * given port is inconsistent with the ports stored by this switch
		 * the method will delete all ports with the number or name of the
		 * given port.
		 *
		 * This method will increment error/warn counters and log
		 *
		 * @param delPort the port from the port status message that should
		 * be deleted.
		 * @return ordered collection of port changes applied to this switch
		 */
		private OrderedCollection<PortChangeEvent>
		handlePortStatusDelete(OFPortDesc delPort) {
			OrderedCollection<PortChangeEvent> events =
					new LinkedHashSetWrapper<PortChangeEvent>();
			lock.writeLock().lock();
			try {
				Map<OFPort,OFPortDesc> newPortByNumber =
						new HashMap<OFPort, OFPortDesc>(portsByNumber);
				OFPortDesc prevPort =
						portsByNumber.get(delPort.getPortNo());
				if (prevPort == null) {
					// so such port. Do we have a port with the name?
					prevPort = portsByName.get(delPort.getName());
					if (prevPort != null) {
						newPortByNumber.remove(prevPort.getPortNo());
						events.add(new PortChangeEvent(prevPort,
								PortChangeType.DELETE));
					}
				} else if (prevPort.getName().equals(delPort.getName())) {
					// port exists with consistent name-number mapping
					newPortByNumber.remove(delPort.getPortNo());
					events.add(new PortChangeEvent(delPort,
							PortChangeType.DELETE));
				} else {
					// port with same number exists but its name differs. This
					// is weird. The best we can do is to delete the existing
					// port(s) that have delPort's name and number.
					newPortByNumber.remove(delPort.getPortNo());
					events.add(new PortChangeEvent(prevPort,
							PortChangeType.DELETE));
					// is there another port that has delPort's name?
					prevPort = portsByName.get(delPort.getName().toLowerCase());
					if (prevPort != null) {
						newPortByNumber.remove(prevPort.getPortNo());
						events.add(new PortChangeEvent(prevPort,
								PortChangeType.DELETE));
					}
				}
				updatePortsWithNewPortsByNumber(newPortByNumber);
				return events;
			} finally {
				lock.writeLock().unlock();
			}
		}

		/**
		 * Handle a OFPortStatus message, update the internal data structures
		 * that store ports and return the list of OFChangeEvents.
		 *
		 * This method will increment error/warn counters and log
		 *
		 * @param ps
		 * @return
		 */
		@SuppressFBWarnings(value="SF_SWITCH_FALLTHROUGH")
		public OrderedCollection<PortChangeEvent> handlePortStatusMessage(OFPortStatus ps) {
			if (ps == null) {
				throw new NullPointerException("OFPortStatus message must " +
						"not be null");
			}
			lock.writeLock().lock();
			try {
				OFPortDesc port = ps.getDesc();
				OFPortReason reason = ps.getReason();
				if (reason == null) {
					throw new IllegalArgumentException("Unknown PortStatus " +
							"reason code " + ps.getReason());
				}

				if (log.isDebugEnabled()) {
					log.debug("Handling OFPortStatus: {} for {}",
							reason, String.format("%s (%d)", port.getName(), port.getPortNo().getPortNumber()));
				}

				if (reason == OFPortReason.DELETE)
					return handlePortStatusDelete(port);

				// We handle ADD and MODIFY the same way. Since OpenFlow
				// doesn't specify what uniquely identifies a port the
				// notion of ADD vs. MODIFY can also be hazy. So we just
				// compare the new port to the existing ones.
				Map<OFPort,OFPortDesc> newPortByNumber =
						new HashMap<OFPort, OFPortDesc>(portsByNumber);
				OrderedCollection<PortChangeEvent> events = getSinglePortChanges(port);
				for (PortChangeEvent e: events) {
					switch(e.type) {
					case DELETE:
						newPortByNumber.remove(e.port.getPortNo());
						break;
					case ADD:
						if (reason != OFPortReason.ADD) {
							// weird case
						}
						// fall through
					case DOWN:
					case OTHER_UPDATE:
					case UP:
						// update or add the port in the map
						newPortByNumber.put(e.port.getPortNo(), e.port);
						break;
					}
				}
				updatePortsWithNewPortsByNumber(newPortByNumber);
				return events;
			} finally {
				lock.writeLock().unlock();
			}

		}

		/**
		 * Given a new or modified port newPort, returns the list of
		 * PortChangeEvents to "transform" the current ports stored by
		 * this switch to include / represent the new port. The ports stored
		 * by this switch are <b>NOT</b> updated.
		 *
		 * This method acquires the readlock and is thread-safe by itself.
		 * Most callers will need to acquire the write lock before calling
		 * this method though (if the caller wants to update the ports stored
		 * by this switch)
		 *
		 * @param newPort the new or modified port.
		 * @return the list of changes
		 */
		public OrderedCollection<PortChangeEvent>
		getSinglePortChanges(OFPortDesc newPort) {
			lock.readLock().lock();
			try {
				OrderedCollection<PortChangeEvent> events =
						new LinkedHashSetWrapper<PortChangeEvent>();
				// Check if we have a port by the same number in our
				// old map.
				OFPortDesc prevPort =
						portsByNumber.get(newPort.getPortNo());
				if (newPort.equals(prevPort)) {
					// nothing has changed
					return events;
				}

				if (prevPort != null &&
						prevPort.getName().equals(newPort.getName())) {
					// A simple modify of a exiting port
					// A previous port with this number exists and it's name
					// also matches the new port. Find the differences
					if ((!prevPort.getState().contains(OFPortState.LINK_DOWN) 
							&& !prevPort.getConfig().contains(OFPortConfig.PORT_DOWN)) 
							&& (newPort.getState().contains(OFPortState.LINK_DOWN) 
									|| newPort.getConfig().contains(OFPortConfig.PORT_DOWN))) {
						events.add(new PortChangeEvent(newPort,
								PortChangeType.DOWN));
					} else if ((prevPort.getState().contains(OFPortState.LINK_DOWN) 
							|| prevPort.getConfig().contains(OFPortConfig.PORT_DOWN)) 
							&& (!newPort.getState().contains(OFPortState.LINK_DOWN) 
									&& !newPort.getConfig().contains(OFPortConfig.PORT_DOWN))) {
						events.add(new PortChangeEvent(newPort,
								PortChangeType.UP));
					} else {
						events.add(new PortChangeEvent(newPort,
								PortChangeType.OTHER_UPDATE));
					}
					return events;
				}

				if (prevPort != null) {
					// There exists a previous port with the same port
					// number but the port name is different (otherwise we would
					// never have gotten here)
					// Remove the port. Name-number mapping(s) have changed
					events.add(new PortChangeEvent(prevPort,
							PortChangeType.DELETE));
				}

				// We now need to check if there exists a previous port sharing
				// the same name as the new/updated port.
				prevPort = portsByName.get(newPort.getName().toLowerCase());
				if (prevPort != null) {
					// There exists a previous port with the same port
					// name but the port number is different (otherwise we
					// never have gotten here).
					// Remove the port. Name-number mapping(s) have changed
					events.add(new PortChangeEvent(prevPort,
							PortChangeType.DELETE));
				}

				// We always need to add the new port. Either no previous port
				// existed or we just deleted previous ports with inconsistent
				// name-number mappings
				events.add(new PortChangeEvent(newPort, PortChangeType.ADD));
				return events;
			} finally {
				lock.readLock().unlock();
			}
		}

		/**
		 * Compare the current ports of this switch to the newPorts list and
		 * return the changes that would be applied to transform the current
		 * ports to the new ports. No internal data structures are updated
		 * see {@link #compareAndUpdatePorts(List, boolean)}
		 *
		 * @param newPorts the list of new ports
		 * @return The list of differences between the current ports and
		 * newPortList
		 */
		public OrderedCollection<PortChangeEvent>
		comparePorts(Collection<OFPortDesc> newPorts) {
			return compareAndUpdatePorts(newPorts, false);
		}

		/**
		 * Compare the current ports of this switch to the newPorts list and
		 * return the changes that would be applied to transform the current
		 * ports to the new ports. No internal data structures are updated
		 * see {@link #compareAndUpdatePorts(List, boolean)}
		 *
		 * @param newPorts the list of new ports
		 * @return The list of differences between the current ports and
		 * newPortList
		 */
		public OrderedCollection<PortChangeEvent>
		updatePorts(Collection<OFPortDesc> newPorts) {
			return compareAndUpdatePorts(newPorts, true);
		}

		/**
		 * Compare the current ports stored in this switch instance with the
		 * new port list given and return the differences in the form of
		 * PortChangeEvents. If the doUpdate flag is true, newPortList will
		 * replace the current list of this switch (and update the port maps)
		 *
		 * Implementation note:
		 * Since this method can optionally modify the current ports and
		 * since it's not possible to upgrade a read-lock to a write-lock
		 * we need to hold the write-lock for the entire operation. If this
		 * becomes a problem and if compares() are common we can consider
		 * splitting in two methods but this requires lots of code duplication
		 *
		 * @param newPorts the list of new ports.
		 * @param doUpdate If true the newPortList will replace the current
		 * port list for this switch. If false this switch will not be changed.
		 * @return The list of differences between the current ports and
		 * newPorts
		 * @throws NullPointerException if newPortsList is null
		 * @throws IllegalArgumentException if either port names or port numbers
		 * are duplicated in the newPortsList.
		 */
		private OrderedCollection<PortChangeEvent> compareAndUpdatePorts(
				Collection<OFPortDesc> newPorts,
				boolean doUpdate) {
			if (newPorts == null) {
				throw new NullPointerException("newPortsList must not be null");
			}
			lock.writeLock().lock();
			try {
				OrderedCollection<PortChangeEvent> events =
						new LinkedHashSetWrapper<PortChangeEvent>();

				Map<OFPort,OFPortDesc> newPortsByNumber =
						new HashMap<OFPort, OFPortDesc>();
				Map<String,OFPortDesc> newPortsByName =
						new HashMap<String, OFPortDesc>();
				List<OFPortDesc> newEnabledPortList =
						new ArrayList<OFPortDesc>();
				List<OFPort> newEnabledPortNumbers =
						new ArrayList<OFPort>();
				List<OFPortDesc> newPortsList =
						new ArrayList<OFPortDesc>(newPorts);

				for (OFPortDesc p: newPortsList) {
					if (p == null) {
						throw new NullPointerException("portList must not " +
								"contain null values");
					}

					// Add the port to the new maps and lists and check
					// that every port is unique
					OFPortDesc duplicatePort;
					duplicatePort = newPortsByNumber.put(p.getPortNo(), p);
					if (duplicatePort != null) {
						String msg = String.format("Cannot have two ports " +
								"with the same number: %s <-> %s",
								String.format("%s (%d)", p.getName(), p.getPortNo().getPortNumber()),
								String.format("%s (%d)", duplicatePort.getName(), duplicatePort.getPortNo().getPortNumber()));
						throw new IllegalArgumentException(msg);
					}
					duplicatePort =
							newPortsByName.put(p.getName().toLowerCase(), p);
					if (duplicatePort != null) {
						String msg = String.format("Cannot have two ports " +
								"with the same name: %s <-> %s",
								String.format("%s (%d)", p.getName(), p.getPortNo().getPortNumber()),
								String.format("%s (%d)", duplicatePort.getName(), duplicatePort.getPortNo().getPortNumber()));
						throw new IllegalArgumentException(msg);
					}
					// Enabled = not down admin (config) or phys (state)
					if (!p.getConfig().contains(OFPortConfig.PORT_DOWN)
							&& !p.getState().contains(OFPortState.LINK_DOWN)) {
						newEnabledPortList.add(p);
						newEnabledPortNumbers.add(p.getPortNo());
					}

					// get changes
					events.addAll(getSinglePortChanges(p));
				}
				// find deleted ports
				// We need to do this after looping through all the new ports
				// to we can handle changed name<->number mappings correctly
				// We could pull it into the loop of we address this but
				// it's probably not worth it
				for (OFPortDesc oldPort: this.portList) {
					if (!newPortsByNumber.containsKey(oldPort.getPortNo())) {
						PortChangeEvent ev =
								new PortChangeEvent(oldPort,
										PortChangeType.DELETE);
						events.add(ev);
					}
				}


				if (doUpdate) {
					portsByName = Collections.unmodifiableMap(newPortsByName);
					portsByNumber =
							Collections.unmodifiableMap(newPortsByNumber);
					enabledPortList =
							Collections.unmodifiableList(newEnabledPortList);
					enabledPortNumbers =
							Collections.unmodifiableList(newEnabledPortNumbers);
					portList = Collections.unmodifiableList(newPortsList);
				}
				return events;
			} finally {
				lock.writeLock().unlock();
			}
		}

		public OFPortDesc getPort(String name) {
			if (name == null) {
				throw new NullPointerException("Port name must not be null");
			}
			lock.readLock().lock();
			try {
				return portsByName.get(name.toLowerCase());
			} finally {
				lock.readLock().unlock();
			}
		}

		public OFPortDesc getPort(OFPort portNumber) {
			lock.readLock().lock();
			try {
				return portsByNumber.get(portNumber);
			} finally {
				lock.readLock().unlock();
			}
		}

		public List<OFPortDesc> getPorts() {
			lock.readLock().lock();
			try {
				return portList;
			} finally {
				lock.readLock().unlock();
			}
		}

		public List<OFPortDesc> getEnabledPorts() {
			lock.readLock().lock();
			try {
				return enabledPortList;
			} finally {
				lock.readLock().unlock();
			}
		}

		public List<OFPort> getEnabledPortNumbers() {
			lock.readLock().lock();
			try {
				return enabledPortNumbers;
			} finally {
				lock.readLock().unlock();
			}
		}
	}

	@Override
	public boolean attributeEquals(String name, Object other) {
		Object attr = this.attributes.get(name);
		if (attr == null)
			return false;
		return attr.equals(other);
	}

	@Override
	public Object getAttribute(String name) {
		// returns null if key doesn't exist
		return this.attributes.get(name);
	}

	@Override
	public void setAttribute(String name, Object value) {
		this.attributes.put(name, value);
		return;
	}

	@Override
	public Object removeAttribute(String name) {
		return this.attributes.remove(name);
	}

	@Override
	public boolean hasAttribute(String name) {
		return this.attributes.containsKey(name);
	}

	@Override
	public void registerConnection(IOFConnectionBackend connection) {
		this.connections.put(connection.getAuxId(), connection);
	}


	@Override
	public ImmutableList<IOFConnection> getConnections() {
		return ImmutableList.<IOFConnection> copyOf(this.connections.values());
	}

	@Override
	public void removeConnections() {
		this.connections.clear();
	}

	@Override
	public void removeConnection(IOFConnectionBackend connection) {
		this.connections.remove(connection.getAuxId());
	}

	@Override
	public void write(OFMessage m) {
		log.trace("Channel: {}, Connected: {}", connections.get(OFAuxId.MAIN).getRemoteInetAddress(), connections.get(OFAuxId.MAIN).isConnected());
		if (isActive()) {
			connections.get(OFAuxId.MAIN).write(m);
			switchManager.handleOutgoingMessage(this, m);
		} else {
			log.warn("Attempted to write to switch {} that is SLAVE.", this.getId().toString());
		}
	}

	/**
	 * Gets a connection specified by aux Id.
	 * @param auxId the specified aux id for the connection desired.
	 * @return the aux connection specified by the auxId
	 */
	public IOFConnection getConnection(OFAuxId auxId) {
		IOFConnection connection = this.connections.get(auxId);
		if(connection == null){
			throw new IllegalArgumentException("OF Connection for " + this + " with " + auxId + " does not exist.");
		}
		return connection;
	}

	public IOFConnection getConnection(LogicalOFMessageCategory category) {
		if(switchManager.isCategoryRegistered(category)){
			return getConnection(category.getAuxId());
		}
		else{
			throw new IllegalArgumentException(category + " is not registered with the floodlight provider service.");
		}
	}

	@Override
	public void write(OFMessage m, LogicalOFMessageCategory category) {
		if (isActive()) {
			this.getConnection(category).write(m);
			switchManager.handleOutgoingMessage(this, m);
		} else {
			log.warn("Attempted to write to switch {} that is SLAVE.", this.getId().toString());
		}
	}

	@Override
	public void write(Iterable<OFMessage> msglist, LogicalOFMessageCategory category) {
		if (isActive()) {
			this.getConnection(category).write(msglist);
			
			for(OFMessage m : msglist) {
				switchManager.handleOutgoingMessage(this, m);				
			}
		} else {
			log.warn("Attempted to write to switch {} that is SLAVE.", this.getId().toString());
		}
	}

	@Override
	public OFConnection getConnectionByCategory(LogicalOFMessageCategory category){
		return (OFConnection) this.getConnection(category);
	}

	@Override
	public <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request, LogicalOFMessageCategory category) {
		return getConnection(category).writeRequest(request);
	}

	@Override
	public <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request) {
		return connections.get(OFAuxId.MAIN).writeRequest(request);
	}

	@Override
	@LogMessageDoc(level="WARN",
	message="Sending OF message that modifies switch " +
			"state while in the slave role: {switch}",
			explanation="An application has sent a message to a switch " +
					"that is not valid when the switch is in a slave role",
					recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	public void write(Iterable<OFMessage> msglist) {
		if (isActive()) {
			connections.get(OFAuxId.MAIN).write(msglist);
						
			for(OFMessage m : msglist) {
				switchManager.handleOutgoingMessage(this, m);
			}
		} else {
			log.warn("Attempted to write to switch {} that is SLAVE.", this.getId().toString());
		}
	}

	@Override
	public void disconnect() {

		// Iterate through connections and perform cleanup
		for(Entry<OFAuxId, IOFConnectionBackend> entry : this.connections.entrySet()){
			entry.getValue().disconnect();
			this.connections.remove(entry.getKey());
		}
		log.debug("~~~~~~~SWITCH DISCONNECTED~~~~~~");
		// Remove all counters from the store
		connected = false;
	}

	@Override
	public void setFeaturesReply(OFFeaturesReply featuresReply) {
		if (portManager.getPorts().isEmpty() && featuresReply.getVersion().compareTo(OFVersion.OF_13) < 0) {
			/* ports are updated via port status message, so we
			 * only fill in ports on initial connection.
			 */
			List<OFPortDesc> OFPortDescs = featuresReply.getPorts();
			portManager.updatePorts(OFPortDescs);
		}
		this.capabilities = featuresReply.getCapabilities();
		this.buffers = featuresReply.getNBuffers();

		if (featuresReply.getVersion().compareTo(OFVersion.OF_13) < 0 ) {
			/* OF1.3+ Per-table actions are set later in the OFTableFeaturesRequest/Reply */
			this.actions = featuresReply.getActions();
		}
		this.tables = featuresReply.getNTables();
	}

	@Override
	public void setPortDescStats(OFPortDescStatsReply reply) {
		/* ports are updated via port status message, so we
		 * only fill in ports on initial connection.
		 */
		List<OFPortDesc> OFPortDescs = reply.getEntries();
		portManager.updatePorts(OFPortDescs);
	}

	@Override
	public Collection<OFPortDesc> getEnabledPorts() {
		return portManager.getEnabledPorts();
	}

	@Override
	public Collection<OFPort> getEnabledPortNumbers() {
		return portManager.getEnabledPortNumbers();
	}

	@Override
	public OFPortDesc getPort(OFPort portNumber) {
		return portManager.getPort(portNumber);
	}

	@Override
	public OFPortDesc getPort(String portName) {
		return portManager.getPort(portName);
	}

	@Override
	public OrderedCollection<PortChangeEvent>
	processOFPortStatus(OFPortStatus ps) {
		return portManager.handlePortStatusMessage(ps);
	}

	@Override
	public void processOFTableFeatures(List<OFTableFeaturesStatsReply> replies) {
		/*
		 * Parse out all the individual replies for each table.
		 */
		for (OFTableFeaturesStatsReply reply : replies) {
			/*
			 * Add or update the features for a particular table.
			 */
			List<OFTableFeatures> tfs = reply.getEntries();
			for (OFTableFeatures tf : tfs) {
				tableFeaturesByTableId.put(tf.getTableId(), TableFeatures.of(tf));
				log.trace("Received TableFeatures for TableId {}, TableName {}", tf.getTableId().toString(), tf.getName());
			}
		}
	}

	@Override
	public Collection<OFPortDesc> getSortedPorts() {
		List<OFPortDesc> sortedPorts =
				new ArrayList<OFPortDesc>(portManager.getPorts());
		Collections.sort(sortedPorts, new Comparator<OFPortDesc>() {
			@Override
			public int compare(OFPortDesc o1, OFPortDesc o2) {
				String name1 = o1.getName();
				String name2 = o2.getName();
				return name1.compareToIgnoreCase(name2);
			}
		});
		return sortedPorts;
	}

	@Override
	public Collection<OFPortDesc> getPorts() {
		return portManager.getPorts();
	}

	@Override
	public OrderedCollection<PortChangeEvent>
	comparePorts(Collection<OFPortDesc> ports) {
		return portManager.comparePorts(ports);
	}

	@Override
	public OrderedCollection<PortChangeEvent>
	setPorts(Collection<OFPortDesc> ports) {
		return portManager.updatePorts(ports);
	}

	@Override
	public boolean portEnabled(OFPort portNumber) {
		OFPortDesc p = portManager.getPort(portNumber);
		if (p == null) return false;
		return (!p.getState().contains(OFPortState.BLOCKED) && !p.getState().contains(OFPortState.LINK_DOWN) && !p.getState().contains(OFPortState.STP_BLOCK));
	}

	@Override
	public boolean portEnabled(String portName) {
		OFPortDesc p = portManager.getPort(portName);
		if (p == null) return false;
		return (!p.getState().contains(OFPortState.BLOCKED) && !p.getState().contains(OFPortState.LINK_DOWN) && !p.getState().contains(OFPortState.STP_BLOCK));
	}

	@Override
	public DatapathId getId() {
		if (datapathId == null)
			throw new RuntimeException("Features reply has not yet been set");
		return datapathId;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "OFSwitchBase DPID[" + ((datapathId != null) ? datapathId.toString() : "?") + "]";
	}

	@Override
	public ConcurrentMap<Object, Object> getAttributes() {
		return this.attributes;
	}

	@Override
	public Date getConnectedSince() {
		return this.connections.get(OFAuxId.MAIN).getConnectedSince();
	}

	@Override
	public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(OFStatsRequest<REPLY> request) {
		return addInternalStatsReplyListener(connections.get(OFAuxId.MAIN).writeStatsRequest(request), request);
	}

	@Override
	public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(OFStatsRequest<REPLY> request, LogicalOFMessageCategory category) {
		return addInternalStatsReplyListener(getConnection(category).writeStatsRequest(request), request);
	}	

	/**
	 * Append a listener to receive an OFStatsReply and update the 
	 * internal OFSwitch data structures.
	 * 
	 * This presently taps into the following stats request 
	 * messages to listen for the corresponding reply:
	 * -- OFTableFeaturesStatsRequest
	 * 
	 * Extend this to tap into and update other OFStatsType messages.
	 * 
	 * @param future
	 * @param request
	 * @return
	 */
	private <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> addInternalStatsReplyListener(final ListenableFuture<List<REPLY>> future, OFStatsRequest<REPLY> request) {
		switch (request.getStatsType()) {
		case TABLE_FEATURES:
		/* case YOUR_CASE_HERE */
			future.addListener(new Runnable() {
				/*
				 * We know the reply will be a list of OFStatsReply.
				 */
				@SuppressWarnings("unchecked")
				@Override
				public void run() {
					/*
					 * The OFConnection handles REPLY_MORE for us in the case there
					 * are multiple OFStatsReply messages with the same XID.
					 */
					try {
						List<? extends OFStatsReply> replies = future.get();
						if (!replies.isEmpty()) {
							/*
							 * By checking only the 0th element, we assume all others are the same type.
							 * TODO If not, what then?
							 */
							switch (replies.get(0).getStatsType()) {
							case TABLE_FEATURES:
								processOFTableFeatures((List<OFTableFeaturesStatsReply>) future.get());
								break;
							/* case YOUR_CASE_HERE */
							default:
								throw new Exception("Received an invalid OFStatsReply of " 
										+ replies.get(0).getStatsType().toString() + ". Expected TABLE_FEATURES.");
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}, MoreExecutors.sameThreadExecutor()); /* No need for another thread. */
		default:
			break;
		}
		return future; /* either unmodified or with an additional listener */
	}

	@Override
	public void cancelAllPendingRequests() {
		for(Entry<OFAuxId, IOFConnectionBackend> entry : this.connections.entrySet()){
			entry.getValue().cancelAllPendingRequests();
		}
	}

	// If any connections are down consider a switch disconnected
	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public boolean isActive() {
		// no lock needed since we use volatile
		return isConnected() && (this.role == OFControllerRole.ROLE_MASTER || this.role == OFControllerRole.ROLE_EQUAL);
	}

	@Override
	public OFControllerRole getControllerRole() {
		return role;
	}

	@Override
	public void setControllerRole(OFControllerRole role) {
		this.role = role;
	}

	@Override
	public void flush() {
		for(Entry<OFAuxId, IOFConnectionBackend> entry : this.connections.entrySet()){
			entry.getValue().flush();
		}
	}

	/**
	 * Get the IP Address for the switch
	 * @return the inet address
	 */
	@Override
	public SocketAddress getInetAddress() {
		return connections.get(OFAuxId.MAIN).getRemoteInetAddress();
	}

	@Override
	public long getBuffers() {
		return buffers;
	}


	@Override
	public Set<OFActionType> getActions() {
		return actions;
	}


	@Override
	public Set<OFCapabilities> getCapabilities() {
		return capabilities;
	}


	@Override
	public short getTables() {
		return tables;
	}

	@Override
	public SwitchDescription getSwitchDescription() {
		return description;
	}

	@Override
	@LogMessageDoc(level="WARN",
	message="Switch {switch} flow table is full",
	explanation="The controller received flow table full " +
			"message from the switch, could be caused by increased " +
			"traffic pattern",
			recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
	public void setTableFull(boolean isFull) {
		if (isFull && !flowTableFull) {
			switchManager.addSwitchEvent(this.datapathId,
					"SWITCH_FLOW_TABLE_FULL " +
							"Table full error from switch", false);
			log.warn("Switch {} flow table is full", datapathId.toString());
		}
		flowTableFull = isFull;
	}

	@Override
	public void startDriverHandshake() {
		if (startDriverHandshakeCalled)
			throw new SwitchDriverSubHandshakeAlreadyStarted();
		startDriverHandshakeCalled = true;
	}

	@Override
	public boolean isDriverHandshakeComplete() {
		if (!startDriverHandshakeCalled)
			throw new SwitchDriverSubHandshakeNotStarted();
		return true;
	}

	@Override
	public void processDriverHandshakeMessage(OFMessage m) {
		if (startDriverHandshakeCalled)
			throw new SwitchDriverSubHandshakeCompleted(m);
		else
			throw new SwitchDriverSubHandshakeNotStarted();
	}

	@Override
	public void setSwitchProperties(SwitchDescription description) {
		this.description = description;
	}


	@Override
	public SwitchStatus getStatus() {
		return status;
	}

	@Override
	public void setStatus(SwitchStatus switchStatus) {
		this.status = switchStatus;
	}

	@Override
	public void updateControllerConnections(OFBsnControllerConnectionsReply controllerCxnsReply) {

		// Instantiate clean map, can't use a builder here since we need to call temp.get()
		Map<URI,Map<OFAuxId, OFBsnControllerConnection>> temp = new ConcurrentHashMap<URI,Map<OFAuxId, OFBsnControllerConnection>>();

		List<OFBsnControllerConnection> controllerCxnUpdates = controllerCxnsReply.getConnections();
		for(OFBsnControllerConnection update : controllerCxnUpdates) {
			URI uri = URI.create(update.getUri());

			Map<OFAuxId, OFBsnControllerConnection> cxns = temp.get(uri);

			// Add to nested map
			if(cxns != null){
				cxns.put(update.getAuxiliaryId(), update);
			} else{
				cxns = new ConcurrentHashMap<OFAuxId, OFBsnControllerConnection>();
				cxns.put(update.getAuxiliaryId(), update);
				temp.put(uri, cxns);
			}
		}

		this.controllerConnections = ImmutableMap.<URI,Map<OFAuxId, OFBsnControllerConnection>>copyOf(temp);
	}

	@Override
	public boolean hasAnotherMaster() {

		//TODO: refactor get connection to not throw illegal arg exceptions
		IOFConnection mainCxn = this.getConnection(OFAuxId.MAIN);

		if(mainCxn != null) {

			// Determine the local URI
			InetSocketAddress address = (InetSocketAddress) mainCxn.getLocalInetAddress();
			URI localURI = URIUtil.createURI(address.getHostName(), address.getPort());

			for(Entry<URI,Map<OFAuxId, OFBsnControllerConnection>> entry : this.controllerConnections.entrySet()) {

				// Don't check our own controller connections
				URI uri = entry.getKey();
				if(!localURI.equals(uri)){

					// We only care for the MAIN connection
					Map<OFAuxId, OFBsnControllerConnection> cxns = this.controllerConnections.get(uri);
					OFBsnControllerConnection controllerCxn = cxns.get(OFAuxId.MAIN);

					if(controllerCxn != null) {
						// If the controller id disconnected or not master we know it is not connected
						if(controllerCxn.getState() == OFBsnControllerConnectionState.BSN_CONTROLLER_CONNECTION_STATE_CONNECTED
								&& controllerCxn.getRole() == OFControllerRole.ROLE_MASTER){
							return true;
						}
					} else {
						log.warn("Unable to find controller connection with aux id "
								+ "MAIN for switch {} on controller with URI {}.",
								this, uri);
					}
				}
			}
		}
		return false;
	}

	@Override
	public TableFeatures getTableFeatures(TableId table) {
		return tableFeaturesByTableId.get(table);
	}

	@Override
	public TableId getMaxTableForTableMissFlow() {
		return maxTableToGetTableMissFlow;
	}
	
	@Override
	public TableId setMaxTableForTableMissFlow(TableId max) {
		if (max.getValue() >= tables) {
			maxTableToGetTableMissFlow = TableId.of(tables - 1 < 0 ? 0 : tables - 1);
		} else {
			maxTableToGetTableMissFlow = max;
		}
		return maxTableToGetTableMissFlow;
	}
}
