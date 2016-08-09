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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.util.OFMessageUtils;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.staticentry.StaticEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import static net.floodlightcontroller.staticentry.StaticEntryPusher.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

public class StaticFlowTests extends FloodlightTestCase {
	protected static Logger log = LoggerFactory.getLogger(StaticFlowTests.class);

	static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";
	static int TotalTestRules = 3;

	static OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

	/***
	 * Create TestRuleXXX and the corresponding FlowModXXX
	 * for X = 1..3
	 */
	static Map<String,Object> TestRule1;
	static OFFlowMod FlowMod1;
	static {
		FlowMod1 = factory.buildFlowModify().build();
		TestRule1 = new HashMap<String,Object>();
		TestRule1.put(Columns.COLUMN_NAME, "TestRule1");
		TestRule1.put(Columns.COLUMN_SWITCH, TestSwitch1DPID);
		// setup match
		Match match;
		TestRule1.put(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_DST), "00:20:30:40:50:60");
		match = MatchUtils.fromString("eth_dst=00:20:30:40:50:60", factory.getVersion());
		// setup actions
		List<OFAction> actions = new LinkedList<OFAction>();
		TestRule1.put(Columns.COLUMN_ACTIONS, "output=1");
		actions.add(factory.actions().output(OFPort.of(1), Integer.MAX_VALUE));
		// done
		FlowMod1 = FlowMod1.createBuilder().setMatch(match)
				.setActions(actions)
				.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
				.setBufferId(OFBufferId.NO_BUFFER)
				.setOutPort(OFPort.ANY)
				.setPriority(Integer.MAX_VALUE)
				.setXid(4)
				.build();
	}

	static Map<String,Object> TestRule2;
	static OFFlowMod FlowMod2;

	static {
		FlowMod2 = factory.buildFlowModify().build();
		TestRule2 = new HashMap<String,Object>();
		TestRule2.put(Columns.COLUMN_NAME, "TestRule2");
		TestRule2.put(Columns.COLUMN_SWITCH, TestSwitch1DPID);
		// setup match
		Match match;        
		TestRule2.put(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_TYPE), "0x800");
		TestRule2.put(StaticEntryPusher.matchFieldToColumnName(MatchFields.IPV4_DST), "192.168.1.0/24");
		match = MatchUtils.fromString("eth_type=0x800,ipv4_dst=192.168.1.0/24", factory.getVersion());
		// setup actions
		List<OFAction> actions = new LinkedList<OFAction>();
		TestRule2.put(Columns.COLUMN_ACTIONS, "output=1");
		actions.add(factory.actions().output(OFPort.of(1), Integer.MAX_VALUE));
		// done
		FlowMod2 = FlowMod2.createBuilder().setMatch(match)
				.setActions(actions)
				.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
				.setBufferId(OFBufferId.NO_BUFFER)
				.setOutPort(OFPort.ANY)
				.setPriority(Integer.MAX_VALUE)
				.setXid(5)
				.build();
	}


	static Map<String,Object> TestRule3;
	static OFFlowMod FlowMod3;
	private StaticEntryPusher pusher;
	private IOFSwitchService switchService;
	private IOFSwitch mockSwitch;
	private MockDebugCounterService debugCounterService;
	private Capture<OFMessage> writeCapture;
	private Capture<List<OFMessage>> writeCaptureList;
	private long dpid;
	private MemoryStorageSource storage;
	static {
		FlowMod3 = factory.buildFlowModify().build();
		TestRule3 = new HashMap<String,Object>();
		TestRule3.put(Columns.COLUMN_NAME, "TestRule3");
		TestRule3.put(Columns.COLUMN_SWITCH, TestSwitch1DPID);
		// setup match
		Match match;
		TestRule3.put(StaticEntryPusher.matchFieldToColumnName(MatchFields.ETH_DST), "00:20:30:40:50:60");
		TestRule3.put(StaticEntryPusher.matchFieldToColumnName(MatchFields.VLAN_VID), 96);
		match = MatchUtils.fromString("eth_dst=00:20:30:40:50:60,eth_vlan_vid=96", factory.getVersion());
		// setup actions
		TestRule3.put(Columns.COLUMN_ACTIONS, "output=controller");
		List<OFAction> actions = new LinkedList<OFAction>();
		actions.add(factory.actions().output(OFPort.CONTROLLER, Integer.MAX_VALUE));
		// done
		FlowMod3 = FlowMod3.createBuilder().setMatch(match)
				.setActions(actions)
				.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
				.setBufferId(OFBufferId.NO_BUFFER)
				.setOutPort(OFPort.ANY)
				.setPriority(Integer.MAX_VALUE)
				.setXid(6)
				.build();
	}

	private void verifyFlowMod(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
		verifyMatch(testFlowMod, goodFlowMod);
		verifyActions(testFlowMod, goodFlowMod);
		// dont' bother testing the cookie; just copy it over
		goodFlowMod = goodFlowMod.createBuilder().setCookie(testFlowMod.getCookie()).build();
		// .. so we can continue to use .equals()
        assertEquals(OFMessageUtils.OFMessageIgnoreXid.of(goodFlowMod), OFMessageUtils.OFMessageIgnoreXid.of(testFlowMod));
	}


	private void verifyMatch(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
		assertEquals(goodFlowMod.getMatch(), testFlowMod.getMatch());
	}


	private void verifyActions(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
		if ((testFlowMod instanceof OFFlowDelete || 
				testFlowMod instanceof OFFlowDeleteStrict) &&
				(goodFlowMod instanceof OFFlowDelete || 
						goodFlowMod instanceof OFFlowDeleteStrict)) {
			log.warn("Not verifying actions of two flow-delete messages (they're undefined by design)");
		} else {
			List<OFAction> goodActions = goodFlowMod.getActions();
			List<OFAction> testActions = testFlowMod.getActions();
			assertNotNull(goodActions);
			assertNotNull(testActions);
			assertEquals(goodActions.size(), testActions.size());
			// assumes actions are marshalled in same order; should be safe
			for(int i = 0; i < goodActions.size(); i++) {
				assertEquals(goodActions.get(i), testActions.get(i));
			}
		}
	}


	@Override
	public void setUp() throws Exception {
		super.setUp();
		debugCounterService = new MockDebugCounterService();
		pusher = new StaticEntryPusher();
		switchService = getMockSwitchService();
		storage = new MemoryStorageSource();
		dpid = HexString.toLong(TestSwitch1DPID);

		mockSwitch = createNiceMock(IOFSwitch.class);
		writeCapture = EasyMock.newCapture(CaptureType.ALL);
		writeCaptureList = EasyMock.newCapture(CaptureType.ALL);

		expect(mockSwitch.write(capture(writeCapture))).andReturn(true).anyTimes();
		expect(mockSwitch.write(capture(writeCaptureList))).andReturn(Collections.<OFMessage>emptyList()).anyTimes();
		expect(mockSwitch.getOFFactory()).andReturn(factory).anyTimes();
		replay(mockSwitch);

		FloodlightModuleContext fmc = new FloodlightModuleContext();
		fmc.addService(IStorageSourceService.class, storage);
		fmc.addService(IOFSwitchService.class, getMockSwitchService());
		fmc.addService(IDebugCounterService.class, debugCounterService);

		MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
		Map<DatapathId, IOFSwitch> switchMap = new HashMap<DatapathId, IOFSwitch>();
		switchMap.put(DatapathId.of(dpid), mockSwitch);
		getMockSwitchService().setSwitches(switchMap);
		fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
		RestApiServer restApi = new RestApiServer();
		fmc.addService(IRestApiService.class, restApi);
		fmc.addService(IOFSwitchService.class, switchService);

		restApi.init(fmc);
		debugCounterService.init(fmc);
		storage.init(fmc);
		pusher.init(fmc);
		debugCounterService.init(fmc);
		storage.startUp(fmc);

		createStorageWithFlowEntries();

		pusher.startUp(fmc);    // again, to hack unittest
	}

	@Test
	public void testStaticFlowPush() throws Exception {

		// verify that flowpusher read all three entries from storage
		assertEquals(TotalTestRules, pusher.countEntries());

		// if someone calls mockSwitch.getOutputStream(), return mockOutStream instead
		//expect(mockSwitch.getOutputStream()).andReturn(mockOutStream).anyTimes();

		// if someone calls getId(), return this dpid instead
		resetToNice(mockSwitch);
		expect(mockSwitch.write(capture(writeCapture))).andReturn(true).anyTimes();
		expect(mockSwitch.write(capture(writeCaptureList))).andReturn(Collections.<OFMessage> emptyList()).anyTimes();
		expect(mockSwitch.getOFFactory()).andReturn(factory).anyTimes();
		expect(mockSwitch.getId()).andReturn(DatapathId.of(dpid)).anyTimes();
		replay(mockSwitch);

		// hook the static pusher up to the fake switch
		pusher.switchAdded(DatapathId.of(dpid));

		verify(mockSwitch);

		// Verify that the switch has gotten some flow_mods
		assertEquals(true, writeCapture.hasCaptured());
		assertEquals(TotalTestRules, writeCapture.getValues().size());

		// Order assumes how things are stored in hash bucket;
		// should be fixed because OFMessage.hashCode() is deterministic
		OFFlowMod firstFlowMod = (OFFlowMod) writeCapture.getValues().get(2);
		verifyFlowMod(firstFlowMod, FlowMod1);
		OFFlowMod secondFlowMod = (OFFlowMod) writeCapture.getValues().get(0); /* Java 8 stores 2-0-1 */
		verifyFlowMod(secondFlowMod, FlowMod2);
		OFFlowMod thirdFlowMod = (OFFlowMod) writeCapture.getValues().get(1);
		verifyFlowMod(thirdFlowMod, FlowMod3);

		writeCapture.reset();

		// delete two rules and verify they've been removed
		// this should invoke staticFlowPusher.rowsDeleted()
		storage.deleteRow(StaticEntryPusher.TABLE_NAME, "TestRule1");
		storage.deleteRow(StaticEntryPusher.TABLE_NAME, "TestRule2");

		assertEquals(1, pusher.countEntries());
		assertEquals(2, writeCapture.getValues().size());

		OFFlowMod firstDelete = (OFFlowMod) writeCapture.getValues().get(0);
		FlowMod1 = FlowModUtils.toFlowDeleteStrict(FlowMod1);
		verifyFlowMod(firstDelete, FlowMod1);

		OFFlowMod secondDelete = (OFFlowMod) writeCapture.getValues().get(1);
		FlowMod2 = FlowModUtils.toFlowDeleteStrict(FlowMod2);
		verifyFlowMod(secondDelete, FlowMod2);

		// add rules back to make sure that staticFlowPusher.rowsInserted() works
		writeCapture.reset();
		FlowMod2 = FlowModUtils.toFlowAdd(FlowMod2);
		FlowMod2 = FlowMod2.createBuilder().setXid(12).build();
		storage.insertRow(StaticEntryPusher.TABLE_NAME, TestRule2);
		assertEquals(2, pusher.countEntries());
		assertEquals(1, writeCaptureList.getValues().size());
		List<OFMessage> outList =
				writeCaptureList.getValues().get(0);
		assertEquals(1, outList.size());
		OFFlowMod firstAdd = (OFFlowMod) outList.get(0);
		verifyFlowMod(firstAdd, FlowMod2);
		writeCapture.reset();
		writeCaptureList.reset();

		// now try an overwriting update, calling staticFlowPusher.rowUpdated()
		TestRule3.put(StaticEntryPusher.matchFieldToColumnName(MatchFields.VLAN_VID), 333);
		storage.updateRow(StaticEntryPusher.TABLE_NAME, TestRule3);
		assertEquals(2, pusher.countEntries());
		assertEquals(1, writeCaptureList.getValues().size());

		outList = writeCaptureList.getValues().get(0);
		assertEquals(2, outList.size());
		OFFlowMod removeFlowMod = (OFFlowMod) outList.get(0);
		FlowMod3 = FlowModUtils.toFlowDeleteStrict(FlowMod3);
		verifyFlowMod(removeFlowMod, FlowMod3);
		FlowMod3 = FlowModUtils.toFlowAdd(FlowMod3);
		FlowMod3 = FlowMod3.createBuilder().setMatch(MatchUtils.fromString("eth_dst=00:20:30:40:50:60,eth_vlan_vid=333", factory.getVersion())).setXid(14).build();
		OFFlowMod updateFlowMod = (OFFlowMod) outList.get(1);
		verifyFlowMod(updateFlowMod, FlowMod3);
		writeCaptureList.reset();

		// now try an action modifying update, calling staticFlowPusher.rowUpdated()
		TestRule3.put(StaticEntryPusher.Columns.COLUMN_ACTIONS, "output=controller,pop_vlan"); // added pop-vlan
		storage.updateRow(StaticEntryPusher.TABLE_NAME, TestRule3);
		assertEquals(2, pusher.countEntries());
		assertEquals(1, writeCaptureList.getValues().size());

		outList = writeCaptureList.getValues().get(0);
		assertEquals(1, outList.size());
		OFFlowMod modifyFlowMod = (OFFlowMod) outList.get(0);
		FlowMod3 = FlowModUtils.toFlowModifyStrict(FlowMod3);
		List<OFAction> modifiedActions = FlowMod3.getActions();
		modifiedActions.add(factory.actions().popVlan()); // add the new action to what we should expect
		FlowMod3 = FlowMod3.createBuilder().setActions(modifiedActions).setXid(19).build();
		verifyFlowMod(modifyFlowMod, FlowMod3);
	}


	IStorageSourceService createStorageWithFlowEntries() {
		return populateStorageWithFlowEntries();
	}

	IStorageSourceService populateStorageWithFlowEntries() {
		Set<String> indexedColumns = new HashSet<String>();
		indexedColumns.add(Columns.COLUMN_NAME);
		storage.createTable(StaticEntryPusher.TABLE_NAME, indexedColumns);
		storage.setTablePrimaryKeyName(StaticEntryPusher.TABLE_NAME, Columns.COLUMN_NAME);

		storage.insertRow(StaticEntryPusher.TABLE_NAME, TestRule1);
		storage.insertRow(StaticEntryPusher.TABLE_NAME, TestRule2);
		storage.insertRow(StaticEntryPusher.TABLE_NAME, TestRule3);

		return storage;
	}

	@Test
	public void testHARoleChanged() throws IOException {

		assert(pusher.entry2dpid.containsValue(TestSwitch1DPID));
		assert(pusher.entriesFromStorage.containsValue(FlowMod1));
		assert(pusher.entriesFromStorage.containsValue(FlowMod2));
		assert(pusher.entriesFromStorage.containsValue(FlowMod3));

		/* FIXME: what's the right behavior here ??
        // Send a notification that we've changed to slave
        mfp.dispatchRoleChanged(Role.SLAVE);
        // Make sure we've removed all our entries
        assert(StaticEntryPusher.entry2dpid.isEmpty());
        assert(StaticEntryPusher.entriesFromStorage.isEmpty());

        // Send a notification that we've changed to master
        mfp.dispatchRoleChanged(Role.MASTER);
        // Make sure we've learned the entries
        assert(StaticEntryPusher.entry2dpid.containsValue(TestSwitch1DPID));
        assert(StaticEntryPusher.entriesFromStorage.containsValue(FlowMod1));
        assert(StaticEntryPusher.entriesFromStorage.containsValue(FlowMod2));
        assert(StaticEntryPusher.entriesFromStorage.containsValue(FlowMod3));
		 */
	}
}
