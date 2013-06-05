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

package net.floodlightcontroller.staticflowentry;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.util.HexString;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import static net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher.*;
import static org.easymock.EasyMock.*;

public class StaticFlowTests extends FloodlightTestCase {

    static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";
    static int TotalTestRules = 3;

    /***
     * Create TestRuleXXX and the corresponding FlowModXXX
     * for X = 1..3
     */
    static Map<String,Object> TestRule1;
    static OFFlowMod FlowMod1;
    static {
        FlowMod1 = new OFFlowMod();
        TestRule1 = new HashMap<String,Object>();
        TestRule1.put(COLUMN_NAME, "TestRule1");
        TestRule1.put(COLUMN_SWITCH, TestSwitch1DPID);
        // setup match
        OFMatch match = new OFMatch();
        TestRule1.put(COLUMN_DL_DST, "00:20:30:40:50:60");
        match.fromString("dl_dst=00:20:30:40:50:60");
        // setup actions
        List<OFAction> actions = new LinkedList<OFAction>();
        TestRule1.put(COLUMN_ACTIONS, "output=1");
        actions.add(new OFActionOutput((short)1, Short.MAX_VALUE));
        // done
        FlowMod1.setMatch(match);
        FlowMod1.setActions(actions);
        FlowMod1.setBufferId(-1);
        FlowMod1.setOutPort(OFPort.OFPP_NONE.getValue());
        FlowMod1.setPriority(Short.MAX_VALUE);
        FlowMod1.setLengthU(OFFlowMod.MINIMUM_LENGTH + 8);  // 8 bytes of actions
    }

    static Map<String,Object> TestRule2;
    static OFFlowMod FlowMod2;

    static {
        FlowMod2 = new OFFlowMod();
        TestRule2 = new HashMap<String,Object>();
        TestRule2.put(COLUMN_NAME, "TestRule2");
        TestRule2.put(COLUMN_SWITCH, TestSwitch1DPID);
        // setup match
        OFMatch match = new OFMatch();
        TestRule2.put(COLUMN_NW_DST, "192.168.1.0/24");
        match.fromString("nw_dst=192.168.1.0/24");
        // setup actions
        List<OFAction> actions = new LinkedList<OFAction>();
        TestRule2.put(COLUMN_ACTIONS, "output=1");
        actions.add(new OFActionOutput((short)1, Short.MAX_VALUE));
        // done
        FlowMod2.setMatch(match);
        FlowMod2.setActions(actions);
        FlowMod2.setBufferId(-1);
        FlowMod2.setOutPort(OFPort.OFPP_NONE.getValue());
        FlowMod2.setPriority(Short.MAX_VALUE);
        FlowMod2.setLengthU(OFFlowMod.MINIMUM_LENGTH + 8);  // 8 bytes of actions

    }


    static Map<String,Object> TestRule3;
    static OFFlowMod FlowMod3;
    private StaticFlowEntryPusher staticFlowEntryPusher;
    private IOFSwitch mockSwitch;
    private Capture<OFMessage> writeCapture;
    private Capture<FloodlightContext> contextCapture;
    private Capture<List<OFMessage>> writeCaptureList;
    private long dpid;
    private IStorageSourceService storage;
    static {
        FlowMod3 = new OFFlowMod();
        TestRule3 = new HashMap<String,Object>();
        TestRule3.put(COLUMN_NAME, "TestRule3");
        TestRule3.put(COLUMN_SWITCH, TestSwitch1DPID);
        // setup match
        OFMatch match = new OFMatch();
        TestRule3.put(COLUMN_DL_DST, "00:20:30:40:50:60");
        TestRule3.put(COLUMN_DL_VLAN, 4096);
        match.fromString("dl_dst=00:20:30:40:50:60,dl_vlan=4096");
        // setup actions
        TestRule3.put(COLUMN_ACTIONS, "output=controller");
        List<OFAction> actions = new LinkedList<OFAction>();
        actions.add(new OFActionOutput(OFPort.OFPP_CONTROLLER.getValue(), Short.MAX_VALUE));
        // done
        FlowMod3.setMatch(match);
        FlowMod3.setActions(actions);
        FlowMod3.setBufferId(-1);
        FlowMod3.setOutPort(OFPort.OFPP_NONE.getValue());
        FlowMod3.setPriority(Short.MAX_VALUE);
        FlowMod3.setLengthU(OFFlowMod.MINIMUM_LENGTH + 8);  // 8 bytes of actions

    }

    private void verifyFlowMod(OFFlowMod testFlowMod,
            OFFlowMod goodFlowMod) {
        verifyMatch(testFlowMod, goodFlowMod);
        verifyActions(testFlowMod, goodFlowMod);
        // dont' bother testing the cookie; just copy it over
        goodFlowMod.setCookie(testFlowMod.getCookie());
        // .. so we can continue to use .equals()
        assertEquals(goodFlowMod, testFlowMod);
    }


    private void verifyMatch(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
        assertEquals(goodFlowMod.getMatch(), testFlowMod.getMatch());
    }


    private void verifyActions(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
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


    @Override
    public void setUp() throws Exception {
        super.setUp();
        staticFlowEntryPusher = new StaticFlowEntryPusher();
        storage = createStorageWithFlowEntries();
        dpid = HexString.toLong(TestSwitch1DPID);

        mockSwitch = createNiceMock(IOFSwitch.class);
        writeCapture = new Capture<OFMessage>(CaptureType.ALL);
        contextCapture = new Capture<FloodlightContext>(CaptureType.ALL);
        writeCaptureList = new Capture<List<OFMessage>>(CaptureType.ALL);

        //OFMessageSafeOutStream mockOutStream = createNiceMock(OFMessageSafeOutStream.class);
        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.write(capture(writeCaptureList), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.flush();
        expectLastCall().anyTimes();


        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IStorageSourceService.class, storage);

        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
        switchMap.put(dpid, mockSwitch);
        // NO ! expect(mockFloodlightProvider.getSwitches()).andReturn(switchMap).anyTimes();
        mockFloodlightProvider.setSwitches(switchMap);
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        RestApiServer restApi = new RestApiServer();
        fmc.addService(IRestApiService.class, restApi);
        restApi.init(fmc);
        staticFlowEntryPusher.init(fmc);
        staticFlowEntryPusher.startUp(fmc);    // again, to hack unittest
    }

    @Test
    public void testStaticFlowPush() throws Exception {

        // verify that flowpusher read all three entries from storage
        assertEquals(TotalTestRules, staticFlowEntryPusher.countEntries());

        // if someone calls mockSwitch.getOutputStream(), return mockOutStream instead
        //expect(mockSwitch.getOutputStream()).andReturn(mockOutStream).anyTimes();

        // if someone calls getId(), return this dpid instead
        expect(mockSwitch.getId()).andReturn(dpid).anyTimes();
        expect(mockSwitch.getStringId()).andReturn(TestSwitch1DPID).anyTimes();
        replay(mockSwitch);

        // hook the static pusher up to the fake switch
        staticFlowEntryPusher.switchAdded(dpid);

        verify(mockSwitch);

        // Verify that the switch has gotten some flow_mods
        assertEquals(true, writeCapture.hasCaptured());
        assertEquals(TotalTestRules, writeCapture.getValues().size());

        // Order assumes how things are stored in hash bucket;
        // should be fixed because OFMessage.hashCode() is deterministic
        OFFlowMod firstFlowMod = (OFFlowMod) writeCapture.getValues().get(2);
        verifyFlowMod(firstFlowMod, FlowMod1);
        OFFlowMod secondFlowMod = (OFFlowMod) writeCapture.getValues().get(1);
        verifyFlowMod(secondFlowMod, FlowMod2);
        OFFlowMod thirdFlowMod = (OFFlowMod) writeCapture.getValues().get(0);
        verifyFlowMod(thirdFlowMod, FlowMod3);

        writeCapture.reset();
        contextCapture.reset();


        // delete two rules and verify they've been removed
        // this should invoke staticFlowPusher.rowsDeleted()
        storage.deleteRow(StaticFlowEntryPusher.TABLE_NAME, "TestRule1");
        storage.deleteRow(StaticFlowEntryPusher.TABLE_NAME, "TestRule2");

        assertEquals(1, staticFlowEntryPusher.countEntries());
        assertEquals(2, writeCapture.getValues().size());

        OFFlowMod firstDelete = (OFFlowMod) writeCapture.getValues().get(0);
        FlowMod1.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
        verifyFlowMod(firstDelete, FlowMod1);

        OFFlowMod secondDelete = (OFFlowMod) writeCapture.getValues().get(1);
        FlowMod2.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
        verifyFlowMod(secondDelete, FlowMod2);

        // add rules back to make sure that staticFlowPusher.rowsInserted() works
        writeCapture.reset();
        FlowMod2.setCommand(OFFlowMod.OFPFC_ADD);
        storage.insertRow(StaticFlowEntryPusher.TABLE_NAME, TestRule2);
        assertEquals(2, staticFlowEntryPusher.countEntries());
        assertEquals(1, writeCaptureList.getValues().size());
        List<OFMessage> outList =
            writeCaptureList.getValues().get(0);
        assertEquals(1, outList.size());
        OFFlowMod firstAdd = (OFFlowMod) outList.get(0);
        verifyFlowMod(firstAdd, FlowMod2);
        writeCapture.reset();
        contextCapture.reset();
        writeCaptureList.reset();

        // now try an overwriting update, calling staticFlowPusher.rowUpdated()
        TestRule3.put(COLUMN_DL_VLAN, 333);
        storage.updateRow(StaticFlowEntryPusher.TABLE_NAME, TestRule3);
        assertEquals(2, staticFlowEntryPusher.countEntries());
        assertEquals(1, writeCaptureList.getValues().size());

        outList = writeCaptureList.getValues().get(0);
        assertEquals(2, outList.size());
        OFFlowMod removeFlowMod = (OFFlowMod) outList.get(0);
        FlowMod3.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
        verifyFlowMod(removeFlowMod, FlowMod3);
        FlowMod3.setCommand(OFFlowMod.OFPFC_ADD);
        FlowMod3.getMatch().fromString("dl_dst=00:20:30:40:50:60,dl_vlan=333");
        OFFlowMod updateFlowMod = (OFFlowMod) outList.get(1);
        verifyFlowMod(updateFlowMod, FlowMod3);
        writeCaptureList.reset();

        // now try an action modifying update, calling staticFlowPusher.rowUpdated()
        TestRule3.put(COLUMN_ACTIONS, "output=controller,strip-vlan"); // added strip-vlan
        storage.updateRow(StaticFlowEntryPusher.TABLE_NAME, TestRule3);
        assertEquals(2, staticFlowEntryPusher.countEntries());
        assertEquals(1, writeCaptureList.getValues().size());

        outList = writeCaptureList.getValues().get(0);
        assertEquals(1, outList.size());
        OFFlowMod modifyFlowMod = (OFFlowMod) outList.get(0);
        FlowMod3.setCommand(OFFlowMod.OFPFC_MODIFY_STRICT);
        List<OFAction> modifiedActions = FlowMod3.getActions();
        modifiedActions.add(new OFActionStripVirtualLan()); // add the new action to what we should expect
        FlowMod3.setActions(modifiedActions);
        FlowMod3.setLengthU(OFFlowMod.MINIMUM_LENGTH + 16); // accommodate the addition of new actions
        verifyFlowMod(modifyFlowMod, FlowMod3);

    }


    IStorageSourceService createStorageWithFlowEntries() {
        return populateStorageWithFlowEntries(new MemoryStorageSource());
    }

    IStorageSourceService populateStorageWithFlowEntries(IStorageSourceService storage) {
        Set<String> indexedColumns = new HashSet<String>();
        indexedColumns.add(COLUMN_NAME);
        storage.createTable(StaticFlowEntryPusher.TABLE_NAME, indexedColumns);
        storage.setTablePrimaryKeyName(StaticFlowEntryPusher.TABLE_NAME, COLUMN_NAME);

        storage.insertRow(StaticFlowEntryPusher.TABLE_NAME, TestRule1);
        storage.insertRow(StaticFlowEntryPusher.TABLE_NAME, TestRule2);
        storage.insertRow(StaticFlowEntryPusher.TABLE_NAME, TestRule3);

        return storage;
    }

    @Test
    public void testHARoleChanged() throws IOException {

        assert(staticFlowEntryPusher.entry2dpid.containsValue(TestSwitch1DPID));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod1));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod2));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod3));

        /* FIXME: what's the right behavior here ??
        // Send a notification that we've changed to slave
        mfp.dispatchRoleChanged(Role.SLAVE);
        // Make sure we've removed all our entries
        assert(staticFlowEntryPusher.entry2dpid.isEmpty());
        assert(staticFlowEntryPusher.entriesFromStorage.isEmpty());

        // Send a notification that we've changed to master
        mfp.dispatchRoleChanged(Role.MASTER);
        // Make sure we've learned the entries
        assert(staticFlowEntryPusher.entry2dpid.containsValue(TestSwitch1DPID));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod1));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod2));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod3));
        */
    }
}
