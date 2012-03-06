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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightProvider;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageFilterManagerService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFMessageListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFMessageFilterManager;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
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

import org.jboss.netty.channel.Channel;
import org.junit.Test;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class ControllerTest extends FloodlightTestCase {
    private Controller controller;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        Set<IFloodlightModule> modSet = new HashSet<IFloodlightModule>();
        
        FloodlightProvider cm = new FloodlightProvider();
        controller = (Controller)cm.getServiceImpls().get(IFloodlightProviderService.class);
        fmc.addService(IFloodlightProviderService.class, controller);
        modSet.add(cm);
        
        MemoryStorageSource memstorage = new MemoryStorageSource();
        fmc.addService(IStorageSourceService.class, memstorage);
        modSet.add(memstorage);
        
        RestApiServer restApi = new RestApiServer();
        fmc.addService(IRestApiService.class, restApi);
        modSet.add(restApi);
        
        CounterStore cs = new CounterStore();
        fmc.addService(ICounterStoreService.class, cs);
        modSet.add(cs);
        
        PktInProcessingTime ppt = new PktInProcessingTime();
        fmc.addService(IPktInProcessingTimeService.class, ppt);
        modSet.add(ppt);
        
        fmc.createConfigMaps(modSet);
        ppt.init(fmc);
        restApi.init(fmc);
        memstorage.init(fmc);
        cm.init(fmc);
        ppt.startUp(fmc);
        restApi.startUp(fmc);
        memstorage.startUp(fmc);
        cm.startUp(fmc);
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
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(sw.getFeaturesReply()).andReturn(new OFFeaturesReply()).anyTimes();

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
        expect(test1.getId()).andReturn(0).anyTimes();
        expect(sw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(sw.getFeaturesReply()).andReturn(new OFFeaturesReply()).anyTimes();
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
        MockFloodlightProvider mbp = new MockFloodlightProvider();
        IOFSwitch sw = createMock(IOFSwitch.class);
        sw.cancelStatisticsReply(1);
        OFStatisticsFuture sf = new OFStatisticsFuture(mbp, sw, 1);
        mbp.addOFSwitchListener(sf);

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
        assertEquals(0, mbp.getSwitchListeners().size());

        // Test multiple stats replies
        reset(sw);
        sw.cancelStatisticsReply(1);

        sf = new OFStatisticsFuture(mbp, sw, 1);
        mbp.addOFSwitchListener(sf);

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
        assertEquals(0, mbp.getSwitchListeners().size());

        // Test cancellation
        reset(sw);
        sw.cancelStatisticsReply(1);
        sf = new OFStatisticsFuture(mbp, sw, 1);
        mbp.addOFSwitchListener(sf);

        replay(sw);
        ff = new FutureFetcher<List<OFStatistics>>(sf);
        t = new Thread(ff);
        t.start();
        sf.cancel(true);
        t.join();

        stats = sf.get();
        verify(sw);
        assertEquals(0, stats.size());
        assertEquals(0, mbp.getSwitchListeners().size());

        // Test self timeout
        reset(sw);
        sw.cancelStatisticsReply(1);
        sf = new OFStatisticsFuture(mbp, sw, 1, 1, TimeUnit.SECONDS);
        mbp.addOFSwitchListener(sf);

        replay(sw);
        ff = new FutureFetcher<List<OFStatistics>>(sf);
        t = new Thread(ff);
        t.start();
        t.join(2000);

        stats = sf.get();
        verify(sw);
        assertEquals(0, stats.size());
        assertEquals(0, mbp.getSwitchListeners().size());
    }

    @Test
    public void testMessageFilterManager() throws Exception {
        FloodlightModuleContext fmCntx = new FloodlightModuleContext();
        MockFloodlightProvider mfp = new MockFloodlightProvider();
        OFMessageFilterManager mfm = new OFMessageFilterManager();
        fmCntx.addService(IOFMessageFilterManagerService.class, mfm);
        fmCntx.addService(IFloodlightProviderService.class, mfp);
        String sid = null;

        
        mfm.init(fmCntx);
        mfm.startUp(fmCntx);

        ConcurrentHashMap <String, String> filter;
        int i;

        //Adding the filter works -- adds up to the maximum filter size.
        for(i=mfm.getMaxFilterSize(); i > 0; --i) {
            filter = new ConcurrentHashMap<String,String>();
            filter.put("mac", String.format("00:11:22:33:44:%d%d", i,i));
            sid = mfm.setupFilter(null, filter, 6);
            assertTrue(mfm.getNumberOfFilters() == mfm.getMaxFilterSize() - i +1);
        }

        // Add one more to see if you can't
        filter = new ConcurrentHashMap<String,String>();
        filter.put("mac", "mac2");
        mfm.setupFilter(null, filter, 10);

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

        // Wait for 8 seconds for all filters to be timed out.
        Thread.sleep(8000);
        assertTrue(mfm.getNumberOfFilters() == 0);
    }

    @Test
    public void testAddSwitch() throws Exception {
        controller.activeSwitches = new ConcurrentHashMap<Long, IOFSwitch>();

        //OFSwitchImpl oldsw = createMock(OFSwitchImpl.class);
        OFSwitchImpl oldsw = new OFSwitchImpl();
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setDatapathId(0L);
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        oldsw.setFeaturesReply(featuresReply);
        //expect(oldsw.getId()).andReturn(0L).anyTimes();
        //expect(oldsw.asyncRemoveSwitchLock()).andReturn(rwlock.writeLock()).anyTimes();
        //oldsw.setConnected(false);
        //expect(oldsw.getFeaturesReply()).andReturn(new OFFeaturesReply()).anyTimes();
        //expect(oldsw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();

        Channel channel = createMock(Channel.class);
        //expect(oldsw.getChannel()).andReturn(channel);
        oldsw.setChannel(channel);
        expect(channel.close()).andReturn(null);

        IOFSwitch newsw = createMock(IOFSwitch.class);
        expect(newsw.getId()).andReturn(0L).anyTimes();
        expect(newsw.getStringId()).andReturn("00:00:00:00:00:00:00").anyTimes();
        expect(newsw.getConnectedSince()).andReturn(new Date());
        Channel channel2 = createMock(Channel.class);
        expect(newsw.getChannel()).andReturn(channel2);
        expect(channel2.getRemoteAddress()).andReturn(null);
        expect(newsw.getFeaturesReply()).andReturn(new OFFeaturesReply()).anyTimes();
        expect(newsw.getPorts()).andReturn(new HashMap<Short,OFPhysicalPort>());

        controller.activeSwitches.put(0L, oldsw);
        replay(newsw, channel, channel2);

        controller.addSwitch(newsw);

        verify(newsw, channel, channel2);
    }
}
