/**
 *    Copyright 2012, Jason Parraga, Marist College 
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
package net.floodlightcontroller.flowcache;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.flowcache.FlowReconcileManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

/**
 * Unit test for PortDownReconciliation. To test the class I have generated
 * there very simple network topologies. an OFMatchReconcile object with
 * information about the PORT_DOWN event is passed to the class, where it begins
 * breaking down the information,analyzing the switches for flows and deleting
 * those that are invalid. This Test specifically verifies that each switch is
 * queried for flows once and is sent the appropriate OFFlowMod delete message.
 * 
 * @author Jason Parraga
 */

public class PortDownReconciliationTest extends FloodlightTestCase {

    protected FloodlightModuleContext fmc;
    protected ILinkDiscoveryService lds;
    protected FlowReconcileManager flowReconcileMgr;
    protected MockThreadPoolService tps;
    protected DefaultEntityClassifier entityClassifier;
    protected PortDownReconciliation pdr;
    protected ITopologyService topology;
    protected IOFSwitch sw1, sw2, sw3, sw4;
    protected Map<Long, IOFSwitch> switches;
    protected Capture<List<OFMessage>> wc1, wc2, wc3, wc4;
    protected Capture<FloodlightContext> bc1, bc2, bc3, bc4;
    protected OFMessage fm, fm2;
    protected ArrayList<OFMatchReconcile> lofmr;
    protected OFMatchReconcile ofmr;
    protected static Logger log;
    protected FloodlightContext cntx;
    protected List<OFStatistics> statsReply;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        log = LoggerFactory.getLogger(PortDownReconciliation.class);
        fmc = new FloodlightModuleContext();
        mockFloodlightProvider = getMockFloodlightProvider();
        pdr = new PortDownReconciliation();
        lds = createMock(ILinkDiscoveryService.class);
        entityClassifier = new DefaultEntityClassifier();
        tps = new MockThreadPoolService();
        flowReconcileMgr = new FlowReconcileManager();
        topology = createMock(ITopologyService.class);
        cntx = new FloodlightContext();
        statsReply = new ArrayList<OFStatistics>();

        fmc.addService(IThreadPoolService.class, tps);
        fmc.addService(IFloodlightProviderService.class,
                       getMockFloodlightProvider());
        fmc.addService(IFlowReconcileService.class, flowReconcileMgr);
        fmc.addService(ITopologyService.class, topology);
        fmc.addService(IEntityClassifierService.class, entityClassifier);
        fmc.addService(ILinkDiscoveryService.class, lds);

        tps.init(fmc);
        flowReconcileMgr.init(fmc);
        entityClassifier.init(fmc);
        getMockFloodlightProvider().init(fmc);
        pdr.init(fmc);

        tps.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
        entityClassifier.startUp(fmc);
        getMockFloodlightProvider().startUp(fmc);
        pdr.startUp(fmc);

        // The STATS_REQUEST object used when querying the switches for flows
        OFStatisticsRequest req = new OFStatisticsRequest();
        req.setStatisticType(OFStatisticsType.FLOW);
        int requestLength = req.getLengthU();
        OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
        specificReq.setMatch(new OFMatch().setWildcards(0xffffffff));
        specificReq.setOutPort((short) 3);
        specificReq.setTableId((byte) 0xff);
        req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
        requestLength += specificReq.getLength();
        req.setLengthU(requestLength);

        // Actions for the STATS_REPLY object
        OFActionOutput action = new OFActionOutput((short) 3, (short) 0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        // Match for the STATS_REPLY object
        OFMatch m = new OFMatch();
        // Set the incoming port to 1 so that it will find the connected
        m.setInputPort((short) 1);

        // STATS_REPLY object
        OFFlowStatisticsReply reply = new OFFlowStatisticsReply();
        reply.setActions(actions);
        reply.setMatch(m);
        // Add the reply to the list of OFStatistics
        statsReply.add(reply);

        // Create the STATS_REPLY asynchronous reply object
        Callable<List<OFStatistics>> replyFuture = new ReplyFuture();
        // Assign the callable object to a Futuretask so that it will produce
        // future results
        FutureTask<List<OFStatistics>> futureStats = new FutureTask<List<OFStatistics>>(
                                                                                        replyFuture);

        // Assign the results of calling the object (the asynchronous reply)
        Future<List<OFStatistics>> results = getResults(futureStats);

        // SW1 -- Mock switch for base and multiple switch test case
        sw1 = EasyMock.createNiceMock(IOFSwitch.class);
        // Expect that the switch's ID is 1
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.queryStatistics(req)).andReturn(results).once();
        // Captures to hold resulting flowmod delete messages
        wc1 = new Capture<List<OFMessage>>(CaptureType.ALL);
        bc1 = new Capture<FloodlightContext>(CaptureType.ALL);
        // Capture the parameters passed when sw1.write is invoked
        sw1.write(capture(wc1), capture(bc1));
        expectLastCall().once();
        replay(sw1);

        // SW2 -- Mock switch for extended test cases
        sw2 = EasyMock.createNiceMock(IOFSwitch.class);
        // Expect that the switch's ID is 2
        expect(sw2.getId()).andReturn(2L).anyTimes();
        expect(sw2.queryStatistics(req)).andReturn(results).once();
        wc2 = new Capture<List<OFMessage>>(CaptureType.ALL);
        bc2 = new Capture<FloodlightContext>(CaptureType.ALL);
        // Capture the parameters passwed when sw1.write is invoked
        sw2.write(capture(wc2), capture(bc2));
        expectLastCall().anyTimes();
        replay(sw2);

        // SW3 -- Mock switch for extended test cases
        sw3 = EasyMock.createNiceMock(IOFSwitch.class);
        // Expect that the switch's ID is 3
        expect(sw3.getId()).andReturn(3L).anyTimes();
        expect(sw3.queryStatistics(req)).andReturn(results).once();
        wc3 = new Capture<List<OFMessage>>(CaptureType.ALL);
        bc3 = new Capture<FloodlightContext>(CaptureType.ALL);
        // Capture the parameters passwed when sw1.write is invoked
        sw3.write(capture(wc3), capture(bc3));
        expectLastCall().anyTimes();
        replay(sw3);

        // SW4 -- Mock switch for extended test cases
        sw4 = EasyMock.createNiceMock(IOFSwitch.class);
        // Expect that the switch's ID is 4
        expect(sw4.getId()).andReturn(4L).anyTimes();
        expect(sw4.queryStatistics(req)).andReturn(results).once();
        wc4 = new Capture<List<OFMessage>>(CaptureType.ALL);
        bc4 = new Capture<FloodlightContext>(CaptureType.ALL);
        // Capture the parameters passed when sw1.write is invoked
        sw4.write(capture(wc4), capture(bc4));
        expectLastCall().anyTimes();
        replay(sw4);

        // Here we create the OFMatch Reconcile list we wish to pass
        lofmr = new ArrayList<OFMatchReconcile>();

        // Create the only OFMatch Reconcile object that will be in the list
        ofmr = new OFMatchReconcile();
        long affectedSwitch = sw1.getId();
        OFMatchWithSwDpid ofmatchsw = new OFMatchWithSwDpid(new OFMatch().setWildcards(OFMatch.OFPFW_ALL),
                                                            affectedSwitch);
        ofmr.rcAction = OFMatchReconcile.ReconcileAction.UPDATE_PATH;
        ofmr.ofmWithSwDpid = ofmatchsw;

        // We'll say port 3 went down
        ofmr.outPort = 3;

        // Add the OFMatch Reconcile object to the list
        lofmr.add(ofmr);

        // Expected Flow Mod Deletes Messages
        // Flow Mod Delete for base switch
        fm = ((OFFlowMod) mockFloodlightProvider.getOFMessageFactory()
                                                 .getMessage(OFType.FLOW_MOD)).setMatch(new OFMatch().setWildcards(OFMatch.OFPFW_ALL))
                                                                              .setCommand(OFFlowMod.OFPFC_DELETE)
                                                                              // Notice
                                                                              // we
                                                                              // specify
                                                                              // an
                                                                              // outPort
                                                                              .setOutPort((short) 3)
                                                                              .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));

        // Flow Mod Delete for the neighborswitches
        fm2 = ((OFFlowMod) mockFloodlightProvider.getOFMessageFactory()
                                                  .getMessage(OFType.FLOW_MOD))
        // Notice that this Match object is more specific
        .setMatch(reply.getMatch())
                                                                               .setCommand(OFFlowMod.OFPFC_DELETE)
                                                                               // Notice
                                                                               // we
                                                                               // specific
                                                                               // an
                                                                               // outPort
                                                                               .setOutPort((short) 3)
                                                                               .setLength(U16.t(OFFlowMod.MINIMUM_LENGTH));

    }

    // This generates the asynchronous reply to sw.getStatistics()
    public Future<List<OFStatistics>>
            getResults(FutureTask<List<OFStatistics>> futureStats) {
        Thread t = new Thread(futureStats);
        t.start();
        return futureStats;

    }

    // Class for the asynchronous reply
    public class ReplyFuture implements Callable<List<OFStatistics>> {
        @Override
        public List<OFStatistics> call() throws Exception {
            // return stats reply defined above
            return statsReply;
        }
    }

    /**
     * This tests the port down reconciliation in the event that the base switch
     * is the only switch involved in the PORT_DOWN event. It simply deletes
     * flows concerning the downed port.
     * 
     * @verify checks to see that a general clearFlowMods(Short outPort) is
     *         called
     * @throws Exception
     */
    @Test
    public void testSingleSwitchPortDownReconciliation() throws Exception {
        log.debug("Starting single switch port down reconciliation test");
        // Load the switch map
        switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        mockFloodlightProvider.setSwitches(switches);

        // Reconcile flows with specified OFMatchReconcile
        pdr.reconcileFlows(lofmr);
        // Validate results
        verify(sw1);
        
        assertTrue(wc1.hasCaptured());

        List<OFMessage> msglist = wc1.getValues().get(0);

        // Make sure the messages we captures correct
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm, m);
        }
    }

    /**
     * This tests the port down reconciliation in the event that the base switch
     * is connected to a chain of three switches. It discovers that is has 1
     * neighbor, which recursively finds out that it has 1 neighbor until the
     * final switch "sw4" is evaluated, which has no neighbors.
     * 
     * @verify checks to see that a general clearFlowMods(Short outPort) is
     *         called on the base switch while specific clearFlowMods(OFMatch
     *         match, Short outPort) are called on the neighboring switches
     * @throws Exception
     */
    @Test
    public void testLinearLinkPortDownReconciliation() throws Exception {
        log.debug("Starting linear link port down reconciliation test");

        // Load the switch map
        switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        switches.put(4L, sw4);
        mockFloodlightProvider.setSwitches(switches);

        // Create the links between the switches
        // (Switch 4) --> (Switch 3) --> (Switch 2) --> (Switch 1)
        Map<Link, LinkInfo> links = new HashMap<Link, LinkInfo>();
        Link link = new Link(2L, (short) 3, 1L, (short) 1);
        Link link2 = new Link(3L, (short) 3, 2L, (short) 1);
        Link link3 = new Link(4L, (short) 3, 3L, (short) 1);
        LinkInfo linkinfo = null;
        links.put(link, linkinfo);
        links.put(link2, linkinfo);
        links.put(link3, linkinfo);

        // Make sure that the link discovery service provides the link we made
        expect(lds.getLinks()).andReturn(links).anyTimes();
        replay(lds);

        // Reconcile flows with specified OFMatchReconcile
        pdr.reconcileFlows(lofmr);
        // Validate results
        verify(sw1, sw2, sw3, sw4);

        // Make sure each capture is not null
        assertTrue(wc2.hasCaptured());
        assertTrue(wc3.hasCaptured());
        assertTrue(wc4.hasCaptured());

        // Make sure each capture has captured the proper Flow Mod Delete
        // message
        List<OFMessage> msglist = wc2.getValues().get(0);
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm2, m);
        }

        msglist = wc3.getValues().get(0);
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm2, m);
        }

        msglist = wc4.getValues().get(0);
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm2, m);
        }
    }

    /**
     * This tests the port down reconciliation in the event that the base switch
     * has three separate neighboring switches with invalid flows. It discovers
     * that is has 3 neighbors and each of them delete flows with the specific
     * OFMatch and outPort.
     * 
     * @verify checks to see that a general clearFlowMods(Short outPort) is
     *         called on the base switch while specific clearFlowMods(OFMatch
     *         match, Short outPort) are called on the neighboring switches
     * @throws Exception
     */
    @Test
    public void testMultipleLinkPortDownReconciliation() throws Exception {
        log.debug("Starting multiple link port down reconciliation test");

        // Load the switch map
        switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        switches.put(3L, sw3);
        switches.put(4L, sw4);
        mockFloodlightProvider.setSwitches(switches);

        // Create the links between the switches
        // (Switch 4 output port 3) --> (Switch 1 input port 1)
        // (Switch 3 output port 3) --> (Switch 1 input port 1)
        // (Switch 2 output port 3) --> (Switch 1 input port 1)
        Map<Link, LinkInfo> links = new HashMap<Link, LinkInfo>();
        Link link = new Link(2L, (short) 3, 1L, (short) 1);
        Link link2 = new Link(3L, (short) 3, 1L, (short) 1);
        Link link3 = new Link(4L, (short) 3, 1L, (short) 1);
        LinkInfo linkinfo = null;
        links.put(link, linkinfo);
        links.put(link2, linkinfo);
        links.put(link3, linkinfo);

        // Make sure that the link discovery service provides the link we made
        expect(lds.getLinks()).andReturn(links).anyTimes();
        replay(lds);

        // Reconcile flows with specified OFMatchReconcile
        pdr.reconcileFlows(lofmr);
        // Validate results
        verify(sw1, sw2, sw3, sw4);

        // Make sure each capture is not null
        assertTrue(wc2.hasCaptured());
        assertTrue(wc3.hasCaptured());
        assertTrue(wc4.hasCaptured());

        // Make sure each capture has captured the proper Flow Mod Delete
        // message
        List<OFMessage> msglist = wc2.getValues().get(0);
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm2, m);
        }

        msglist = wc3.getValues().get(0);
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm2, m);
        }

        msglist = wc4.getValues().get(0);
        for (OFMessage m : msglist) {
            if (m instanceof OFFlowMod) assertEquals(fm2, m);
        }
    }
}
