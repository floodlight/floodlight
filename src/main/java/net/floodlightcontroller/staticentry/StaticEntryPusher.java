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

package net.floodlightcontroller.staticentry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticentry.web.StaticEntryWebRoutable;
import net.floodlightcontroller.staticentry.web.StaticFlowEntryWebRoutable;
import net.floodlightcontroller.staticentry.web.StaticFlowWebRoutable;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.GroupUtils;
import net.floodlightcontroller.util.InstructionUtils;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowRemovedReason;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * This module is responsible for maintaining a set of static flows on
 * switches. This is just a big 'ol dumb list of flows and groups and something external
 * is responsible for ensuring they make sense for the network.
 */
public class StaticEntryPusher
implements IOFSwitchListener, IFloodlightModule, IStaticEntryPusherService, IStorageSourceListener, IOFMessageListener {
	protected static Logger log = LoggerFactory.getLogger(StaticEntryPusher.class);
	public static final String MODULE_NAME = "staticentrypusher";

	public static final int STATIC_ENTRY_APP_ID = 10;
	static {
		AppCookie.registerApp(STATIC_ENTRY_APP_ID, MODULE_NAME);
	}

	public static final String TABLE_NAME = "controller_staticentrytable";

	public static class Columns {
		public static final String COLUMN_NAME = "name";
		public static final String COLUMN_ENTRY_TYPE = "entry_type";
		public static final String ENTRY_TYPE_FLOW = "flow";
		public static final String ENTRY_TYPE_GROUP = "group";
		public static final String COLUMN_SWITCH = "switch";
		public static final String COLUMN_TABLE_ID = "table";
		public static final String COLUMN_ACTIVE = "active";
		public static final String COLUMN_IDLE_TIMEOUT = "idle_timeout";
		public static final String COLUMN_HARD_TIMEOUT = "hard_timeout";
		public static final String COLUMN_PRIORITY = "priority";
		public static final String COLUMN_COOKIE = "cookie";

		/* NOTE: Use MatchUtil's names for MatchField column names */

		/* 
		 * Support for OF1.0 generic transport ports (possibly from the REST API). 
		 * Only use these to read them in, but store them as the type of port their IpProto
		 */
		public static final String COLUMN_NW_TOS = MatchUtils.STR_NW_TOS;
		public static final String COLUMN_TP_SRC = MatchUtils.STR_TP_SRC;
		public static final String COLUMN_TP_DST = MatchUtils.STR_TP_DST;

		public static final String COLUMN_ACTIONS = "actions";

		/* NOTE: Use InstructionUtil's names for Instruction column names */

		public static final String COLUMN_GROUP_TYPE = GroupUtils.GROUP_TYPE;
		public static final String COLUMN_GROUP_BUCKETS = GroupUtils.GROUP_BUCKETS;
		public static final String COLUMN_GROUP_ID = GroupUtils.GROUP_ID;

		private static Set<String> ALL_COLUMNS;	/* Use internally to query only */
	}

	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IStorageSourceService storageSourceService;
	protected IRestApiService restApiService;

	private IHAListener haListener;

	// Map<DPID, Map<Name, OFMessage>>; OFMessage can be null to indicate non-active
	protected Map<String, Map<String, OFMessage>> entriesFromStorage;
	// Entry Name -> DPID of Switch it's on
	protected Map<String, String> entry2dpid;

	// Class to sort FlowMod's by priority, from lowest to highest
	class FlowModSorter implements Comparator<String> {
		private String dpid;
		public FlowModSorter(String dpid) {
			this.dpid = dpid;
		}
		@Override
		public int compare(String o1, String o2) {
			OFMessage m1 = entriesFromStorage.get(dpid).get(o1);
			OFMessage m2 = entriesFromStorage.get(dpid).get(o2);
			if (m1 == null || m2 == null) {// sort active=false flows by key
				return o1.compareTo(o2);
			}
			if (m1 instanceof OFFlowMod && m2 instanceof OFFlowMod) {
				return (int) (U32.of(((OFFlowMod) m1).getPriority()).getValue() - U32.of(((OFFlowMod) m2).getPriority()).getValue());
			} else if (m1 instanceof OFFlowMod) {
				return 1;
			} else if (m2 instanceof OFFlowMod) {
				return -1;
			} else {
				return 0;
			}
		}
	};

	public static String matchFieldToColumnName(MatchFields mf) {
		return MatchUtils.getMatchFieldName(mf);
	}
	
	public static String intructionToColumnName(OFInstructionType t) {
		return InstructionUtils.getInstructionName(t);
	}
	
	/**
	 * used for debugging and unittests
	 * @return the number of static flow entries as cached from storage
	 */
	public int countEntries() {
		int size = 0;
		if (entriesFromStorage == null)
			return 0;
		for (String ofswitch : entriesFromStorage.keySet())
			size += entriesFromStorage.get(ofswitch).size();
		return size;
	}

	public IFloodlightProviderService getFloodlightProvider() {
		return floodlightProviderService;
	}

	public void setFloodlightProvider(IFloodlightProviderService floodlightProviderService) {
		this.floodlightProviderService = floodlightProviderService;
	}

	public void setStorageSource(IStorageSourceService storageSourceService) {
		this.storageSourceService = storageSourceService;
	}

	/**
	 * Reads from our entriesFromStorage for the specified switch and
	 * sends the FlowMods down to the controller in <b>sorted</b> order.
	 *
	 * Sorted is important to maintain correctness of the switch:
	 * if a packet would match both a lower and a higher priority
	 * rule, then we want it to match the higher priority or nothing,
	 * but never just the lower priority one.  Inserting from high to
	 * low priority fixes this.
	 *
	 * TODO consider adding a "block all" flow mod and then removing it
	 * while starting up.
	 *
	 * @param sw The switch to send entries to
	 */
	protected void sendEntriesToSwitch(DatapathId switchId) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		if (sw == null)
			return;
		String stringId = sw.getId().toString();

		if ((entriesFromStorage != null) && (entriesFromStorage.containsKey(stringId))) {
			Map<String, OFMessage> entries = entriesFromStorage.get(stringId);
			List<String> sortedList = new ArrayList<String>(entries.keySet());
			// weird that Collections.sort() returns void
			Collections.sort( sortedList, new FlowModSorter(stringId));
			for (String entryName : sortedList) {
				OFMessage message = entries.get(entryName);
				if (message != null) {
					if (log.isDebugEnabled()) {
						log.debug("Pushing static entry {} for {}", stringId, entryName);
					}
					writeOFMessageToSwitch(sw.getId(), message);
				}
			}
		}
	}

	/**
	 * Used only for bundle-local indexing
	 *
	 * @param map
	 * @return
	 */

	protected Map<String, String> computeEntry2DpidMap(
			Map<String, Map<String, OFMessage>> map) {
		Map<String, String> ret = new ConcurrentHashMap<String, String>();
		for(String dpid : map.keySet()) {
			for( String entry: map.get(dpid).keySet())
				ret.put(entry, dpid);
		}
		return ret;
	}

	/**
	 * Read entries from storageSource, and store them in a hash
	 *
	 * @return
	 */
	private Map<String, Map<String, OFMessage>> readEntriesFromStorage() {
		Map<String, Map<String, OFMessage>> entries = new ConcurrentHashMap<String, Map<String, OFMessage>>();
		try {
			Map<String, Object> row;
			// null1=no predicate, null2=no ordering
			IResultSet resultSet = storageSourceService.executeQuery(TABLE_NAME, Columns.ALL_COLUMNS.toArray(new String[Columns.ALL_COLUMNS.size()]), null, null);
			for (Iterator<IResultSet> it = resultSet.iterator(); it.hasNext();) {
				row = it.next().getRow();
				parseRow(row, entries);
			}
		} catch (StorageException e) {
			log.error("failed to access storage: {}", e.getMessage());
			// if the table doesn't exist, then wait to populate later via
			// setStorageSource()
		}
		return entries;
	}

	/**
	 * Take a single row, turn it into a entry.
	 * If an entry is inactive, mark it with null
	 *
	 * @param row
	 * @param entries
	 */
	void parseRow(Map<String, Object> row, Map<String, Map<String, OFMessage>> entries) {
		String switchName = null;
		String entryName = null;
		String entryType = Columns.ENTRY_TYPE_FLOW;

		StringBuffer matchString = new StringBuffer();
		OFFlowMod.Builder fmb = null; 
		OFGroupMod.Builder gmb = null;

		if (!row.containsKey(Columns.COLUMN_SWITCH) || !row.containsKey(Columns.COLUMN_NAME)) {
			log.warn("Skipping entry with missing required 'switch' or 'name' entry: {}", row);
			return;
		}
		
		try {
			switchName = DatapathId.of((String) row.get(Columns.COLUMN_SWITCH)).toString();
			entryName = (String) row.get(Columns.COLUMN_NAME);

			String tmp = (String) row.get(Columns.COLUMN_ENTRY_TYPE);
			if (tmp != null) {
				tmp = tmp.toLowerCase().trim();
				if (tmp.equals(Columns.ENTRY_TYPE_GROUP)) {
					entryType = Columns.ENTRY_TYPE_GROUP;
				}
			} /* else use default of flow */

			if (!entries.containsKey(switchName)) {
				entries.put(switchName, new HashMap<String, OFMessage>());
			}

			/* get the correct builder for the OF version supported by the switch */
			try {
				if (entryType.equals(Columns.ENTRY_TYPE_FLOW)) {
					fmb = OFFactories.getFactory(switchService.getSwitch(DatapathId.of(switchName)).getOFFactory().getVersion()).buildFlowModify();
					StaticEntries.initDefaultFlowMod(fmb, entryName);
				} else if (entryType.equals(Columns.ENTRY_TYPE_GROUP)) {
					gmb = OFFactories.getFactory(switchService.getSwitch(DatapathId.of(switchName)).getOFFactory().getVersion()).buildGroupModify();
				} else {
					log.error("Not adding a flow or a group? Possible Static Flow Pusher bug");
					return;
				}
			} catch (NullPointerException e) {
				/* switch was not connected/known */
				storageSourceService.deleteRowAsync(TABLE_NAME, entryName);
				log.error("Deleting entry {}. Switch {} was not connected to the controller, and we need to know the OF protocol version to compose the flow mod.", entryName, switchName);
				return;
			}

			for (String key : row.keySet()) {
				if (row.get(key) == null) {
					continue;
				}

				if (key.equals(Columns.COLUMN_SWITCH) || key.equals(Columns.COLUMN_NAME)) {
					continue; // already handled
				}

				if (key.equals(Columns.COLUMN_ACTIVE)) {
					if (!Boolean.valueOf((String) row.get(Columns.COLUMN_ACTIVE))) {
						log.debug("skipping inactive entry {} for switch {}", entryName, switchName);
						entries.get(switchName).put(entryName, null);  // mark this an inactive
						return;
					}
				} else if (key.equals(Columns.COLUMN_HARD_TIMEOUT) && fmb != null) {
					fmb.setHardTimeout(Integer.valueOf((String) row.get(Columns.COLUMN_HARD_TIMEOUT)));
				} else if (key.equals(Columns.COLUMN_IDLE_TIMEOUT) && fmb != null) {
					fmb.setIdleTimeout(Integer.valueOf((String) row.get(Columns.COLUMN_IDLE_TIMEOUT)));
				} else if (key.equals(Columns.COLUMN_TABLE_ID) && fmb != null) {
					if (fmb.getVersion().compareTo(OFVersion.OF_10) > 0) {
						fmb.setTableId(TableId.of(Integer.parseInt((String) row.get(key)))); // support multiple flow tables for OF1.1+
					} else {
						log.error("Table not supported in OpenFlow 1.0");
					}
				} else if (key.equals(Columns.COLUMN_ACTIONS) && fmb != null) {
					ActionUtils.fromString(fmb, (String) row.get(Columns.COLUMN_ACTIONS));
				} else if (key.equals(Columns.COLUMN_COOKIE) && fmb != null) {
					fmb.setCookie(StaticEntries.computeEntryCookie(Integer.valueOf((String) row.get(Columns.COLUMN_COOKIE)), entryName));
				} else if (key.equals(Columns.COLUMN_PRIORITY) && fmb != null) {
					fmb.setPriority(U32.t(Integer.valueOf((String) row.get(Columns.COLUMN_PRIORITY))));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.APPLY_ACTIONS)) && fmb != null) {
					InstructionUtils.applyActionsFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.APPLY_ACTIONS)));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.CLEAR_ACTIONS)) && fmb != null) {
					InstructionUtils.clearActionsFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.CLEAR_ACTIONS)));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.EXPERIMENTER)) && fmb != null) {
					InstructionUtils.experimenterFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.EXPERIMENTER)));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.METER)) && fmb != null) {
					InstructionUtils.meterFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.METER)));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.GOTO_TABLE)) && fmb != null) {
					InstructionUtils.gotoTableFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.GOTO_TABLE)));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_ACTIONS)) && fmb != null) {
					InstructionUtils.writeActionsFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_ACTIONS)));
				} else if (key.equals(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_METADATA)) && fmb != null) {
					InstructionUtils.writeMetadataFromString(fmb, 
							(String) row.get(StaticEntryPusher.intructionToColumnName(OFInstructionType.WRITE_METADATA)));
				} else if (key.equals(Columns.COLUMN_GROUP_TYPE) && gmb != null) {
					GroupUtils.setGroupTypeFromString(gmb, (String) row.get(Columns.COLUMN_GROUP_TYPE));
				} else if (key.equals(Columns.COLUMN_GROUP_BUCKETS) && gmb != null) {
					GroupUtils.setGroupBucketsFromJsonArray(gmb, (String) row.get(Columns.COLUMN_GROUP_BUCKETS));
				} else if (key.equals(Columns.COLUMN_GROUP_ID) && gmb != null) {
					GroupUtils.setGroupIdFromString(gmb, (String) row.get(Columns.COLUMN_GROUP_ID));
				} else if (fmb != null) { // the rest of the keys are for Match().fromString()

					if (matchString.length() > 0) {
						matchString.append(",");
					}
					matchString.append(key + "=" + row.get(key).toString());
				}
			}
		} catch (Exception e) {
			if (entryName != null && switchName != null) {
				log.warn("Skipping entry {} on switch {} with bad data : " + e.getMessage(), entryName, switchName);
			} else {
				log.warn("Skipping entry with bad data: {} :: {} ", e.getMessage(), e.getStackTrace());
			}
		}

		if (fmb != null) {
			String match = matchString.toString();

			try {
				fmb.setMatch(MatchUtils.fromString(match, fmb.getVersion()));
			} catch (IllegalArgumentException e) {
				log.error(e.toString());
				log.error("Ignoring flow entry {} on switch {} with illegal OFMatch() key: " + match, entryName, switchName);
				return;
			} catch (Exception e) {
				log.error("OF version incompatible for the match: " + match);
				e.printStackTrace();
				return;
			}
			entries.get(switchName).put(entryName, fmb.build());
		} else if (gmb != null) {
			entries.get(switchName).put(entryName, gmb.build());
		} else {
			log.error("Processed neither flow nor group mod. Possible Static Flow Pusher bug");
		}
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		log.debug("Switch {} connected; processing its static entries",
				switchId.toString());
		sendEntriesToSwitch(switchId);
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// do NOT delete from our internal state; we're tracking the rules,
		// not the switches
	}

	@Override
	public void switchActivated(DatapathId switchId) {}

	@Override
	public void switchChanged(DatapathId switchId) {}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {}


	@Override
	public void rowsModified(String tableName, Set<Object> rowKeys) {
		// This handles both rowInsert() and rowUpdate()
		log.debug("Modifying Table {}", tableName);
		HashMap<String, Map<String, OFMessage>> entriesToAdd =
				new HashMap<String, Map<String, OFMessage>>();
		// build up list of what was added
		for (Object key: rowKeys) {
			IResultSet resultSet = storageSourceService.getRow(tableName, key);
			Iterator<IResultSet> it = resultSet.iterator();
			while (it.hasNext()) {
				Map<String, Object> row = it.next().getRow();
				parseRow(row, entriesToAdd);
			}
		}
		// batch updates by switch and blast them out
		for (String dpid : entriesToAdd.keySet()) {
			if (!entriesFromStorage.containsKey(dpid))
				entriesFromStorage.put(dpid, new HashMap<String, OFMessage>());

			List<OFMessage> outQueue = new ArrayList<OFMessage>();

			/* For every flow per dpid, decide how to "add" the flow. */
			for (String entry : entriesToAdd.get(dpid).keySet()) {
				OFFlowMod newFlowMod = null;
				OFFlowMod oldFlowMod = null;
				OFGroupMod newGroupMod = null;
				OFGroupMod oldGroupMod = null;

				if (entriesToAdd.get(dpid).get(entry) instanceof OFFlowMod) {
					newFlowMod = (OFFlowMod) entriesToAdd.get(dpid).get(entry);
				} else if (entriesToAdd.get(dpid).get(entry) instanceof OFGroupMod) {
					newGroupMod = (OFGroupMod) entriesToAdd.get(dpid).get(entry);
				}
				final boolean isFlowMod = newFlowMod == null ? false : true;


				String oldDpid = entry2dpid.get(entry);

				if (isFlowMod) {
					if (oldDpid != null) {
						oldFlowMod = (OFFlowMod) entriesFromStorage.get(oldDpid).remove(entry);
					} 

					/* Modify, which can be either a Flow MODIFY_STRICT or a Flow DELETE_STRICT with a side of Flow ADD */
					if (oldFlowMod != null && newFlowMod != null) { 
						/* MODIFY_STRICT b/c the match is still the same */
						if (oldFlowMod.getMatch().equals(newFlowMod.getMatch())
								&& oldFlowMod.getCookie().equals(newFlowMod.getCookie())
								&& oldFlowMod.getPriority() == newFlowMod.getPriority()
								&& oldDpid.equalsIgnoreCase(dpid)) {
							log.debug("ModifyStrict SFP Flow");
							entriesFromStorage.get(dpid).put(entry, newFlowMod);
							entry2dpid.put(entry, dpid);
							newFlowMod = FlowModUtils.toFlowModifyStrict(newFlowMod);
							outQueue.add(newFlowMod);
							/* DELETE_STRICT and then ADD b/c the match is now different */
						} else {
							log.debug("DeleteStrict and Add SFP Flow");
							oldFlowMod = FlowModUtils.toFlowDeleteStrict(oldFlowMod);
							OFFlowAdd addTmp = FlowModUtils.toFlowAdd(newFlowMod);
							/* If the flow's dpid and the current switch we're looking at are the same, add to the queue. */
							if (oldDpid.equals(dpid)) {
								outQueue.add(oldFlowMod);
								outQueue.add(addTmp); 
								/* Otherwise, go ahead and send the flows now (since queuing them will send to the wrong switch). */
							} else {
								writeOFMessageToSwitch(DatapathId.of(oldDpid), oldFlowMod);
								writeOFMessageToSwitch(DatapathId.of(dpid), FlowModUtils.toFlowAdd(newFlowMod)); 
							}
							entriesFromStorage.get(dpid).put(entry, addTmp);
							entry2dpid.put(entry, dpid);			
						}
						/* Add a brand-new flow with ADD */
					} else if (newFlowMod != null && oldFlowMod == null) {
						log.debug("Add SFP Flow");
						OFFlowAdd addTmp = FlowModUtils.toFlowAdd(newFlowMod);
						entriesFromStorage.get(dpid).put(entry, addTmp);
						entry2dpid.put(entry, dpid);
						outQueue.add(addTmp);
						/* Something strange happened, so remove the flow */
					} else if (newFlowMod == null) { 
						entriesFromStorage.get(dpid).remove(entry);
						entry2dpid.remove(entry);
					}
				} else { /* must be a group mod */
					if (oldDpid != null) {
						oldGroupMod = (OFGroupMod) entriesFromStorage.get(oldDpid).remove(entry);
					}

					if (oldGroupMod != null && newGroupMod != null) {
						/* Modify */
						if (oldGroupMod.getGroup().equals(newGroupMod.getGroup()) &&
								oldDpid.equalsIgnoreCase(dpid)) {
							log.debug("Modify SFP Group");
							entriesFromStorage.get(dpid).put(entry, newGroupMod);
							entry2dpid.put(entry, dpid);
							newGroupMod = GroupUtils.toGroupModify(newGroupMod);
							outQueue.add(newGroupMod);
						} else { /* Delete followed by Add */
							log.debug("Delete and Add SFP Group");
							oldGroupMod = GroupUtils.toGroupDelete(oldGroupMod);
							newGroupMod = GroupUtils.toGroupAdd(newGroupMod);
							if (oldDpid.equalsIgnoreCase(dpid)) {
								outQueue.add(oldGroupMod);
								outQueue.add(newGroupMod);
							} else {
								writeOFMessageToSwitch(DatapathId.of(oldDpid), oldGroupMod);
								writeOFMessageToSwitch(DatapathId.of(dpid), newGroupMod);
							}
							entriesFromStorage.get(dpid).put(entry, newGroupMod);
							entry2dpid.put(entry, dpid);
						}
						/* Add */
					} else if (oldGroupMod == null && newGroupMod != null) {
						log.debug("Add SFP Group");
						newGroupMod = GroupUtils.toGroupAdd(newGroupMod);
						entriesFromStorage.get(dpid).put(entry, newGroupMod);
						entry2dpid.put(entry, dpid);
						outQueue.add(newGroupMod);
					} else {
						/* Something strange happened; remove group */
						entriesFromStorage.get(dpid).remove(entry);
						entry2dpid.remove(entry);
					}
				}
			}
			/* Batch-write all queued messages to the switch */
			writeOFMessagesToSwitch(DatapathId.of(dpid), outQueue);
		}
	}

	@Override
	public void rowsDeleted(String tableName, Set<Object> rowKeys) {
		if (log.isDebugEnabled()) {
			log.debug("Deleting from table {}", tableName);
		}

		for(Object obj : rowKeys) {
			if (!(obj instanceof String)) {
				log.debug("Tried to delete non-string key {}; ignoring", obj);
				continue;
			}
			deleteStaticFlowEntry((String) obj);
		}
	}

	private void deleteStaticFlowEntry(String entryName) {
		String dpid = entry2dpid.remove(entryName);

		if (dpid == null) {
			// assume state has been cleared by deleteFlowsForSwitch() or
			// deleteAllFlows()
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Sending delete flow mod for flow {} for switch {}", entryName, dpid);
		}

		// send flow_mod delete
		if (switchService.getSwitch(DatapathId.of(dpid)) != null) {
			OFMessage message = entriesFromStorage.get(dpid).get(entryName);
			if (message instanceof OFFlowMod) {
				message = FlowModUtils.toFlowDeleteStrict((OFFlowMod) message);
			} else if (message instanceof OFGroupMod) {
				message = GroupUtils.toGroupDelete((OFGroupMod) message);
			}

			if (entriesFromStorage.containsKey(dpid) && entriesFromStorage.get(dpid).containsKey(entryName)) {
				entriesFromStorage.get(dpid).remove(entryName);
			} else {
				log.debug("Tried to delete non-existent entry {} for switch {}", entryName, dpid);
				return;
			}

			writeOFMessageToSwitch(DatapathId.of(dpid), message);
		} else {
			log.debug("Not sending flow delete for disconnected switch.");
		}
		return;
	}

	/**
	 * Writes a list of OFMessages to a switch
	 * @param dpid The datapath ID of the switch to write to
	 * @param messages The list of OFMessages to write.
	 */
	private void writeOFMessagesToSwitch(DatapathId dpid, List<OFMessage> messages) {
		IOFSwitch ofswitch = switchService.getSwitch(dpid);
		if (ofswitch != null) {  // is the switch connected
			if (log.isDebugEnabled()) {
				log.debug("Sending {} new entries to {}", messages.size(), dpid);
			}
			ofswitch.write(messages);
		}
	}

	/**
	 * Writes a single OFMessage to a switch
	 * @param dpid The datapath ID of the switch to write to
	 * @param message The OFMessage to write.
	 */
	private void writeOFMessageToSwitch(DatapathId dpid, OFMessage message) {
		IOFSwitch ofswitch = switchService.getSwitch(dpid);
		if (ofswitch != null) {  // is the switch connected
			if (log.isDebugEnabled()) {
				log.debug("Sending 1 new entries to {}", dpid.toString());
			}
			ofswitch.write(message);
		}
	}

	@Override
	public String getName() {
		return MODULE_NAME;
	}

	/**
	 * Handles a flow removed message from a switch. If the flow was removed
	 * and we did not explicitly delete it we re-install it. If we explicitly
	 * removed the flow we stop the processing of the flow removed message.
	 * @param sw The switch that sent the flow removed message.
	 * @param msg The flow removed message.
	 * @param cntx The associated context.
	 * @return Whether to continue processing this message.
	 */
	public Command handleFlowRemoved(IOFSwitch sw, OFFlowRemoved msg, FloodlightContext cntx) {
		U64 cookie = msg.getCookie();

		if (AppCookie.extractApp(cookie) == STATIC_ENTRY_APP_ID) {
			OFFlowRemovedReason reason = null;
			reason = msg.getReason();
			if (reason != null) {
				if (OFFlowRemovedReason.DELETE == reason) {
					log.debug("Received flow_removed message for a infinite " + 
							"timeout flow from switch {}. Removing it from the SFP DB", msg, sw);
				} else if (OFFlowRemovedReason.HARD_TIMEOUT == reason || OFFlowRemovedReason.IDLE_TIMEOUT == reason) {
					/* Remove the Flow from the DB since it timed out */
					log.debug("Received an IDLE or HARD timeout for an SFP flow. Removing it from the SFP DB");
				} else {
					log.debug("Received flow_removed message for reason {}. Removing it from the SFP DB", reason);
				}
				/* 
				 * Lookup the flow based on the flow contents. We do not know/care about the name of the 
				 * flow based on this message, but we can get the table values for this switch and search.
				 */
				String flowToRemove = null;
				Map<String, OFMessage> flowsByName = getEntries(sw.getId())
						.entrySet()
						.stream()
						.filter(e -> e.getValue() instanceof OFFlowMod)
						.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

				for (Map.Entry<String, OFMessage> e : flowsByName.entrySet()) {
					OFFlowMod f = (OFFlowMod) e.getValue();
					if (msg.getCookie().equals(f.getCookie()) &&
							(msg.getVersion().compareTo(OFVersion.OF_12) < 0 ? true : msg.getHardTimeout() == f.getHardTimeout()) &&
							msg.getIdleTimeout() == f.getIdleTimeout() &&
							msg.getMatch().equals(f.getMatch()) &&
							msg.getPriority() == f.getPriority() &&
							(msg.getVersion().compareTo(OFVersion.OF_10) == 0 ? true : msg.getTableId().equals(f.getTableId()))
							) {
						flowToRemove = e.getKey();
						break;
					}
				}

				/*
				 * Remove the flow. This will send the delete message to the switch,
				 * since we cannot tell the storage listener rowsdeleted() that we
				 * are only removing our local DB copy of the flow and that it actually
				 * timed out on the switch and is already gone. The switch will silently
				 * discard the delete message in this case.
				 * 
				 * TODO: We should come up with a way to convey to the storage listener
				 * the reason for the flow being removed.
				 */
				if (flowToRemove != null) {
					log.warn("Removing flow {} for reason {}", flowToRemove, reason);
					deleteEntry(flowToRemove);
				}

				/* Stop the processing chain since we sent or asked for the delete message. */
				return Command.STOP;
			}
		}
		/* Continue the processing chain, since we did not send the delete. */
		return Command.CONTINUE;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case FLOW_REMOVED:
			return handleFlowRemoved(sw, (OFFlowRemoved) msg, cntx);
		default:
			return Command.CONTINUE;
		}
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;  // no dependency for non-packet in
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;  // no dependency for non-packet in
	}

	// IFloodlightModule

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IStaticEntryPusherService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m =
		new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		m.put(IStaticEntryPusherService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		l.add(IStorageSourceService.class);
		l.add(IRestApiService.class);
		return l;
	}

	private void populateColumns() {
		Set<String> tmp = new HashSet<String>();
		tmp.add(Columns.COLUMN_NAME);
		tmp.add(Columns.COLUMN_ENTRY_TYPE);
		tmp.add(Columns.COLUMN_SWITCH);
		tmp.add(Columns.COLUMN_TABLE_ID);
		tmp.add(Columns.COLUMN_ACTIVE);
		tmp.add(Columns.COLUMN_IDLE_TIMEOUT);
		tmp.add(Columns.COLUMN_HARD_TIMEOUT);
		tmp.add(Columns.COLUMN_PRIORITY);
		tmp.add(Columns.COLUMN_COOKIE);
		tmp.add(Columns.COLUMN_TP_SRC);
		tmp.add(Columns.COLUMN_TP_DST);
		tmp.add(Columns.COLUMN_ACTIONS);
		
		tmp.add(Columns.COLUMN_GROUP_TYPE);
		tmp.add(Columns.COLUMN_GROUP_BUCKETS);
		tmp.add(Columns.COLUMN_GROUP_ID);
		
		for (MatchFields m : MatchFields.values()) {
			/* skip all BSN_* matches */
			if (!m.name().toLowerCase().startsWith("bsn")) {
				tmp.add(StaticEntryPusher.matchFieldToColumnName(m));
			}
		}
		
		for (OFInstructionType t : OFInstructionType.values()) {
			tmp.add(StaticEntryPusher.intructionToColumnName(t));
		}

		Columns.ALL_COLUMNS = ImmutableSet.copyOf(tmp);
		
	}
	
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		populateColumns();
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		storageSourceService = context.getServiceImpl(IStorageSourceService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		haListener = new HAListenerDelegate();
	} 

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.FLOW_REMOVED, this);
		switchService.addOFSwitchListener(this);
		floodlightProviderService.addHAListener(this.haListener);
		// assumes no switches connected at startup()
		storageSourceService.createTable(TABLE_NAME, null);
		storageSourceService.setTablePrimaryKeyName(TABLE_NAME, Columns.COLUMN_NAME);
		storageSourceService.addListener(TABLE_NAME, this);
		entriesFromStorage = readEntriesFromStorage();
		entry2dpid = computeEntry2DpidMap(entriesFromStorage);
		restApiService.addRestletRoutable(new StaticEntryWebRoutable()); /* current */
	    restApiService.addRestletRoutable(new StaticFlowWebRoutable()); /* v1.0 - v1.2 (v1.3?) */
	    restApiService.addRestletRoutable(new StaticFlowEntryWebRoutable()); /* v0.91, v0.90, and before */
	}

	// IStaticFlowEntryPusherService methods

	@Override
	public void addFlow(String name, OFFlowMod fm, DatapathId swDpid) {
		try {
			Map<String, Object> fmMap = StaticEntries.flowModToStorageEntry(fm, swDpid.toString(), name);
			storageSourceService.insertRowAsync(TABLE_NAME, fmMap);
		} catch (Exception e) {
			log.error("Did not add flow with bad match/action combination. {}", fm);
		}
	}

	@Override
	public void addGroup(String name, OFGroupMod gm, DatapathId swDpid) {
		try {
			Map<String, Object> gmMap = StaticEntries.groupModToStorageEntry(gm, swDpid.toString(), name);
			storageSourceService.insertRowAsync(TABLE_NAME, gmMap);
		} catch (Exception e) {
			log.error("Did not add group with bad match/action combination. {}", gm);
		}
	}

	@Override
	public void deleteEntry(String name) {
		storageSourceService.deleteRowAsync(TABLE_NAME, name);
	}

	@Override
	public void deleteAllEntries() {
		for (String entry : entry2dpid.keySet()) {
			deleteEntry(entry);
		}

		/*
        FIXME: Since the OF spec 1.0 is not clear on how
        to match on cookies. Once all switches come to a
        common implementation we can possibly re-enable this
        fix.

        // Send a delete for each switch
        Set<String> swSet = new HashSet<String>();
        for (String dpid : entry2dpid.values()) {
            // Avoid sending duplicate deletes
            if (!swSet.contains(dpid)) {
                swSet.add(dpid);
                sendDeleteByCookie(HexString.toLong(dpid));
            }
        }

        // Clear our map
        entry2dpid.clear();

        // Clear our book keeping map
        for (Map<String, OFFlowMod> eMap : entriesFromStorage.values()) {
            eMap.clear();
        }

        // Reset our DB
        storageSource.deleteMatchingRowsAsync(TABLE_NAME, null);
		 */
	}

	@Override
	public void deleteEntriesForSwitch(DatapathId dpid) {
		String sDpid = dpid.toString();

		for (Entry<String, String> e : entry2dpid.entrySet()) {
			if (e.getValue().equals(sDpid))
				deleteEntry(e.getKey());
		}

		/*
        FIXME: Since the OF spec 1.0 is not clear on how
        to match on cookies. Once all switches come to a
        common implementation we can possibly re-enable this
        fix.
        //sendDeleteByCookie(dpid);

        String sDpid = HexString.toHexString(dpid);
        // Clear all internal flows for this switch
        Map<String, OFFlowMod> sMap = entriesFromStorage.get(sDpid);
        if (sMap != null) {
            for (String entryName : sMap.keySet()) {
                entry2dpid.remove(entryName);
                // Delete from DB
                deleteFlow(entryName);
            }
            sMap.clear();
        } else {
            log.warn("Map of storage entries for switch {} was null", sDpid);
        }
		 */
	}

	/**
	 * Deletes all flows installed by static flow pusher on a given switch.
	 * We send a delete flow mod with the static flow pusher app ID in the cookie.
	 * Since OF1.0 doesn't support masking based on the cookie we have to
	 * disable having flow specific cookies.
	 * @param dpid The DPID of the switch to clear all it's flows.
	 */
	/*
    FIXME: Since the OF spec 1.0 is not clear on how
    to match on cookies. Once all switches come to a
    common implementation we can possibly re-enable this
    fix.
    private void sendDeleteByCookie(long dpid) {
        if (log.isDebugEnabled())
            log.debug("Deleting all static flows on switch {}", HexString.toHexString(dpid));

        IOFSwitch sw = floodlightProvider.getSwitch(dpid);
        if (sw == null) {
            log.warn("Tried to delete static flows for non-existant switch {}",
                    HexString.toHexString(dpid));
            return;
        }

        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().
                getMessage(OFType.FLOW_MOD);
        OFMatch ofm = new OFMatch();
        fm.setMatch(ofm);
        fm.setCookie(AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, 0));
        fm.setCommand(OFFlowMod.OFPFC_DELETE);
        fm.setOutPort(OFPort.OFPP_NONE);

        try {
            sw.write(fm, null);
            sw.flush();
        } catch (IOException e1) {
            log.error("Error deleting all flows for switch {}:\n {}",
                    HexString.toHexString(dpid), e1.getMessage());
            return;
        }
    }
	 */

	@Override
	public Map<String, Map<String, OFMessage>> getEntries() {
		return entriesFromStorage;
	}

	@Override
	public Map<String, OFMessage> getEntries(DatapathId dpid) {
		Map<String, OFMessage> m = entriesFromStorage.get(dpid.toString());
		return m == null ? Collections.emptyMap() : m;
	}

	// IHAListener

	private class HAListenerDelegate implements IHAListener {
		@Override
		public void transitionToActive() {
			log.debug("Re-reading static flows from storage due " +
					"to HA change from STANDBY->ACTIVE");
			entriesFromStorage = readEntriesFromStorage();
			entry2dpid = computeEntry2DpidMap(entriesFromStorage);
		}

		@Override
		public void controllerNodeIPsChanged(
				Map<String, String> curControllerNodeIPs,
				Map<String, String> addedControllerNodeIPs,
				Map<String, String> removedControllerNodeIPs) {
			// ignore
		}

		@Override
		public String getName() {
			return StaticEntryPusher.this.getName();
		}

		@Override
		public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
				String name) {
			return false;
		}

		@Override
		public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
				String name) {
			return false;
		}

		@Override
		public void transitionToStandby() {	
			log.debug("Controller is now in STANDBY role. Clearing static flow entries from store.");
			deleteAllEntries();
		}
	}

	@Override
	public void switchDeactivated(DatapathId switchId) {
	}
}