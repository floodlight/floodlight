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

package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.anyObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IShutdownListener;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.NullConnection;
import net.floodlightcontroller.core.OFSwitch;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.debugevent.DebugEventService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortFeatures;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFPort;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFSwitchManagerTest{
    private Controller controller;
    private OFSwitchManager switchManager;

    // FIXME:LOJI: For now just work with OF 1.0
    private final OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);
    private static DatapathId DATAPATH_ID_0 = DatapathId.of(0);
    private static DatapathId DATAPATH_ID_1 = DatapathId.of(1);

    @Before
    public void setUp() throws Exception {
        doSetUp(HARole.ACTIVE);
    }

    public void doSetUp(HARole role) throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();

        FloodlightProvider cm = new FloodlightProvider();
        fmc.addConfigParam(cm, "role", role.toString());
        controller = (Controller)cm.getServiceImpls().get(IFloodlightProviderService.class);
        fmc.addService(IFloodlightProviderService.class, controller);

        MemoryStorageSource memstorage = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, memstorage);
        
        RestApiServer restApi = new RestApiServer();
        fmc.addService(IRestApiService.class, restApi);
        
        ThreadPool threadPool = new ThreadPool();
        fmc.addService(IThreadPoolService.class, threadPool);
        
        // TODO: should mock IDebugCounterService and make sure
        // the expected counters are updated.
        MockDebugCounterService debugCounterService = new MockDebugCounterService();
        fmc.addService(IDebugCounterService.class, debugCounterService);

        DebugEventService debugEventService = new DebugEventService();
        fmc.addService(IDebugEventService.class, debugEventService);

        switchManager = new OFSwitchManager();
        fmc.addService(IOFSwitchService.class, switchManager);

        MockSyncService syncService = new MockSyncService();
        fmc.addService(ISyncService.class, syncService);

        IShutdownService shutdownService = createMock(IShutdownService.class);
        shutdownService.registerShutdownListener(anyObject(IShutdownListener.class));
        expectLastCall().anyTimes();
        replay(shutdownService);
        fmc.addService(IShutdownService.class, shutdownService);
        verify(shutdownService);

        threadPool.init(fmc);
        syncService.init(fmc);
        switchManager.init(fmc);
        debugCounterService.init(fmc);
        memstorage.init(fmc);
        debugEventService.init(fmc);
        restApi.init(fmc);
        cm.init(fmc);

        syncService.init(fmc);
        switchManager.startUpBase(fmc);
        debugCounterService.startUp(fmc);
        memstorage.startUp(fmc);
        debugEventService.startUp(fmc);
        threadPool.startUp(fmc);
        restApi.startUp(fmc);
        cm.startUp(fmc);
    }

    @After
    public void tearDown(){

    }

    public Controller getController() {
        return controller;
    }

    private static SwitchDescription createSwitchDescription() {
        return new SwitchDescription();
    }

    private OFFeaturesReply createOFFeaturesReply(DatapathId datapathId) {
        OFFeaturesReply fr = factory.buildFeaturesReply()
                .setXid(0)
                .setDatapathId(datapathId)
                .setPorts(ImmutableList.<OFPortDesc>of())
                .build();
        return fr;
    }


    /** Set the mock expectations for sw when sw is passed to addSwitch
     * The same expectations can be used when a new SwitchSyncRepresentation
     * is created from the given mocked switch */
    protected void setupSwitchForAddSwitch(IOFSwitch sw, DatapathId datapathId,
            SwitchDescription description, OFFeaturesReply featuresReply) {
        if (description == null) {
            description = createSwitchDescription();
        }
        if (featuresReply == null) {
            featuresReply = createOFFeaturesReply(datapathId);
        }
        List<OFPortDesc> ports = featuresReply.getPorts();

        expect(sw.getOFFactory()).andReturn(OFFactories.getFactory(OFVersion.OF_10)).anyTimes();
        expect(sw.getStatus()).andReturn(SwitchStatus.MASTER).anyTimes();
        expect(sw.getId()).andReturn(datapathId).anyTimes();
        expect(sw.getSwitchDescription()).andReturn(description).anyTimes();
        expect(sw.getBuffers())
                .andReturn(featuresReply.getNBuffers()).anyTimes();
        expect(sw.getTables())
                .andReturn(featuresReply.getNTables()).anyTimes();
        expect(sw.getCapabilities())
                .andReturn(featuresReply.getCapabilities()).anyTimes();
        expect(sw.getActions())
                .andReturn(featuresReply.getActions()).anyTimes();
        expect(sw.getPorts())
                .andReturn(ports).anyTimes();
        expect(sw.attributeEquals(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true))
                .andReturn(false).anyTimes();
        expect(sw.getInetAddress()).andReturn(null).anyTimes();
    }

    @Test
    /**
     * Test switchActivated for a new switch, i.e., a switch that was not
     * previously known to the controller cluser. We expect that all
     * flow mods are cleared and we expect a switchAdded
     */
    public void testNewSwitchActivated() throws Exception {

        IOFSwitchBackend sw = createMock(IOFSwitchBackend.class);
        setupSwitchForAddSwitch(sw, DATAPATH_ID_0, null, null);

        // Ensure switch doesn't already exist
        assertNull(switchManager.getSwitch(DATAPATH_ID_0));

        // strict mock. Order of events matters!
        IOFSwitchListener listener = createStrictMock(IOFSwitchListener.class);
        listener.switchAdded(DATAPATH_ID_0);
        expectLastCall().once();
        listener.switchActivated(DATAPATH_ID_0);
        expectLastCall().once();
        replay(listener);
        switchManager.addOFSwitchListener(listener);
        replay(sw);
        switchManager.switchAdded(sw);
        switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
        verify(sw);

        assertEquals(sw, switchManager.getSwitch(DATAPATH_ID_0));
        controller.processUpdateQueueForTesting();
        verify(listener);
    }

    /**
     * Test switchActivated for a new switch while in slave: disconnect the switch
     */
    @Test
    public void testNewSwitchActivatedWhileSlave() throws Exception {
        doSetUp(HARole.STANDBY);
        IOFSwitchBackend sw = createMock(IOFSwitchBackend.class);

        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        switchManager.addOFSwitchListener(listener);

        expect(sw.getId()).andReturn(DATAPATH_ID_0).anyTimes();
        expect(sw.getStatus()).andReturn(SwitchStatus.MASTER).anyTimes();
        sw.disconnect();
        expectLastCall().once();
        expect(sw.getOFFactory()).andReturn(factory).once();
        replay(sw, listener); // nothing recorded
        switchManager.switchAdded(sw);
        switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
        verify(sw);
        controller.processUpdateQueueForTesting();
        verify(listener);
    }


    /**
     * Create and activate a switch, either completely new or reconnected
     * The mocked switch instance will be returned. It will be reset.
     */
    private IOFSwitchBackend doActivateSwitchInt(DatapathId datapathId,
                                          SwitchDescription description,
                                          OFFeaturesReply featuresReply,
                                          boolean clearFlows)
                                          throws Exception {

        IOFSwitchBackend sw = createMock(IOFSwitchBackend.class);
        if (featuresReply == null) {
            featuresReply = createOFFeaturesReply(datapathId);
        }
        if (description == null) {
            description = createSwitchDescription();
        }
        setupSwitchForAddSwitch(sw, datapathId, description, featuresReply);
        replay(sw);
        switchManager.switchAdded(sw);
        switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
        verify(sw);
        assertEquals(sw, switchManager.getSwitch(datapathId));
        // drain updates and ignore
        controller.processUpdateQueueForTesting();

        reset(sw);
        return sw;
    }

    /**
     * Create and activate a new switch with the given dpid, features reply
     * and description. If description and/or features reply are null we'll
     * allocate the default one
     * The mocked switch instance will be returned. It wil be reset.
     */
    private IOFSwitchBackend doActivateNewSwitch(DatapathId dpid,
                                          SwitchDescription description,
                                          OFFeaturesReply featuresReply)
                                          throws Exception {
        return doActivateSwitchInt(dpid, description, featuresReply, true);
    }

    /**
     * Remove a nonexisting switch. should be ignored
     */
    @Test
    public void testNonexistingSwitchDisconnected() throws Exception {
        IOFSwitchBackend sw = createMock(IOFSwitchBackend.class);
        expect(sw.getId()).andReturn(DATAPATH_ID_1).anyTimes();
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        switchManager.addOFSwitchListener(listener);
        replay(sw, listener);
        switchManager.switchDisconnected(sw);
        controller.processUpdateQueueForTesting();
        verify(sw, listener);

        assertNull(switchManager.getSwitch(DATAPATH_ID_1));
    }

    /**
     * Try to remove a switch that's different from what's in the active
     * switch map. Should be ignored
     */
    @Test
    public void testSwitchDisconnectedOther() throws Exception {
        IOFSwitch origSw = doActivateNewSwitch(DATAPATH_ID_1, null, null);
        // create a new mock switch
        IOFSwitchBackend sw = createMock(IOFSwitchBackend.class);
        expect(sw.getId()).andReturn(DATAPATH_ID_1).anyTimes();
        IOFSwitchListener listener = createMock(IOFSwitchListener.class);
        switchManager.addOFSwitchListener(listener);
        replay(sw, listener);
        switchManager.switchDisconnected(sw);
        controller.processUpdateQueueForTesting();
        verify(sw, listener);

        expect(origSw.getStatus()).andReturn(SwitchStatus.MASTER).anyTimes();
        replay(origSw);
        assertSame(origSw, switchManager.getSwitch(DATAPATH_ID_1));
    }



    /**
     * Try to activate a switch that's already active (which can happen if
     * two different switches have the same DPIP or if a switch reconnects
     * while the old TCP connection is still alive
     */
    @Test
    public void testSwitchActivatedWithAlreadyActiveSwitch() throws Exception {
        SwitchDescription oldDescription = new SwitchDescription(
                "", "", "", "", "Ye Olde Switch");
        SwitchDescription newDescription = new SwitchDescription(
                "", "", "", "", "The new Switch");
        OFFeaturesReply featuresReply = createOFFeaturesReply(DATAPATH_ID_0);


        // Setup: add a switch to the controller
        IOFSwitchBackend oldsw = createMock(IOFSwitchBackend.class);
        setupSwitchForAddSwitch(oldsw, DATAPATH_ID_0, oldDescription, featuresReply);
        replay(oldsw);
        switchManager.switchAdded(oldsw);
        switchManager.switchStatusChanged(oldsw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
        verify(oldsw);
        // drain the queue, we don't care what's in it
        controller.processUpdateQueueForTesting();
        assertEquals(oldsw, switchManager.getSwitch(DATAPATH_ID_0));

        // Now the actual test: add a new switch with the same dpid to
        // the controller
        reset(oldsw);
        expect(oldsw.getId()).andReturn(DATAPATH_ID_0).anyTimes();
        oldsw.cancelAllPendingRequests();
        expectLastCall().once();
        oldsw.disconnect();
        expectLastCall().once();


        IOFSwitchBackend newsw = createMock(IOFSwitchBackend.class);
        setupSwitchForAddSwitch(newsw, DATAPATH_ID_0, newDescription, featuresReply);

        // Strict mock. We need to get the removed notification before the
        // add notification
        IOFSwitchListener listener = createStrictMock(IOFSwitchListener.class);
        listener.switchRemoved(DATAPATH_ID_0);
        listener.switchAdded(DATAPATH_ID_0);
        listener.switchActivated(DATAPATH_ID_0);
        replay(listener);
        switchManager.addOFSwitchListener(listener);


        replay(newsw, oldsw);
        switchManager.switchAdded(newsw);
        switchManager.switchStatusChanged(newsw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
        verify(newsw, oldsw);

        assertEquals(newsw, switchManager.getSwitch(DATAPATH_ID_0));
        controller.processUpdateQueueForTesting();
        verify(listener);
    }



    /**
    * Tests that you can't remove a switch from the map returned by
    * getSwitches() (because getSwitches should return an unmodifiable
    * map)
    */
   @Test
   public void testRemoveActiveSwitch() {
       IOFSwitchBackend sw = createNiceMock(IOFSwitchBackend.class);
       setupSwitchForAddSwitch(sw, DATAPATH_ID_1, null, null);
       replay(sw);
       switchManager.switchAdded(sw);
       switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
       assertEquals(sw, switchManager.getSwitch(DATAPATH_ID_1));
       try {
           switchManager.getAllSwitchMap().remove(DATAPATH_ID_1);
           fail("Expected: UnsupportedOperationException");
       } catch(UnsupportedOperationException e) {
           // expected
       }
       // we don't care for updates. drain queue.
       controller.processUpdateQueueForTesting();
   }

   /**
    * Tests that the switch manager should only return a switch to a getActiveSwitch
    * call when the switch is visible/active.
    */
   @Test
   public void testGetActiveSwitch() {
       MockOFConnection connection = new MockOFConnection(DATAPATH_ID_1, OFAuxId.MAIN);
       IOFSwitchBackend sw = new MockOFSwitchImpl(connection);
       sw.setStatus(SwitchStatus.HANDSHAKE);

       assertNull(switchManager.getActiveSwitch(DATAPATH_ID_1));
       switchManager.switchAdded(sw);
       assertNull(switchManager.getActiveSwitch(DATAPATH_ID_1));
       sw.setStatus(SwitchStatus.MASTER);
       assertEquals(sw, switchManager.getActiveSwitch(DATAPATH_ID_1));
       sw.setStatus(SwitchStatus.QUARANTINED);
       assertNull(switchManager.getActiveSwitch(DATAPATH_ID_1));
       sw.setStatus(SwitchStatus.SLAVE);
       assertEquals(sw, switchManager.getActiveSwitch(DATAPATH_ID_1));
       sw.setStatus(SwitchStatus.DISCONNECTED);
       assertNull(switchManager.getActiveSwitch(DATAPATH_ID_1));
       // we don't care for updates. drain queue.
       controller.processUpdateQueueForTesting();
   }

   /**
    * Test that notifyPortChanged() results in an IOFSwitchListener
    * update and that its arguments are passed through to
    * the listener call
    */
   @Test
   public void testNotifySwitchPortChanged() throws Exception {
       DatapathId dpid = DatapathId.of(42);

       OFPortDesc p1 = factory.buildPortDesc()
               .setName("Port1")
               .setPortNo(OFPort.of(1))
               .build();
       OFFeaturesReply fr1 = factory.buildFeaturesReply()
               .setXid(0)
               .setDatapathId(dpid)
               .setPorts(ImmutableList.<OFPortDesc>of(p1))
               .build();

       OFPortDesc p2 = factory.buildPortDesc()
               .setName("Port1")
               .setPortNo(OFPort.of(1))
               .setAdvertised(ImmutableSet.<OFPortFeatures>of(OFPortFeatures.PF_100MB_FD))
               .build();
       OFFeaturesReply fr2 = factory.buildFeaturesReply()
               .setXid(0)
               .setDatapathId(dpid)
               .setPorts(ImmutableList.<OFPortDesc>of(p2))
               .build();

       SwitchDescription desc = createSwitchDescription();

       // activate switch
       IOFSwitchBackend sw = doActivateNewSwitch(dpid, desc, fr1);

       IOFSwitchListener listener = createMock(IOFSwitchListener.class);
       switchManager.addOFSwitchListener(listener);
       // setup switch with the new, second features reply (and thus ports)
       setupSwitchForAddSwitch(sw, dpid, desc, fr2);
       listener.switchPortChanged(dpid, p2,
                                  PortChangeType.OTHER_UPDATE);
       expectLastCall().once();
       replay(listener);
       replay(sw);
       switchManager.notifyPortChanged(sw, p2,
                                    PortChangeType.OTHER_UPDATE);
       controller.processUpdateQueueForTesting();
       verify(listener);
       verify(sw);

   }

    /**
     * Test the driver registry: test the bind order
     */
    @Test
    public void testSwitchDriverRegistryBindOrder() {
        IOFSwitchDriver driver1 = createMock(IOFSwitchDriver.class);
        IOFSwitchDriver driver2 = createMock(IOFSwitchDriver.class);
        IOFSwitchDriver driver3 = createMock(IOFSwitchDriver.class);
        IOFSwitchBackend returnedSwitch = null;
        IOFSwitchBackend mockSwitch = createMock(IOFSwitchBackend.class);
        switchManager.addOFSwitchDriver("", driver3);
        switchManager.addOFSwitchDriver("test switch", driver1);
        switchManager.addOFSwitchDriver("test", driver2);

        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);

        SwitchDescription description = new SwitchDescription(
                "test switch", "version 0.9", "", "", "");
        reset(driver1);
        reset(driver2);
        reset(driver3);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(description);
        expectLastCall().once();
        OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);
        expect(driver1.getOFSwitchImpl(description, factory)).andReturn(mockSwitch).once();
        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);
        returnedSwitch = switchManager.getOFSwitchInstance(new NullConnection(), description, factory, DatapathId.of(1));
        assertSame(mockSwitch, returnedSwitch);
        verify(driver1);
        verify(driver2);
        verify(driver3);
        verify(mockSwitch);

        description = new SwitchDescription(
                "testFooBar", "version 0.9", "", "", "");
        reset(driver1);
        reset(driver2);
        reset(driver3);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(description);
        expectLastCall().once();
        expect(driver2.getOFSwitchImpl(description, factory)).andReturn(mockSwitch).once();
        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);
        returnedSwitch = switchManager.getOFSwitchInstance(new NullConnection(), description,
                OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
        assertSame(mockSwitch, returnedSwitch);
        verify(driver1);
        verify(driver2);
        verify(driver3);
        verify(mockSwitch);

        description = new SwitchDescription(
                "FooBar", "version 0.9", "", "", "");
        reset(driver1);
        reset(driver2);
        reset(driver3);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(description);
        expectLastCall().once();
        expect(driver3.getOFSwitchImpl(description, factory)).andReturn(mockSwitch).once();
        replay(driver1);
        replay(driver2);
        replay(driver3);
        replay(mockSwitch);
        returnedSwitch = switchManager.getOFSwitchInstance(new NullConnection(), description, factory, DatapathId.of(1));
        assertSame(mockSwitch, returnedSwitch);
        verify(driver1);
        verify(driver2);
        verify(driver3);
        verify(mockSwitch);
    }

    /**
     * Test SwitchDriverRegistry
     * Test fallback to default if no switch driver is registered for a
     * particular prefix
     */
    @Test
    public void testSwitchDriverRegistryNoDriver() {
        IOFSwitchDriver driver = createMock(IOFSwitchDriver.class);
        IOFSwitch returnedSwitch = null;
        IOFSwitchBackend mockSwitch = createMock(IOFSwitchBackend.class);
        switchManager.addOFSwitchDriver("test switch", driver);

        replay(driver);
        replay(mockSwitch);

        SwitchDescription desc = new SwitchDescription("test switch", "version 0.9", "", "", "");
        reset(driver);
        reset(mockSwitch);
        mockSwitch.setSwitchProperties(desc);
        expectLastCall().once();
        expect(driver.getOFSwitchImpl(desc, factory)).andReturn(mockSwitch).once();
        replay(driver);
        replay(mockSwitch);
        returnedSwitch = switchManager.getOFSwitchInstance(new NullConnection(), desc, factory, DatapathId.of(1));
        assertSame(mockSwitch, returnedSwitch);
        verify(driver);
        verify(mockSwitch);

        desc = new SwitchDescription("Foo Bar test switch", "version 0.9", "", "", "");
        reset(driver);
        reset(mockSwitch);
        replay(driver);
        replay(mockSwitch);
        returnedSwitch = switchManager.getOFSwitchInstance(new NullConnection(), desc,
                OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
        assertNotNull(returnedSwitch);
        assertTrue("Returned switch should be OFSwitch",
                   returnedSwitch instanceof OFSwitch);
        assertEquals(desc, returnedSwitch.getSwitchDescription());
        verify(driver);
        verify(mockSwitch);
    }

    /**
     *
     */
    @Test
    public void testDriverRegistryExceptions() {
        IOFSwitchDriver driver = createMock(IOFSwitchDriver.class);
        IOFSwitchDriver driver2 = createMock(IOFSwitchDriver.class);
        replay(driver, driver2); // no calls expected on driver

        //---------------
        // Test exception handling when registering driver
        try {
            switchManager.addOFSwitchDriver("foobar", null);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }

        try {
            switchManager.addOFSwitchDriver(null, driver);
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }

        // test that we can register each prefix only once!
        switchManager.addOFSwitchDriver("foobar",  driver);
        try {
            switchManager.addOFSwitchDriver("foobar",  driver);
            fail("Expected IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            //expected
        }

        try {
            switchManager.addOFSwitchDriver("foobar",  driver2);
            fail("Expected IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            //expected
        }

        //OFDescStatsReply desc = createOFDescStatsReply();
        //desc.setDatapathDescription(null);
        SwitchDescription description = new SwitchDescription(null, "", "", "", "");
        try {
            switchManager.getOFSwitchInstance(null, description,
                    OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        description = new SwitchDescription("", null, "", "", "");
        try {
            switchManager.getOFSwitchInstance(null, description,
                    OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        description = new SwitchDescription("", "", null, "", "");
        try {
            switchManager.getOFSwitchInstance(null, description,
                    OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        description = new SwitchDescription("", "", "", null, "");
        try {
            switchManager.getOFSwitchInstance(null, description,
                    OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected
        }
        description = new SwitchDescription("", "", "", "", null);
        try {
            switchManager.getOFSwitchInstance(null, description,
                    OFFactories.getFactory(OFVersion.OF_10), DatapathId.of(1));
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            //expected

        }
        verify(driver, driver2);
    }

    @Test
    public void testRegisterCategory() {

        // Must be in INIT state
        Timer timer = createMock(Timer.class);
        replay(timer);
        switchManager = new OFSwitchManager();
        switchManager.loadLogicalCategories();
        assertTrue("Connections should be empty", switchManager.getNumRequiredConnections() == 0);

        // Add initial category
        switchManager = new OFSwitchManager();
        LogicalOFMessageCategory category = new LogicalOFMessageCategory("aux1", 1);
        switchManager.registerLogicalOFMessageCategory(category);
        switchManager.loadLogicalCategories();

        assertTrue("Required connections should be 1", switchManager.getNumRequiredConnections() == 1);

        // Multiple categories on the same auxId should produce one required connection
        switchManager = new OFSwitchManager();
        switchManager.registerLogicalOFMessageCategory(new LogicalOFMessageCategory("aux1", 1));
        switchManager.registerLogicalOFMessageCategory(new LogicalOFMessageCategory("aux1-2", 1));
        switchManager.loadLogicalCategories();

        assertTrue("Required connections should be 1", switchManager.getNumRequiredConnections() == 1);

        // Adding a category on a different aux ID should increase the required connection count
        switchManager = new OFSwitchManager();
        switchManager.registerLogicalOFMessageCategory(new LogicalOFMessageCategory("aux1", 1));
        switchManager.registerLogicalOFMessageCategory(new LogicalOFMessageCategory("aux2", 2));
        switchManager.loadLogicalCategories();
        assertTrue("Required connections should be 2", switchManager.getNumRequiredConnections() == 2);
    }

    @Test
    public void testRegisterCategoryException() {

        switchManager = new OFSwitchManager();
        switchManager.loadLogicalCategories();
        LogicalOFMessageCategory category = new LogicalOFMessageCategory("test", 1);

        // Wrong State
        try {
            switchManager.registerLogicalOFMessageCategory(category);
            fail("Expected Unsupported Operation Exception not thrown");
        } catch (UnsupportedOperationException e) { /* expected */ }

        switchManager = new OFSwitchManager();

        // Categories must have category with auxid of 1
        LogicalOFMessageCategory bad = new LogicalOFMessageCategory("bad", 2);
        switchManager.registerLogicalOFMessageCategory(bad);

        try{
            switchManager.loadLogicalCategories();
            fail("Expected exception not thrown");
        } catch (IllegalStateException e) { /* expected */}

        // Non contiguous category auxids of (1,3)
        switchManager = new OFSwitchManager();
        switchManager.registerLogicalOFMessageCategory(category);
        LogicalOFMessageCategory nonContiguous = new LogicalOFMessageCategory("bad", 3);
        switchManager.registerLogicalOFMessageCategory(nonContiguous);

        try{
            switchManager.loadLogicalCategories();
            fail("Expected exception not thrown");
        } catch (IllegalStateException e) { /* expected */}
    }

    @Test
    public void testNewConnectionOpened() {
        MockOFConnection connection = new MockOFConnection(DATAPATH_ID_1, OFAuxId.MAIN);
        OFFeaturesReply featuresReply = createOFFeaturesReply(DATAPATH_ID_1);

        // Assert no switch handlers
        assertTrue(switchManager.getSwitchHandshakeHandlers().isEmpty());
        switchManager.connectionOpened(connection, featuresReply);
        // Ensure a
        assertTrue(switchManager.getSwitchHandshakeHandlers().size() == 1);
        assertTrue(switchManager.getSwitchHandshakeHandlers().get(0).getDpid().equals(DATAPATH_ID_1));
    }

    @Test
    public void testDuplicateConnectionOpened() {
        // Seed with 1 connection and handler
        testNewConnectionOpened();

        MockOFConnection connection = new MockOFConnection(DATAPATH_ID_1, OFAuxId.MAIN);
        OFFeaturesReply featuresReply = createOFFeaturesReply(DATAPATH_ID_1);

        switchManager.connectionOpened(connection, featuresReply);

        // Ensure duplicate connections are
        assertTrue(switchManager.getSwitchHandshakeHandlers().size() == 1);
        assertTrue(switchManager.getSwitchHandshakeHandlers().get(0).getDpid().equals(DATAPATH_ID_1));
    }

    @Test
    public void testHandshakeDisconnected() {
        // Seed with 1 connection and handler
        testNewConnectionOpened();

        assertTrue(switchManager.getSwitchHandshakeHandlers().size() == 1);
        // Disconnect wrong handshake
        switchManager.handshakeDisconnected(DATAPATH_ID_0);
        assertTrue(switchManager.getSwitchHandshakeHandlers().size() == 1);
        // Disconnect correct handshake
        switchManager.handshakeDisconnected(DATAPATH_ID_1);
        assertTrue(switchManager.getSwitchHandshakeHandlers().size() == 0);

    }
}
