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

package net.floodlightcontroller.staticflowentry.web;

import java.io.IOException;
import java.util.Map;


import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.staticflowentry.StaticFlowEntries;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;

/**
 * Pushes a static flow entry to the storage source
 * @author alexreimers
 *
 */
@LogMessageCategory("Static Flow Pusher")
public class StaticFlowEntryPusherResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(StaticFlowEntryPusherResource.class);

	/**
	 * Checks to see if the user matches IP information without
	 * checking for the correct ether-type (2048).
	 * @param rows The Map that is a string representation of
	 * the static flow.
	 * @reutrn True if they checked the ether-type, false otherwise
	 */
	private boolean checkMatchIp(Map<String, Object> rows) {
		boolean matchEther = false;
		String val = (String) rows.get(StaticFlowEntryPusher.COLUMN_DL_TYPE);
		if (val != null) {
			int type = 0;
			// check both hex and decimal
			if (val.startsWith("0x")) {
				type = Integer.parseInt(val.substring(2), 16);
			} else {
				try {
					type = Integer.parseInt(val);
				} catch (NumberFormatException e) { /* fail silently */}
			}
			if (type == 2048) matchEther = true;
		}

		if ((rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_DST) ||
				rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_SRC) ||
				rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_PROTO) ||
				rows.containsKey(StaticFlowEntryPusher.COLUMN_NW_TOS)) &&
				(matchEther == false))
			return false;

		return true;
	}

	/**
	 * The check for flow entry validity will happen only when the SFP's 
	 * storage listener detects the row entry is inserted. This, unfortunately,
	 * is after the REST API returns the message to the user who's inserting
	 * the flow.
	 * 
	 * This function will perform the same error checking that will happen
	 * automatically later when the OFFlowMod is composed and built. This is
	 * somewhat redundant, since the flow entry will only be sent to the
	 * switch if it's valid, but this is the only way to tell the user something
	 * wasn't correct in their flow definition.
	 * 
	 * @param row
	 * @return A String describing the error if there is one; the empty string if all checks
	 *
	private String validateFlowEntry(Map<String, Object> row) {
		/*
		 * First, build the match string and try to turn it into a
		 * Match.Builder with all the fields set. Then, before the
		 * it's built, check all prerequisites.
		 *
		IOFSwitchService switchService = (IOFSwitchService) getContext()
				.getAttributes()
				.get(IOFSwitchService.class.getCanonicalName());
		String switchName = (String) row.get(StaticFlowEntryPusher.COLUMN_SWITCH);
		String entryName = (String) row.get(StaticFlowEntryPusher.COLUMN_NAME);
		String matchString;
		DatapathId dpid;
		
		try {
			dpid = DatapathId.of(switchName);
		} catch (NumberFormatException e) {
			return "Invalid switch DPID " + switchName + ".";
		}
		
		IOFSwitch theSwitch = switchService.getSwitch(DatapathId.of(switchName));
		
		if (theSwitch == null) {
			return "Switch " + switchName + " is not connected to the controller.";		
		}
		
		for (String key : row.keySet()) {
			// get the correct builder for the OF version supported by the switch
			OFFlowModify.Builder fmb = theSwitch.getOFFactory().buildFlowModify();

			StaticFlowEntries.initDefaultFlowMod(fmb, entryName);

			if (row.get(key) == null
					|| key.equals(StaticFlowEntryPusher.COLUMN_SWITCH) 
					|| key.equals(StaticFlowEntryPusher.COLUMN_NAME) 
					|| key.equals("id")
					|| key.equals(StaticFlowEntryPusher.COLUMN_HARD_TIMEOUT) 
					|| key.equals(StaticFlowEntryPusher.COLUMN_IDLE_TIMEOUT)) {
				continue;
			}

			if (key.equals(StaticFlowEntryPusher.COLUMN_ACTIVE)) {
				if  (!Boolean.valueOf((String) row.get(StaticFlowEntryPusher.COLUMN_ACTIVE))) {
					log.debug("Flow entry is inactive; verifying it anyway."); 
				}
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_TABLE_ID)) {
				if (fmb.getVersion() != OFVersion.OF_10) { // all except 1.0 support tables 
					fmb.setTableId(TableId.of(Integer.parseInt((String) row.get(key)))); // support multiple flow tables for OF1.1+
				} else {
					return "Tables not supported in OpenFlow 1.0.";
				}
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_ACTIONS)) {
				ActionUtils.fromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_ACTIONS), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_COOKIE)) {
				fmb.setCookie(StaticFlowEntries.computeEntryCookie(Integer.valueOf((String) row.get(StaticFlowEntryPusher.COLUMN_COOKIE)), entryName));
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_PRIORITY)) {
				fmb.setPriority(U16.t(Integer.valueOf((String) row.get(StaticFlowEntryPusher.COLUMN_PRIORITY))));
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_APPLY_ACTIONS)) {
				InstructionUtils.applyActionsFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_APPLY_ACTIONS), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_CLEAR_ACTIONS)) {
				InstructionUtils.clearActionsFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_CLEAR_ACTIONS), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_EXPERIMENTER)) {
				InstructionUtils.experimenterFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_EXPERIMENTER), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_METER)) {
				InstructionUtils.meterFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_METER), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_TABLE)) {
				InstructionUtils.gotoTableFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_GOTO_TABLE), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_ACTIONS)) {
				InstructionUtils.writeActionsFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_ACTIONS), log);
			} else if (key.equals(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_METADATA)) {
				InstructionUtils.writeMetadataFromString(fmb, (String) row.get(StaticFlowEntryPusher.COLUMN_INSTR_WRITE_METADATA), log);
			} else { // the rest of the keys are for Match().fromString()
				if (matchString.length() > 0) {
					matchString.append(",");
				}
				matchString.append(key + "=" + row.get(key).toString());
			}
		}
		String match = matchString.toString();

		fmb.setMatch(MatchUtils.fromString(match, fmb.getVersion()));

		return "";
	} */

	/**
	 * Takes a Static Flow Pusher string in JSON format and parses it into
	 * our database schema then pushes it to the database.
	 * @param fmJson The Static Flow Pusher entry in JSON format.
	 * @return A string status message
	 */
	@Post
	@LogMessageDoc(level="ERROR",
	message="Error parsing push flow mod request: {request}",
	explanation="An invalid request was sent to static flow pusher",
	recommendation="Fix the format of the static flow mod request")
	public String store(String fmJson) {
		IStorageSourceService storageSource =
				(IStorageSourceService)getContext().getAttributes().
				get(IStorageSourceService.class.getCanonicalName());

		Map<String, Object> rowValues;
		try {
			rowValues = StaticFlowEntries.jsonToStorageEntry(fmJson);
			String status = null;
			if (!checkMatchIp(rowValues)) {
				status = "Warning! Must specify eth_type of IPv4/IPv6 to " +
						"match on IPv4/IPv6 fields! The flow has been discarded.";
				log.error(status);
			} else {
				status = "Entry pushed";
			}
			storageSource.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, rowValues);
			return ("{\"status\" : \"" + status + "\"}");
		} catch (IOException e) {
			log.error("Error parsing push flow mod request: " + fmJson, e);
			return "{\"status\" : \"Error! Could not parse flod mod, see log for details.\"}";
		}
	}

	@Delete
	@LogMessageDoc(level="ERROR",
	message="Error deleting flow mod request: {request}",
	explanation="An invalid delete request was sent to static flow pusher",
	recommendation="Fix the format of the static flow mod request")
	public String del(String fmJson) {
		IStorageSourceService storageSource =
				(IStorageSourceService)getContext().getAttributes().
				get(IStorageSourceService.class.getCanonicalName());
		String fmName = null;
		if (fmJson == null) {
			return "{\"status\" : \"Error! No data posted.\"}";
		}
		try {
			fmName = StaticFlowEntries.getEntryNameFromJson(fmJson);
			if (fmName == null) {
				return "{\"status\" : \"Error deleting entry, no name provided\"}";
			}
		} catch (IOException e) {
			log.error("Error deleting flow mod request: " + fmJson, e);
			return "{\"status\" : \"Error deleting entry, see log for details\"}";
		}

		storageSource.deleteRowAsync(StaticFlowEntryPusher.TABLE_NAME, fmName);
		return "{\"status\" : \"Entry " + fmName + " deleted\"}";
	}
}
