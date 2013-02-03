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

import static org.easymock.EasyMock.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.FloodlightProvider;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFMessageFilterManagerService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFMessageFilterManager;
import net.floodlightcontroller.core.internal.Controller.IUpdate;
import net.floodlightcontroller.core.internal.Controller.SwitchUpdate;
import net.floodlightcontroller.core.internal.Controller.SwitchUpdateType;
import net.floodlightcontroller.core.internal.OFChannelState.HandshakeState;
import net.floodlightcontroller.core.internal.RoleChanger.PendingRoleRequestEntry;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.perfmon.PktInProcessingTime;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.memory.MemoryStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.jboss.netty.channel.Channel;
import org.junit.Test;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFError.OFErrorType;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class ControllerTest extends FloodlightTestCase
        implements IOFSwitchDriver {
   
    private Controller controller;
    private MockThreadPoolService tp;
    private boolean test_bind_order = false;
    private List<String> bind_order;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        
        FloodlightProvider cm = new FloodlightProvider();
        controller = (Controller)cm.getServiceImpls().get(IFloodlightProviderService.class);
        fmc.addService(IFloodlightProviderService.class, controller);
        
        MemoryStorageSource memstorage = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, memstorage);
        
        RestApiServer restApi = new RestApiServer();
        fmc.addService(IRestApiService.class, restApi);
        
        CounterStore cs = new CounterStore();
        fmc.addService(ICounterStoreService.class, cs);
        
        PktInProcessingTime ppt = new PktInProcessingTime();
        fmc.addService(IPktInProcessingTimeService.class, ppt);
        
        tp = new MockThreadPoolService();
        fmc.addService(IThreadPoolService.class, tp);
        
        ppt.init(fmc);
        restApi.init(fmc);
        memstorage.init(fmc);
        cm.init(fmc);
        tp.init(fmc);
        ppt.startUp(fmc);
        restApi.startUp(fmc);
        memstorage.startUp(fmc);
        cm.startUp(fmc);
        tp.startUp(fmc);
    }

    public Controller getController() {
        return controller;
    }

    protected OFStatisticsReply getStatisticsReply(int transactionId,
            int count, boolean moreReplies) {
        OFStatisticsReply sr = new OFStatisticsReply();
        sr.setXid(transactionId);
        sr.setStatisticType(OFStatisticsType.FLOW);
        List<OFStatistics> statistics = new ArrayList<OFStatistics>();
        for (int i = 0; i < count; ++i) {
            statistics.add(new OFFlowStatisticsReply());
        }
        sr.setStatistics(statistics);
        if (moreReplies)
            sr.setFlags((short) 1);
        return sr;
    }
    
    /* Set the mock expectations for sw when sw is passed to addSwitch */
    protected void setupSwitchForAddSwitch(IOFSwitch sw, long dpid) {
        String dpidString = HexString.toHexString(dpid);
                
        expect(sw.getId()).andReturn(dpid).anyTimes();
        expect(sw.getStringId()).andReturn(dpidString).anyTimes();
    }
    
    /**
     * Run the controller's main loop so that updates are processed
     */
    protected class ControllerRunThread extends Thread {
        @Override
        public void run() {
            controller.openFlowPort = 0; // Don't listen
            controller.run();
        }
    }

    /**
     * Verify that a listener that throws an exception halts further
     * execution, and verify that the Commands STOP and CONTINUE are honored.
     * @throws Exception
     */
    @Test
    public void testHandleMessages() throws Exception {
        Controller controller = getController();
        controller.removeOFMessageListeners(OFType.PACKET_IN);

        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        // Build our test packet
        IPacket testPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        byte[] testPacketSerialized = testPacket.serialize();

        // Build the PacketIn        
        OFPacketIn pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        IOFMessageListener test1 = createMock(IOFMessageListener.class);
        expect(test1.getName()).andReturn("test1").anyTimes();
        expect(test1.isCallbackOrderingPrereq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(test1.isCallbackOrderingPostreq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class))).andThrow(new RuntimeException("This is NOT an error! We are testing exception catching."));
        IOFMessageListener test2 = createMock(IOFMessageListener.class);
        expect(test2.getName()).andReturn("test2").anyTimes();
        expect(test2.isCallbackOrderingPrereq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(test2.isCallbackOrderingPostreq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        // expect no calls to test2.receive() since test1.receive() threw an exception

        replay(test1, test2, sw);
        controller.addOFMessageListener(OFType.PACKET_IN, test1);
        controller.addOFMessageListener(OFType.PACKET_IN, test2);
        try {
            controller.handleMessage(sw, pi, null);
        } catch (RuntimeException e) {
            assertEquals(e.getMessage().startsWith("This is NOT an error!"), true);
        }
        verify(test1, test2, sw);

        // verify STOP works
        reset(test1, test2, sw);
        expect(test1.receive(eq(sw), eq(pi), isA(FloodlightContext.class))).andReturn(Command.STOP);       
        expect(sw.getId()).andReturn(0L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        replay(test1, test2, sw);
        controller.handleMessage(sw, pi, null);
        verify(test1, test2, sw);
    }

    public class FutureFetcher<E> implements Runnable {
        public E value;
        public Future<E> future;

        public FutureFetcher(Future<E> future) {
            this.future = future;
        }

        @Override
        public void run() {
            try {
                value = future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @return the value
         */
        public E getValue() {
            return value;
        }

        /**
         * @return the future
         */
        public Future<E> getFuture() {
            return future;
        }
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    public void testOFStatisticsFuture() throws Exception {
        // Test for a single stats reply
        IOFSwitch sw = createMock(IOFSwitch.class);
        sw.cancelStatisticsReply(1);
        OFStatisticsFuture sf = new OFStatisticsFuture(tp, sw, 1);

        replay(sw);
        List<OFStatistics> stats;
        FutureFetcher<List<OFStatistics>> ff = new FutureFetcher<List<OFStatistics>>(sf);
        Thread t = new Thread(ff);
        t.start();
        sf.deliverFuture(sw, getStatisticsReply(1, 10, false));

        t.join();
        stats = ff.getValue();
        verify(sw);
        assertEquals(10, stats.size());

        // Test multiple stats replies
        reset(sw);
        sw.cancelStatisticsReply(1);

        sf = new OFStatisticsFuture(tp, sw, 1);

        replay(sw);
        ff = new FutureFetcher<List<OFStatistics>>(sf);
        t = new Thread(ff);
        t.start();
        sf.deliverFuture(sw, getStatisticsReply(1, 10, true));
        sf.deliverFuture(sw, getStatisticsReply(1, 5, false));
        t.join();

        stats = sf.get();
        verify(sw);
        assertEquals(15, stats.size());

        // Test cancellation
        reset(sw);
        sw.cancelStatisticsReply(1);
        sf = new OFStatisticsFuture(tp, sw, 1);

        replay(sw);
        ff = new FutureFetcher<List<OFStatistics>>(sf);
        t = new Thread(ff);
        t.start();
        sf.cancel(true);
        t.join();

        stats = sf.get();
        verify(sw);
        assertEquals(0, stats.size());

        // Test self timeout
        reset(sw);
        sw.cancelStatisticsReply(1);
        sf = new OFStatisticsFuture(tp, sw, 1, 75, TimeUnit.MILLISECONDS);

        replay(sw);
        ff = new FutureFetcher<List<OFStatistics>>(sf);
        t = new Thread(ff);
        t.start();
        t.join(2000);

        stats = sf.get();
        verify(sw);
        assertEquals(0, stats.size());
    }

    @Test
    public void testMessageFilterManager() throws Exception {
        class MyOFMessageFilterManager extends OFMessageFilterManager {
            public MyOFMessageFilterManager(int timer_interval) {
                super();
                TIMER_INTERVAL = timer_interval;
            }
        }
        FloodlightModuleContext fmCntx = new FloodlightModuleContext();
        MockFloodlightProvider mfp = new MockFloodlightProvider();
        OFMessageFilterManager mfm = new MyOFMessageFilterManager(100);
        MockThreadPoolService mtp = new MockThreadPoolService();
        fmCntx.addService(IOFMessageFilterManagerService.class, mfm);
        fmCntx.addService(IFloodlightProviderService.class, mfp);
        fmCntx.addService(IThreadPoolService.class, mtp);
        String sid = null;
        
        mfm.init(fmCntx);
        mfm.startUp(fmCntx);

        ConcurrentHashMap <String, String> filter;
        int i;

        //Adding the filter works -- adds up to the maximum filter size.
        for(i=mfm.getMaxFilterSize(); i > 0; --i) {
            filter = new ConcurrentHashMap<String,String>();
            filter.put("mac", String.format("00:11:22:33:44:%d%d", i,i));
            sid = mfm.setupFilter(null, filter, 60);
            assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize() - i +1);
        }

        // Add one more to see if you can't
        filter = new ConcurrentHashMap<String,String>();
        filter.put("mac", "mac2");
        mfm.setupFilter(null, filter, 10*1000);

        assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize());

        // Deleting the filter works.
        mfm.setupFilter(sid, null, -1);        
        assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize()-1);

        // Creating mock switch to which we will send packet out and 
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(new Long(0));

        // Mock Packet-in   
        IPacket testPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        byte[] testPacketSerialized = testPacket.serialize();

        // Build the PacketIn        
        OFPacketIn pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        // Mock Packet-out
        OFPacketOut packetOut =
                (OFPacketOut) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        packetOut.setBufferId(pi.getBufferId())
        .setInPort(pi.getInPort());
        List<OFAction> poactions = new ArrayList<OFAction>();
        poactions.add(new OFActionOutput(OFPort.OFPP_TABLE.getValue(), (short) 0));
        packetOut.setActions(poactions)
        .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
        .setPacketData(testPacketSerialized)
        .setLengthU(OFPacketOut.MINIMUM_LENGTH+packetOut.getActionsLength()+testPacketSerialized.length);

        FloodlightContext cntx = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD, (Ethernet) testPacket);


        // Let's check the listeners.
        List <IOFMessageListener> lm; 

        // Check to see if all the listeners are active.
        lm = mfp.getListeners().get(OFType.PACKET_OUT);
        assertTrue(lm.size() == 1);
        assertTrue(lm.get(0).equals(mfm));

        lm = mfp.getListeners().get(OFType.FLOW_MOD);
        assertTrue(lm.size() == 1);
        assertTrue(lm.get(0).equals(mfm));

        lm = mfp.getListeners().get(OFType.PACKET_IN);
        assertTrue(lm.size() == 1);
        assertTrue(lm.get(0).equals(mfm));

        HashSet<String> matchedFilters;        

        // Send a packet in and check if it matches a filter.
        matchedFilters = mfm.getMatchedFilters(pi, cntx);
        assertTrue(matchedFilters.size() == 1);

        // Send a packet out and check if it matches a filter
        matchedFilters = mfm.getMatchedFilters(packetOut, cntx);
        assertTrue(matchedFilters.size() == 1);

        // Wait for all filters to be timed out.
        Thread.sleep(150);
        assertEquals(0, mfm.getNumberOfFilters());
    }

    @Test
    public void testAddSwitchNoClearFM() throws Exception {
        controller.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();

        OFSwitchImpl oldsw = new OFSwitchImpl();
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setDatapathId(0L);
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        oldsw.setFeaturesReply(featuresReply);

        Channel channel = createMock(Channel.class);
        oldsw.setChannel(channel);
        expect(channel.getRemoteAddress()).andReturn(null);
        expect(channel.close()).andReturn(null);

        IOFSwitch newsw = createMock(IOFSwitch.class);
        expect(newsw.getId()).andReturn(0L).anyTimes();
        expect(newsw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        controller.activeSwitches.put(0L, oldsw);
        
        replay(newsw, channel);

        controller.addSwitch(newsw, false);

        verify(newsw, channel);
    }
    
    @Test
    public void testAddSwitchClearFM() throws Exception {
        controller.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();

        OFSwitchImpl oldsw = new OFSwitchImpl();
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setDatapathId(0L);
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        oldsw.setFeaturesReply(featuresReply);

        Channel channel = createMock(Channel.class);
        oldsw.setChannel(channel);
        expect(channel.close()).andReturn(null);
        expect(channel.getRemoteAddress()).andReturn(null);

        IOFSwitch newsw = createMock(IOFSwitch.class);
        expect(newsw.getId()).andReturn(0L).anyTimes();
        expect(newsw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        newsw.clearAllFlowMods();
        expectLastCall().once();
        controller.activeSwitches.put(0L, oldsw);
        
        replay(newsw, channel);

        controller.addSwitch(newsw, true);

        verify(newsw, channel);
    }
    
    @Test
    public void testUpdateQueue() throws Exception {
        class DummySwitchListener implements IOFSwitchListener {
            public int nAdded;
            public int nRemoved;
            public int nPortChanged;
            public DummySwitchListener() {
                nAdded = 0;
                nRemoved = 0;
                nPortChanged = 0;
            }
            @Override
            public synchronized void addedSwitch(IOFSwitch sw) {
                nAdded++;
                notifyAll();
            }
            @Override
            public synchronized void removedSwitch(IOFSwitch sw) {
                nRemoved++;
                notifyAll();
            }
            @Override
            public String getName() {
                return "dummy";
            }
            @Override
            public synchronized void switchPortChanged(Long switchId) {
                nPortChanged++;
                notifyAll();
            }
        }
        DummySwitchListener switchListener = new DummySwitchListener();
        IOFSwitch sw = createMock(IOFSwitch.class);
        ControllerRunThread t = new ControllerRunThread();
        t.start();
        
        controller.addOFSwitchListener(switchListener);
        synchronized(switchListener) {
            controller.updates.put(controller.new SwitchUpdate(sw,
                                      Controller.SwitchUpdateType.ADDED));
            switchListener.wait(500);
            assertTrue("IOFSwitchListener.addedSwitch() was not called", 
                    switchListener.nAdded == 1);
            controller.updates.put(controller.new SwitchUpdate(sw, 
                                      Controller.SwitchUpdateType.REMOVED));
            switchListener.wait(500);
            assertTrue("IOFSwitchListener.removedSwitch() was not called", 
                    switchListener.nRemoved == 1);
            controller.updates.put(controller.new SwitchUpdate(sw, 
                                      Controller.SwitchUpdateType.PORTCHANGED));
            switchListener.wait(500);
            assertTrue("IOFSwitchListener.switchPortChanged() was not called", 
                    switchListener.nPortChanged == 1);
        }
    }
    

    private Map<String,Object> getFakeControllerIPRow(String id, String controllerId, 
            String type, int number, String discoveredIP ) {
        HashMap<String, Object> row = new HashMap<String,Object>();
        row.put(Controller.CONTROLLER_INTERFACE_ID, id);
        row.put(Controller.CONTROLLER_INTERFACE_CONTROLLER_ID, controllerId);
        row.put(Controller.CONTROLLER_INTERFACE_TYPE, type);
        row.put(Controller.CONTROLLER_INTERFACE_NUMBER, number);
        row.put(Controller.CONTROLLER_INTERFACE_DISCOVERED_IP, discoveredIP);
        return row;
    }

    /**
     * Test notifications for controller node IP changes. This requires
     * synchronization between the main test thread and another thread 
     * that runs Controller's main loop and takes / handles updates. We
     * synchronize with wait(timeout) / notifyAll(). We check for the 
     * expected condition after the wait returns. However, if wait returns
     * due to the timeout (or due to spurious awaking) and the check fails we
     * might just not have waited long enough. Using a long enough timeout
     * mitigates this but we cannot get rid of the fundamental "issue". 
     * 
     * @throws Exception
     */
    @Test
    public void testControllerNodeIPChanges() throws Exception {
        class DummyHAListener implements IHAListener {
            public Map<String, String> curControllerNodeIPs;
            public Map<String, String> addedControllerNodeIPs;
            public Map<String, String> removedControllerNodeIPs;
            public int nCalled;
            
            public DummyHAListener() {
                this.nCalled = 0;
            }
                
            @Override
            public void roleChanged(Role oldRole, Role newRole) {
                // ignore
            }
    
            @Override
            public synchronized void controllerNodeIPsChanged(
                    Map<String, String> curControllerNodeIPs,
                    Map<String, String> addedControllerNodeIPs,
                    Map<String, String> removedControllerNodeIPs) {
                this.curControllerNodeIPs = curControllerNodeIPs;
                this.addedControllerNodeIPs = addedControllerNodeIPs;
                this.removedControllerNodeIPs = removedControllerNodeIPs;
                this.nCalled++;
                notifyAll();
            }
            
            public void do_assert(int nCalled,
                    Map<String, String> curControllerNodeIPs,
                    Map<String, String> addedControllerNodeIPs,
                    Map<String, String> removedControllerNodeIPs) {
                assertEquals("nCalled is not as expected", nCalled, this.nCalled);
                assertEquals("curControllerNodeIPs is not as expected", 
                        curControllerNodeIPs, this.curControllerNodeIPs);
                assertEquals("addedControllerNodeIPs is not as expected", 
                        addedControllerNodeIPs, this.addedControllerNodeIPs);
                assertEquals("removedControllerNodeIPs is not as expected", 
                        removedControllerNodeIPs, this.removedControllerNodeIPs);
                
            }
        }
        long waitTimeout = 250; // ms
        DummyHAListener listener  = new DummyHAListener();
        HashMap<String,String> expectedCurMap = new HashMap<String, String>();
        HashMap<String,String> expectedAddedMap = new HashMap<String, String>();
        HashMap<String,String> expectedRemovedMap = new HashMap<String, String>();
        
        controller.addHAListener(listener);
        ControllerRunThread t = new ControllerRunThread();
        t.start();
        
        synchronized(listener) {
            // Insert a first entry
            controller.storageSource.insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    getFakeControllerIPRow("row1", "c1", "Ethernet", 0, "1.1.1.1"));
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedAddedMap.put("c1", "1.1.1.1");
            listener.wait(waitTimeout);
            listener.do_assert(1, expectedCurMap, expectedAddedMap, expectedRemovedMap);
            
            // Add an interface that we want to ignore. 
            controller.storageSource.insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    getFakeControllerIPRow("row2", "c1", "Ethernet", 1, "1.1.1.2"));
            listener.wait(waitTimeout); // TODO: do a different check. This call will have to wait for the timeout
            assertTrue("controllerNodeIPsChanged() should not have been called here", 
                    listener.nCalled == 1);

            // Add another entry
            controller.storageSource.insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    getFakeControllerIPRow("row3", "c2", "Ethernet", 0, "2.2.2.2"));
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedCurMap.put("c2", "2.2.2.2");
            expectedAddedMap.put("c2", "2.2.2.2");
            listener.wait(waitTimeout);
            listener.do_assert(2, expectedCurMap, expectedAddedMap, expectedRemovedMap);


            // Update an entry
            controller.storageSource.updateRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                    "row3", getFakeControllerIPRow("row3", "c2", "Ethernet", 0, "2.2.2.3"));
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedCurMap.put("c2", "2.2.2.3");
            expectedAddedMap.put("c2", "2.2.2.3");
            expectedRemovedMap.put("c2", "2.2.2.2");
            listener.wait(waitTimeout);
            listener.do_assert(3, expectedCurMap, expectedAddedMap, expectedRemovedMap);

            // Delete an entry
            controller.storageSource.deleteRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME, 
                    "row3");
            expectedCurMap.clear();
            expectedAddedMap.clear();
            expectedRemovedMap.clear();
            expectedCurMap.put("c1", "1.1.1.1");
            expectedRemovedMap.put("c2", "2.2.2.3");
            listener.wait(waitTimeout);
            listener.do_assert(4, expectedCurMap, expectedAddedMap, expectedRemovedMap);
        }
    }
    
    @Test
    public void testGetControllerNodeIPs() {
        HashMap<String,String> expectedCurMap = new HashMap<String, String>();
        
        controller.storageSource.insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                getFakeControllerIPRow("row1", "c1", "Ethernet", 0, "1.1.1.1"));
        controller.storageSource.insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                getFakeControllerIPRow("row2", "c1", "Ethernet", 1, "1.1.1.2"));
        controller.storageSource.insertRow(Controller.CONTROLLER_INTERFACE_TABLE_NAME,
                getFakeControllerIPRow("row3", "c2", "Ethernet", 0, "2.2.2.2"));
        expectedCurMap.put("c1", "1.1.1.1");
        expectedCurMap.put("c2", "2.2.2.2");    
        assertEquals("expectedControllerNodeIPs is not as expected", 
                expectedCurMap, controller.getControllerNodeIPs());
    }
    
    @Test
    public void testSetRoleNull() {
        try {
            controller.setRole(null);
            fail("Should have thrown an Exception");
        }
        catch (NullPointerException e) {
            //exptected
        }
    }
    
    @Test 
    public void testSetRole() {
        controller.connectedSwitches.add(new OFSwitchImpl());
        RoleChanger roleChanger = createMock(RoleChanger.class); 
        roleChanger.submitRequest(controller.connectedSwitches, Role.SLAVE);
        controller.roleChanger = roleChanger;
        
        assertEquals("Check that update queue is empty", 0, 
                    controller.updates.size());
        
        replay(roleChanger);
        controller.setRole(Role.SLAVE);
        controller.doSetRole(); // avoid wait
        verify(roleChanger);
        
        Controller.IUpdate upd = controller.updates.poll();
        assertNotNull("Check that update queue has an update", upd);
        assertTrue("Check that update is HARoleUpdate", 
                   upd instanceof Controller.HARoleUpdate);
        Controller.HARoleUpdate roleUpd = (Controller.HARoleUpdate)upd;
        assertSame(Role.MASTER, roleUpd.oldRole);
        assertSame(Role.SLAVE, roleUpd.newRole);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testCheckSwitchReady() {
        OFChannelState state = new OFChannelState();
        Controller.OFChannelHandler chdlr = controller.new OFChannelHandler(state);
        Channel channel = createMock(Channel.class);
        chdlr.channel = channel;
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        
        // Wrong current state 
        // Should not go to READY
        state.hsState = OFChannelState.HandshakeState.HELLO;
        state.hasDescription = false;
        state.hasGetConfigReply = false;
        state.switchBindingDone = false;
        expect(channel.getRemoteAddress()).andReturn(null).anyTimes();
        expect(channel.write(EasyMock.anyObject())).andReturn(null).anyTimes();
        replay(channel);
        chdlr.checkSwitchReady();
        assertSame(OFChannelState.HandshakeState.HELLO, state.hsState);
        
        // Have only config reply
        state.hsState = OFChannelState.HandshakeState.FEATURES_REPLY;
        state.hasDescription = false;
        state.hasGetConfigReply = true;
        state.featuresReply = featuresReply;
        state.switchBindingDone = false;
        chdlr.checkSwitchReady();
        assertSame(OFChannelState.HandshakeState.FEATURES_REPLY, state.hsState);
        assertTrue(controller.connectedSwitches.isEmpty());
        assertTrue(controller.activeSwitches.isEmpty());
        
        // Have only desc reply
        state.hsState = OFChannelState.HandshakeState.FEATURES_REPLY;
        state.hasDescription = true;
        state.description = desc;
        state.hasGetConfigReply = false;
        state.switchBindingDone = false;
        chdlr.checkSwitchReady();
        assertSame(OFChannelState.HandshakeState.FEATURES_REPLY, state.hsState);
        assertTrue(controller.connectedSwitches.isEmpty());
        assertTrue(controller.activeSwitches.isEmpty());
        
        //////////////////////////////////////////
        // Finally, everything is right. Should advance to READY
        //////////////////////////////////////////
        controller.roleChanger = createMock(RoleChanger.class);
        state.hsState = OFChannelState.HandshakeState.FEATURES_REPLY;
        state.hasDescription = true;
        state.description = desc;
        state.hasGetConfigReply = true;
        state.featuresReply = featuresReply;
        state.switchBindingDone = false;
        // Role support disabled. Switch should be promoted to active switch
        // list. 
        // setupSwitchForAddSwitch(chdlr.sw, 0L);
        // chdlr.sw.clearAllFlowMods();
        desc.setManufacturerDescription("test vendor");
        controller.roleChanger.submitRequest(
                (List<IOFSwitch>)EasyMock.anyObject(),
                (Role)EasyMock.anyObject());
        replay(controller.roleChanger);
        chdlr.checkSwitchReady();
        verify(controller.roleChanger);
        assertSame(OFChannelState.HandshakeState.READY, state.hsState);
        reset(controller.roleChanger);
        controller.connectedSwitches.clear();
        controller.activeSwitches.clear();
        
        
        // Role support enabled. 
        state.hsState = OFChannelState.HandshakeState.FEATURES_REPLY;
        controller.role = Role.MASTER;
        Capture<Collection<IOFSwitch>> swListCapture = 
                    new Capture<Collection<IOFSwitch>>();
        controller.roleChanger.submitRequest(capture(swListCapture), 
                    same(Role.MASTER));
        replay(controller.roleChanger);
        chdlr.checkSwitchReady();
        verify(controller.roleChanger);
        assertSame(OFChannelState.HandshakeState.READY, state.hsState);
        assertTrue(controller.activeSwitches.isEmpty());
        assertFalse(controller.connectedSwitches.isEmpty());
        Collection<IOFSwitch> swList = swListCapture.getValue();
        assertEquals(1, swList.size());
    }
    
    public class TestSwitchClass extends OFSwitchImpl {
    }

    public class Test11SwitchClass extends OFSwitchImpl {
    }

    @Test
    public void testBindSwitchToDriver() {
        controller.addOFSwitchDriver("test", this);
        
        OFChannelState state = new OFChannelState();
        Controller.OFChannelHandler chdlr =
                controller.new OFChannelHandler(state);
        
        // Swith should be bound of OFSwitchImpl (default)
        state.hsState = OFChannelState.HandshakeState.HELLO;
        state.hasDescription = true;
        state.hasGetConfigReply = true;
        state.switchBindingDone = false;
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        desc.setManufacturerDescription("test switch");
        desc.setHardwareDescription("version 0.9");
        state.description = desc;
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        state.featuresReply = featuresReply;

        chdlr.bindSwitchToDriver();
        assertTrue(chdlr.sw instanceof OFSwitchImpl);
        assertTrue(!(chdlr.sw instanceof TestSwitchClass));
        
        // Switch should be bound to TestSwitchImpl
        state.switchBindingDone = false;
        desc.setManufacturerDescription("test1 switch");
        desc.setHardwareDescription("version 1.0");
        state.description = desc;
        state.featuresReply = featuresReply;

        chdlr.bindSwitchToDriver();
        assertTrue(chdlr.sw instanceof TestSwitchClass);

        // Switch should be bound to Test11SwitchImpl
        state.switchBindingDone = false;
        desc.setManufacturerDescription("test11 switch");
        desc.setHardwareDescription("version 1.1");
        state.description = desc;
        state.featuresReply = featuresReply;

        chdlr.bindSwitchToDriver();
        assertTrue(chdlr.sw instanceof Test11SwitchClass);
    }
    
    @Test
    public void testBindSwitchOrder() {
        List<String> order = new ArrayList<String>(3);
        controller.addOFSwitchDriver("", this);
        controller.addOFSwitchDriver("test switch", this);
        controller.addOFSwitchDriver("test", this);
        order.add("test switch");
        order.add("test");
        order.add("");
        test_bind_order = true;
        
        OFChannelState state = new OFChannelState();
        Controller.OFChannelHandler chdlr =
                controller.new OFChannelHandler(state);
        chdlr.sw = null;
        
        // Swith should be bound of OFSwitchImpl (default)
        state.hsState = OFChannelState.HandshakeState.HELLO;
        state.hasDescription = true;
        state.hasGetConfigReply = true;
        state.switchBindingDone = false;
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        desc.setManufacturerDescription("test switch");
        desc.setHardwareDescription("version 0.9");
        state.description = desc;
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        state.featuresReply = featuresReply;

        chdlr.bindSwitchToDriver();
        assertTrue(chdlr.sw instanceof OFSwitchImpl);
        assertTrue(!(chdlr.sw instanceof TestSwitchClass));
        // Verify bind_order is called as expected
        assertTrue(order.equals(bind_order));
        test_bind_order = false;
        bind_order = null;
   }
    
    @Test
    public void testChannelDisconnected() throws Exception {
        OFChannelState state = new OFChannelState();
        state.hsState = OFChannelState.HandshakeState.READY;
        Controller.OFChannelHandler chdlr = controller.new OFChannelHandler(state);
        chdlr.sw = createMock(IOFSwitch.class);
        
        // Switch is active 
        expect(chdlr.sw.getId()).andReturn(0L).anyTimes();
        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:00")
                    .anyTimes();
        chdlr.sw.cancelAllStatisticsReplies();
        chdlr.sw.setConnected(false);
        expect(chdlr.sw.isConnected()).andReturn(true);
        
        controller.connectedSwitches.add(chdlr.sw);
        controller.activeSwitches.put(0L, chdlr.sw);
        
        replay(chdlr.sw);
        chdlr.channelDisconnected(null, null);
        verify(chdlr.sw);
        
        // Switch is connected but not active
        reset(chdlr.sw);
        expect(chdlr.sw.getId()).andReturn(0L).anyTimes();
        chdlr.sw.setConnected(false);
        replay(chdlr.sw);
        chdlr.channelDisconnected(null, null);
        verify(chdlr.sw);
        
        // Not in ready state
        state.hsState = HandshakeState.START;
        reset(chdlr.sw);
        replay(chdlr.sw);
        chdlr.channelDisconnected(null, null);
        verify(chdlr.sw);
        
        // Switch is null
        state.hsState = HandshakeState.READY;
        chdlr.sw = null;
        chdlr.channelDisconnected(null, null);
    }
    
    /*
    @Test
    public void testRoleChangeForSerialFailoverSwitch() throws Exception {
        OFSwitchImpl newsw = createMock(OFSwitchImpl.class);
        expect(newsw.getId()).andReturn(0L).anyTimes();
        expect(newsw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        Channel channel2 = createMock(Channel.class);
        expect(newsw.getChannel()).andReturn(channel2);
        
        // newsw.role is null because the switch does not support
        // role request messages
        expect(newsw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                        .andReturn(false);
        // switch is connected 
        controller.connectedSwitches.add(newsw);

        // the switch should get disconnected when role is changed to SLAVE
        expect(channel2.close()).andReturn(null);

        replay(newsw, channel2);
        controller.setRole(Role.SLAVE);
        verify(newsw,  channel2);
    }
    */

    @Test
    public void testRoleNotSupportedError() throws Exception {
        int xid = 424242;
        OFChannelState state = new OFChannelState();
        state.hsState = HandshakeState.READY;
        Controller.OFChannelHandler chdlr = controller.new OFChannelHandler(state);
        chdlr.sw = createMock(IOFSwitch.class);
        Channel ch = createMock(Channel.class);
        
        // the error returned when role request message is not supported by sw
        OFError msg = new OFError();
        msg.setType(OFType.ERROR);
        msg.setXid(xid);
        msg.setErrorType(OFErrorType.OFPET_BAD_REQUEST);
        
        // the switch connection should get disconnected when the controller is
        // in SLAVE mode and the switch does not support role-request messages
        controller.role = Role.SLAVE;
        setupPendingRoleRequest(chdlr.sw, xid, controller.role, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(Role.SLAVE, false);
        expect(chdlr.sw.getHARole()).andReturn(Role.SLAVE);
        chdlr.sw.disconnectOutputStream();
        
        replay(ch, chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(ch, chdlr.sw);
        assertTrue("activeSwitches must be empty",
                   controller.activeSwitches.isEmpty());
        reset(ch, chdlr.sw);
              
        // We are MASTER, the switch should be added to the list of active
        // switches.
        controller.role = Role.MASTER;
        setupPendingRoleRequest(chdlr.sw, xid, controller.role, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(controller.role, false);
        setupSwitchForAddSwitch(chdlr.sw, 0L);
        chdlr.sw.clearAllFlowMods();
        expect(chdlr.sw.getHARole()).andReturn(null).anyTimes();
        replay(ch, chdlr.sw);
        
        chdlr.processOFMessage(msg);
        verify(ch, chdlr.sw);
        assertSame("activeSwitches must contain this switch",
                   chdlr.sw, controller.activeSwitches.get(0L));
        reset(ch, chdlr.sw);

    }
    
    
    @Test 
    public void testVendorMessageUnknown() throws Exception {
        // Check behavior with an unknown vendor id
        // Ensure that vendor message listeners get called, even for Vendors 
        // unknown to floodlight. It is the responsibility of the listener to
        // discard unknown vendors.
        OFChannelState state = new OFChannelState();
        state.hsState = HandshakeState.READY;
        Controller.OFChannelHandler chdlr = controller.new OFChannelHandler(state);
        OFVendor msg = new OFVendor();
        msg.setVendor(0);
        IOFSwitch sw = createMock(IOFSwitch.class);
        chdlr.sw = sw;
        controller.activeSwitches.put(1L, sw);
        
        // prepare the Vendor Message Listener expectations
        ListenerDispatcher<OFType, IOFMessageListener> ld = 
                new ListenerDispatcher<OFType, IOFMessageListener>();
        IOFMessageListener ml = createMock(IOFMessageListener.class);
        expect(ml.getName()).andReturn("Dummy").anyTimes();
        expect(ml.isCallbackOrderingPrereq((OFType)anyObject(), 
                (String)anyObject())).andReturn(false).anyTimes();
        expect(ml.isCallbackOrderingPostreq((OFType)anyObject(), 
                (String)anyObject())).andReturn(false).anyTimes();
        expect(ml.receive(eq(sw), eq(msg), isA(FloodlightContext.class))).
                andReturn(Command.CONTINUE).once();
        controller.messageListeners.put(OFType.VENDOR, ld);

        // prepare the switch and lock expectations
        Lock lock = createNiceMock(Lock.class);
        expect(sw.getListenerReadLock()).andReturn(lock).anyTimes();
        expect(sw.isConnected()).andReturn(true).anyTimes();
        expect(sw.getHARole()).andReturn(Role.MASTER).anyTimes();
        expect(sw.getId()).andReturn(1L).anyTimes();
        
        // test
        replay(chdlr.sw, lock, ml);
        ld.addListener(OFType.VENDOR, ml);
        chdlr.processOFMessage(msg);
    }
    
    
    // Helper function.
    protected Controller.OFChannelHandler getChannelHandlerForRoleReplyTest() {
        OFChannelState state = new OFChannelState();
        state.hsState = HandshakeState.READY;
        Controller.OFChannelHandler chdlr = controller.new OFChannelHandler(state);
        chdlr.sw = createMock(IOFSwitch.class);
        return chdlr;
    }
    
    // Helper function
    protected OFVendor getRoleReplyMsgForRoleReplyTest(int xid, int nicira_role) {
        OFVendor msg = new OFVendor();
        msg.setXid(xid);
        msg.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
        OFRoleReplyVendorData roleReplyVendorData = 
                new OFRoleReplyVendorData(OFRoleReplyVendorData.NXT_ROLE_REPLY);
        msg.setVendorData(roleReplyVendorData);
        roleReplyVendorData.setRole(nicira_role);
        return msg;
    }
    
    // Helper function
    protected void setupPendingRoleRequest(IOFSwitch sw, int xid, Role role,
            long cookie) {
        LinkedList<PendingRoleRequestEntry> pendingList =
                new LinkedList<PendingRoleRequestEntry>();
        controller.roleChanger.pendingRequestMap.put(sw, pendingList);
        PendingRoleRequestEntry entry =
                new PendingRoleRequestEntry(xid, role, cookie);
        pendingList.add(entry);
    }
    
    /** invalid role in role reply */
    @Test 
    public void testNiciraRoleReplyInvalidRole() 
                    throws Exception {
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        Channel ch = createMock(Channel.class);
        chdlr.sw.disconnectOutputStream();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid, 232323);
        replay(chdlr.sw, ch);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw, ch);
    }
    
    /** First role reply message received: transition from slave to master */
    @Test 
    public void testNiciraRoleReplySlave2MasterFristTime() 
                    throws Exception {
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
                                       OFRoleReplyVendorData.NX_ROLE_MASTER);

        setupPendingRoleRequest(chdlr.sw, xid, Role.MASTER, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(Role.MASTER, true);
        setupSwitchForAddSwitch(chdlr.sw, 1L);
        chdlr.sw.clearAllFlowMods();
        replay(chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw);
        assertSame("activeSwitches must contain this switch",
                   chdlr.sw, controller.activeSwitches.get(1L));
    }
    
    
    /** Not first role reply message received: transition from slave to master */
    @Test 
    public void testNiciraRoleReplySlave2MasterNotFristTime() 
                    throws Exception {
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
                                       OFRoleReplyVendorData.NX_ROLE_MASTER);

        setupPendingRoleRequest(chdlr.sw, xid, Role.MASTER, 123456);        
        expect(chdlr.sw.getHARole()).andReturn(Role.SLAVE);
        chdlr.sw.setHARole(Role.MASTER, true);
        setupSwitchForAddSwitch(chdlr.sw, 1L);
        // Flow table shouldn't be wipe
        replay(chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw);
        assertSame("activeSwitches must contain this switch",
                   chdlr.sw, controller.activeSwitches.get(1L));
    }
    
    /** transition from slave to equal */
    @Test 
    public void testNiciraRoleReplySlave2Equal() 
                    throws Exception {
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid,
                                       OFRoleReplyVendorData.NX_ROLE_OTHER);
        
        setupPendingRoleRequest(chdlr.sw, xid, Role.EQUAL, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(Role.EQUAL, true);
        setupSwitchForAddSwitch(chdlr.sw, 1L);
        chdlr.sw.clearAllFlowMods();
        replay(chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw);
        assertSame("activeSwitches must contain this switch",
                   chdlr.sw, controller.activeSwitches.get(1L));
    };
    
    @Test
    /** Slave2Slave transition ==> no change */
    public void testNiciraRoleReplySlave2Slave() throws Exception{
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid, 
                                       OFRoleReplyVendorData.NX_ROLE_SLAVE);
        
        setupPendingRoleRequest(chdlr.sw, xid, Role.SLAVE, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(Role.SLAVE, true);
        expect(chdlr.sw.getId()).andReturn(1L).anyTimes();
        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:01")
                    .anyTimes();
        // don't add switch to activeSwitches ==> slave2slave
        replay(chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw);
        assertTrue("activeSwitches must be empty", 
                   controller.activeSwitches.isEmpty());
    }
    
    @Test
    /** Equal2Master transition ==> no change */
    public void testNiciraRoleReplyEqual2Master() throws Exception{
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid, 
                                       OFRoleReplyVendorData.NX_ROLE_MASTER);
        
        setupPendingRoleRequest(chdlr.sw, xid, Role.MASTER, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(Role.MASTER, true);
        expect(chdlr.sw.getId()).andReturn(1L).anyTimes();
        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:01")
                    .anyTimes();
        controller.activeSwitches.put(1L, chdlr.sw);
        // Must not clear flow mods
        replay(chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw);
        assertSame("activeSwitches must contain this switch",
                   chdlr.sw, controller.activeSwitches.get(1L));
    }
    
    @Test 
    public void testNiciraRoleReplyMaster2Slave() 
                    throws Exception {
        int xid = 424242;
        Controller.OFChannelHandler chdlr = getChannelHandlerForRoleReplyTest();
        OFVendor msg = getRoleReplyMsgForRoleReplyTest(xid, 
                                       OFRoleReplyVendorData.NX_ROLE_SLAVE);
        
        setupPendingRoleRequest(chdlr.sw, xid, Role.SLAVE, 123456);                
        expect(chdlr.sw.getHARole()).andReturn(null);
        chdlr.sw.setHARole(Role.SLAVE, true);
        expect(chdlr.sw.getId()).andReturn(1L).anyTimes();
        expect(chdlr.sw.getStringId()).andReturn("00:00:00:00:00:00:00:01")
                    .anyTimes();
        controller.activeSwitches.put(1L, chdlr.sw);
        expect(chdlr.sw.getHARole()).andReturn(Role.SLAVE).anyTimes();
        expect(chdlr.sw.isConnected()).andReturn(true);
        chdlr.sw.cancelAllStatisticsReplies();
        replay(chdlr.sw);
        chdlr.processOFMessage(msg);
        verify(chdlr.sw);
        assertTrue("activeSwitches must be empty", 
                   controller.activeSwitches.isEmpty());
    }
    
    /**
     * Tests that you can't remove a switch from the active
     * switch list.
     * @throws Exception
     */
    @Test
    public void testRemoveActiveSwitch() {
        IOFSwitch sw = createNiceMock(IOFSwitch.class);
        boolean exceptionThrown = false;
        expect(sw.getId()).andReturn(1L).anyTimes();
        replay(sw);
        getController().activeSwitches.put(sw.getId(), sw);
        try {
            getController().getSwitches().remove(1L);
        } catch (UnsupportedOperationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        verify(sw);
    }
    
    public void verifyPortChangedUpdateInQueue(IOFSwitch sw) throws Exception {
        assertEquals(1, controller.updates.size());
        IUpdate update = controller.updates.take();
        assertEquals(true, update instanceof SwitchUpdate);
        SwitchUpdate swUpdate = (SwitchUpdate)update;
        assertEquals(sw, swUpdate.sw);
        assertEquals(SwitchUpdateType.PORTCHANGED, swUpdate.switchUpdateType);
    }
    
    /*
     * Test handlePortStatus()
     * TODO: test correct updateStorage behavior!
     */
    @Test 
    public void testHandlePortStatus() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        OFPhysicalPort port = new OFPhysicalPort();
        port.setName("myPortName1");
        port.setPortNumber((short)42);
        
        OFPortStatus ofps = new OFPortStatus();
        ofps.setDesc(port);
        
        ofps.setReason((byte)OFPortReason.OFPPR_ADD.ordinal());
        sw.setPort(port);
        expectLastCall().once();
        replay(sw);
        controller.handlePortStatusMessage(sw, ofps);
        verify(sw);
        verifyPortChangedUpdateInQueue(sw);
        reset(sw);
        
        ofps.setReason((byte)OFPortReason.OFPPR_MODIFY.ordinal());
        sw.setPort(port);
        expectLastCall().once();
        replay(sw);
        controller.handlePortStatusMessage(sw, ofps);
        verify(sw);
        verifyPortChangedUpdateInQueue(sw);
        reset(sw);
        
        ofps.setReason((byte)OFPortReason.OFPPR_DELETE.ordinal());
        sw.deletePort(port.getPortNumber());
        expectLastCall().once();
        replay(sw);
        controller.handlePortStatusMessage(sw, ofps);
        verify(sw);
        verifyPortChangedUpdateInQueue(sw);
        reset(sw);
    }

    @Override
    public IOFSwitch getOFSwitchImpl(String regis_desc,
            OFDescriptionStatistics description) {
        // If testing bind order, just record registered desc string
        if (test_bind_order) {
            if (bind_order == null) {
                bind_order = new ArrayList<String>();
            }
            bind_order.add(regis_desc);
            return null;
        }
        String hw_desc = description.getHardwareDescription();
        if (hw_desc.equals("version 1.1")) {
            return new Test11SwitchClass();
        }
        if (hw_desc.equals("version 1.0")) {
            return new TestSwitchClass();
        }
        return null;
    }
    
    private void setupSwitchForDispatchTest(IOFSwitch sw, 
                                            boolean isConnected,
                                            Role role) {
        Lock lock = createNiceMock(Lock.class);
        expect(sw.getId()).andReturn(1L).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:01").anyTimes();
        expect(sw.getListenerReadLock()).andReturn(lock).anyTimes();
        expect(sw.isConnected()).andReturn(isConnected).anyTimes();
        expect(sw.getHARole()).andReturn(role).anyTimes();
        replay(lock);
        
    }
    
    @Test 
    public void testMessageDispatch() throws Exception {
        // Mock a dummy packet in
        // Build our test packet
        IPacket testPacket = new Ethernet()
        .setSourceMACAddress("00:44:33:22:11:00")
        .setDestinationMACAddress("00:11:22:33:44:55")
        .setEtherType(Ethernet.TYPE_ARP)
        .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
        byte[] testPacketSerialized = testPacket.serialize();

        // Build the PacketIn        
        OFPacketIn pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);

        
        // Mock switch and add to data structures 
        IOFSwitch sw = createMock(IOFSwitch.class);
        
        controller.connectedSwitches.add(sw);
        
        // create a channel handler 
        OFChannelState state = new OFChannelState();
        state.hsState = HandshakeState.READY;
        Controller.OFChannelHandler chdlr = controller.new OFChannelHandler(state);
        chdlr.sw = sw;
        
        // mock role changer 
        RoleChanger roleChanger = createMock(RoleChanger.class); 
        roleChanger.submitRequest(eq(controller.connectedSwitches), 
                                  anyObject(Role.class));
        expectLastCall().anyTimes();
        controller.roleChanger = roleChanger;
        
        
        // Mock message listener and add 
        IOFMessageListener listener = createNiceMock(IOFMessageListener.class);
        expect(listener.getName()).andReturn("foobar").anyTimes();
        replay(listener);
        controller.addOFMessageListener(OFType.PACKET_IN, listener);
        resetToStrict(listener);
        
        
        assertEquals("Check that update queue is empty", 0, 
                    controller.updates.size());
        
        
        replay(roleChanger);
        
        //-------------------
        // Test 1: role is master, switch is master and in activeMap
        // we expect the msg to be dispatched 
        reset(sw);
        resetToDefault(listener);
        controller.activeSwitches.put(1L, sw);
        setupSwitchForDispatchTest(sw, true, Role.MASTER);
        listener.receive(same(sw), same(pi), 
                         anyObject(FloodlightContext.class));
        expectLastCall().andReturn(Command.STOP).once();
        replay(sw, listener);
        chdlr.processOFMessage(pi);
        verify(sw, listener);
        assertEquals(0, controller.updates.size());
        
        
        //-------------------
        // Test 1b: role is master, switch is master and in activeMap
        // but switch is not connected 
        // no message dispatched 
        reset(sw);
        resetToDefault(listener);
        controller.activeSwitches.put(1L, sw);
        setupSwitchForDispatchTest(sw, false, Role.MASTER);
        replay(sw, listener);
        chdlr.processOFMessage(pi);
        verify(sw, listener);
        assertEquals(0, controller.updates.size());
        
        
        //-------------------
        // Test 1c: role is master, switch is slave and in activeMap
        // no message dispatched 
        reset(sw);
        resetToDefault(listener);
        controller.activeSwitches.put(1L, sw);
        setupSwitchForDispatchTest(sw, true, Role.SLAVE);
        replay(sw, listener);
        chdlr.processOFMessage(pi);
        verify(sw, listener);
        assertEquals(0, controller.updates.size());
        
        
        //-------------------
        // Test 1d: role is master, switch is master but not in activeMap
        // we expect the msg to be dispatched 
        reset(sw);
        resetToDefault(listener);
        controller.activeSwitches.remove(1L);
        setupSwitchForDispatchTest(sw, true, Role.MASTER);
        replay(sw, listener);
        chdlr.processOFMessage(pi);
        verify(sw, listener);
        assertEquals(0, controller.updates.size());        
        
        
        
        //-------------------
        // Test 2: check correct dispatch and HA notification behavior
        // We set the role to slave but do not notify the clients 
        reset(sw);
        resetToDefault(listener);
        controller.activeSwitches.put(1L, sw);
        setupSwitchForDispatchTest(sw, true, Role.MASTER);
        listener.receive(same(sw), same(pi), 
                         anyObject(FloodlightContext.class));
        expectLastCall().andReturn(Command.STOP).once();
        replay(sw, listener);
        controller.setRole(Role.SLAVE);
        controller.doSetRole();  // avoid the wait
        chdlr.processOFMessage(pi);
        verify(sw, listener);
        assertEquals(1, controller.updates.size());
        
        // Now notify listeners 
        Controller.IUpdate upd = controller.updates.poll(1, TimeUnit.NANOSECONDS);
        assertTrue("Check that update is HARoleUpdate", 
                   upd instanceof Controller.HARoleUpdate);
        upd.dispatch();
        resetToDefault(listener);
        replay(listener);
        chdlr.processOFMessage(pi);
        verify(listener);
        assertEquals(0, controller.updates.size());
        
        // transition back to master but don't notify yet
        resetToDefault(listener);
        replay(listener);
        controller.setRole(Role.MASTER);
        controller.doSetRole(); // avoid the wait
        chdlr.processOFMessage(pi);
        verify(listener);
        assertEquals(1, controller.updates.size());
        
        // now notify listeners 
        upd = controller.updates.poll(1, TimeUnit.NANOSECONDS);
        assertTrue("Check that update is HARoleUpdate", 
                   upd instanceof Controller.HARoleUpdate);
        upd.dispatch();
        resetToDefault(listener);
        listener.receive(same(sw), same(pi), 
                         anyObject(FloodlightContext.class));
        expectLastCall().andReturn(Command.STOP).once();
        replay(listener);
        chdlr.processOFMessage(pi);
        verify(listener);
        assertEquals(0, controller.updates.size());
        
        verify(sw);
    }
    
    
    /*
     * Test correct timing behavior between HA Role notification and dispatching
     * OFMessages to listeners. 
     * When transitioning to SLAVE: stop dispatching message before sending
     *    notifications
     * When transitioning to MASTER: start dispatching messages after sending
     *     notifications
     * (This implies that messages should not be dispatched while the 
     * notifications are being sent). 
     * 
     * We encapsulate the logic for this in a class that implements both
     * IHAListener and IOFMessageListener. Then we inject an OFMessage fom
     * the IHAListener and check that it gets dropped correctly. 
     */
    @Test 
    public void testRoleNotifcationAndMessageDispatch() throws Exception {
        class TestRoleNotificationsAndDispatch implements IHAListener, IOFMessageListener {
            OFPacketIn pi;
            Controller.OFChannelHandler chdlr;
            IOFSwitch sw;
            private boolean haveReceived;
            private boolean doInjectMessageFromHAListener;
            
            public TestRoleNotificationsAndDispatch() {
                IPacket testPacket = new Ethernet()
                .setSourceMACAddress("00:44:33:22:11:00")
                .setDestinationMACAddress("00:11:22:33:44:55")
                .setEtherType(Ethernet.TYPE_ARP)
                .setPayload(
                        new ARP()
                        .setHardwareType(ARP.HW_TYPE_ETHERNET)
                        .setProtocolType(ARP.PROTO_TYPE_IP)
                        .setHardwareAddressLength((byte) 6)
                        .setProtocolAddressLength((byte) 4)
                        .setOpCode(ARP.OP_REPLY)
                        .setSenderHardwareAddress(Ethernet.toMACAddress("00:44:33:22:11:00"))
                        .setSenderProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.1"))
                        .setTargetHardwareAddress(Ethernet.toMACAddress("00:11:22:33:44:55"))
                        .setTargetProtocolAddress(IPv4.toIPv4AddressBytes("192.168.1.2")));
                byte[] testPacketSerialized = testPacket.serialize();
        
                // Build the PacketIn        
                pi = ((OFPacketIn) new BasicFactory().getMessage(OFType.PACKET_IN))
                        .setBufferId(-1)
                        .setInPort((short) 1)
                        .setPacketData(testPacketSerialized)
                        .setReason(OFPacketInReason.NO_MATCH)
                        .setTotalLength((short) testPacketSerialized.length);
                
                // Mock switch and add to data structures 
                sw = createMock(IOFSwitch.class);
                controller.connectedSwitches.add(sw);
                controller.activeSwitches.put(1L, sw);
                setupSwitchForDispatchTest(sw, true, Role.MASTER);
                replay(sw);
                
                // create a channel handler 
                OFChannelState state = new OFChannelState();
                state.hsState = HandshakeState.READY;
                chdlr = controller.new OFChannelHandler(state);
                chdlr.sw = this.sw;
                
                // add ourself as listeners
                controller.addOFMessageListener(OFType.PACKET_IN, this);
                controller.addHAListener(this);
            }
            
            
            private void injectMessage(boolean shouldReceive) throws Exception {
                haveReceived = false;
                chdlr.processOFMessage(pi);
                assertEquals(shouldReceive, haveReceived);
            }
            
            public void transitionToSlave() throws Exception {
                IUpdate update;
                
                // Bring controller into well defined state for MASTER
                doInjectMessageFromHAListener = false; 
                update = controller.new HARoleUpdate(Role.MASTER, Role.SLAVE);
                update.dispatch();
                doInjectMessageFromHAListener = true;
                
                
                // inject message. Listener called 
                injectMessage(true);
                // Dispatch update 
                update = controller.new HARoleUpdate(Role.SLAVE, Role.MASTER);
                update.dispatch();
                // inject message. Listener not called
                injectMessage(false);
            }
            
            public void transitionToMaster() throws Exception {
                IUpdate update;
                
                // Bring controller into well defined state for SLAVE
                doInjectMessageFromHAListener = false; 
                update = controller.new HARoleUpdate(Role.SLAVE, Role.MASTER);
                update.dispatch();
                doInjectMessageFromHAListener = true;
                
                
                // inject message. Listener not called 
                injectMessage(false);
                // Dispatch update 
                update = controller.new HARoleUpdate(Role.MASTER, Role.SLAVE);
                update.dispatch();
                // inject message. Listener called
                injectMessage(true);
            }
            
            //---------------
            // IHAListener
            //---------------
            @Override
            public void roleChanged(Role oldRole, Role newRole) {
                try {
                    if (doInjectMessageFromHAListener)
                        injectMessage(false);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
            @Override
            public
                    void
                    controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
                                             Map<String, String> addedControllerNodeIPs,
                                             Map<String, String> removedControllerNodeIPs) {
                // TODO Auto-generated method stub
            }
            
            //-------------------------
            // IOFMessageListener
            //-------------------------
            @Override
            public String getName() {
                return "FooBar";
            }
            @Override
            public boolean isCallbackOrderingPrereq(OFType type, String name) {
                return false;
            }
            @Override
            public boolean isCallbackOrderingPostreq(OFType type, String name) {
                return false;
            }
            @Override
            public Command receive(IOFSwitch sw, 
                                   OFMessage msg, 
                                   FloodlightContext cntx) {
                haveReceived = true;
                return Command.STOP;
            }
        }
        
        TestRoleNotificationsAndDispatch x = new TestRoleNotificationsAndDispatch();
        x.transitionToSlave();
        x.transitionToMaster();
        
    }

}
