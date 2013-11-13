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

package net.floodlightcontroller.flowcache;

import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.ListIterator;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.counter.SimpleCounter;
import net.floodlightcontroller.counter.CounterValue.CounterType;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.flowcache.PriorityPendingQueue.EventPriority;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;

public class FlowReconcileMgrTest extends FloodlightTestCase {

    protected FlowReconcileManager flowReconcileMgr;
    protected MockThreadPoolService threadPool;
    protected ICounterStoreService counterStore;
    protected FloodlightModuleContext fmc;
    
    OFStatisticsRequest ofStatsRequest;

    protected int NUM_FLOWS_PER_THREAD = 100;
    protected int NUM_THREADS = 20;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        fmc = new FloodlightModuleContext();
        flowReconcileMgr = new FlowReconcileManager();
        threadPool = new MockThreadPoolService();
        counterStore = createMock(ICounterStoreService.class);
        
        fmc.addService(ICounterStoreService.class, counterStore);
        fmc.addService(IThreadPoolService.class, threadPool);
        
        threadPool.init(fmc);
        flowReconcileMgr.init(fmc);

        threadPool.startUp(fmc);
        flowReconcileMgr.startUp(fmc);
    }

    /** Verify pipeline listener registration and ordering
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testFlowReconcilePipeLine() throws Exception {
        flowReconcileMgr.flowReconcileEnabled = true;
    
        IFlowReconcileListener r1 =
            EasyMock.createNiceMock(IFlowReconcileListener.class);
        IFlowReconcileListener r2 =
            EasyMock.createNiceMock(IFlowReconcileListener.class);
        IFlowReconcileListener r3 =
            EasyMock.createNiceMock(IFlowReconcileListener.class);
        
        expect(r1.getName()).andReturn("r1").anyTimes();
        expect(r2.getName()).andReturn("r2").anyTimes();
        expect(r3.getName()).andReturn("r3").anyTimes();
        
        // Set the listeners' order: r1 -> r2 -> r3
        expect(r1.isCallbackOrderingPrereq((OFType)anyObject(),
            (String)anyObject())).andReturn(false).anyTimes();
        expect(r1.isCallbackOrderingPostreq((OFType)anyObject(),
            (String)anyObject())).andReturn(false).anyTimes();
        expect(r2.isCallbackOrderingPrereq((OFType)anyObject(),
            eq("r1"))).andReturn(true).anyTimes();
        expect(r2.isCallbackOrderingPrereq((OFType)anyObject(),
            eq("r3"))).andReturn(false).anyTimes();
        expect(r2.isCallbackOrderingPostreq((OFType)anyObject(),
            eq("r1"))).andReturn(false).anyTimes();
        expect(r2.isCallbackOrderingPostreq((OFType)anyObject(),
            eq("r3"))).andReturn(true).anyTimes();
        expect(r3.isCallbackOrderingPrereq((OFType)anyObject(),
            eq("r1"))).andReturn(false).anyTimes();
        expect(r3.isCallbackOrderingPrereq((OFType)anyObject(),
            eq("r2"))).andReturn(true).anyTimes();
        expect(r3.isCallbackOrderingPostreq((OFType)anyObject(),
            (String)anyObject())).andReturn(false).anyTimes();
        
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).
                  andThrow(new RuntimeException("This is NOT an error! " +
                            "We are testing exception catching."));
        
        SimpleCounter cnt = (SimpleCounter)SimpleCounter.createCounter(
                            new Date(),
                            CounterType.LONG);
        cnt.increment();
        expect(counterStore.getCounter(
                flowReconcileMgr.controllerPktInCounterName))
                .andReturn(cnt)
                .anyTimes();
        
        replay(r1, r2, r3, counterStore);
        flowReconcileMgr.clearFlowReconcileListeners();
        flowReconcileMgr.addFlowReconcileListener(r1);
        flowReconcileMgr.addFlowReconcileListener(r2);
        flowReconcileMgr.addFlowReconcileListener(r3);
        
        int pre_flowReconcileThreadRunCount =
                flowReconcileMgr.flowReconcileThreadRunCount.get();
        Date startTime = new Date();
        OFMatchReconcile ofmRcIn = new OFMatchReconcile();
        try {
            flowReconcileMgr.reconcileFlow(ofmRcIn,EventPriority.HIGH);
            flowReconcileMgr.doReconcile();
        } catch (RuntimeException e) {
            assertEquals(e.getMessage()
                .startsWith("This is NOT an error!"), true);
        }
        
        verify(r1, r2, r3);

        // verify STOP works
        reset(r1, r2, r3);
        
        // restart reconcileThread since it exited due to previous runtime
        // exception.
        flowReconcileMgr.startUp(fmc);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.STOP).times(1);
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() {
                fail("Unexpected call");
                return Command.STOP;
            }
        }).anyTimes();
        
        pre_flowReconcileThreadRunCount =
            flowReconcileMgr.flowReconcileThreadRunCount.get();
        startTime = new Date();
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn,EventPriority.HIGH);
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() <=
                pre_flowReconcileThreadRunCount) {
            Thread.sleep(10);
            Date currTime = new Date();
            assertTrue((currTime.getTime() - startTime.getTime()) < 1000);
        }
        verify(r1, r2, r3);
        
        // verify CONTINUE works
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.CONTINUE).times(1);
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.STOP).times(1);
        expect(r3.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() {
                fail("Unexpected call");
                return Command.STOP;
            }
        }).anyTimes();
        
        pre_flowReconcileThreadRunCount =
            flowReconcileMgr.flowReconcileThreadRunCount.get();
        startTime = new Date();
        
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn,EventPriority.HIGH);
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() <=
                pre_flowReconcileThreadRunCount) {
            Thread.sleep(10);
            Date currTime = new Date();
            assertTrue((currTime.getTime() - startTime.getTime()) < 1000);
        }
        verify(r1, r2, r3);
        
        // verify CONTINUE works
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.CONTINUE).times(1);
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.CONTINUE).times(1);
        expect(r3.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.STOP).times(1);
        
        pre_flowReconcileThreadRunCount =
            flowReconcileMgr.flowReconcileThreadRunCount.get();
        startTime = new Date();
        
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn,EventPriority.HIGH);
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() <=
                pre_flowReconcileThreadRunCount) {
            Thread.sleep(10);
            Date currTime = new Date();
            assertTrue((currTime.getTime() - startTime.getTime()) < 1000);
        }
        verify(r1, r2, r3);
        
        // Verify removeFlowReconcileListener
        flowReconcileMgr.removeFlowReconcileListener(r1);
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() {
                fail("Unexpected call to a listener that is " +
                        "removed from the chain.");
                return Command.STOP;
            }
        }).anyTimes();
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.CONTINUE).times(1);
        expect(r3.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.STOP).times(1);
        
        pre_flowReconcileThreadRunCount =
            flowReconcileMgr.flowReconcileThreadRunCount.get();
        startTime = new Date();
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn,EventPriority.HIGH);
        while (flowReconcileMgr.flowReconcileThreadRunCount.get() <=
                pre_flowReconcileThreadRunCount) {
            Thread.sleep(10);
            Date currTime = new Date();
            assertTrue((currTime.getTime() - startTime.getTime()) < 1000);
        }
        verify(r1, r2, r3);
    }
    
    @Test
    public void testGetPktInRate() {
        internalTestGetPktInRate(CounterType.LONG);
        internalTestGetPktInRate(CounterType.DOUBLE);
    }
    
    protected void internalTestGetPktInRate(CounterType type) {
        Date currentTime = new Date();
        SimpleCounter newCnt = (SimpleCounter)SimpleCounter.createCounter(
                                currentTime, type);
        newCnt.increment(currentTime, 1);
    
        // Set the lastCounter time in the future of the current time
        Date lastCounterTime = new Date(currentTime.getTime() + 1000);
        flowReconcileMgr.lastPacketInCounter =
                (SimpleCounter)SimpleCounter.createCounter(
                    lastCounterTime, type);
        flowReconcileMgr.lastPacketInCounter.increment(lastCounterTime, 1);
    
        assertEquals(FlowReconcileManager.MAX_SYSTEM_LOAD_PER_SECOND,
                flowReconcileMgr.getPktInRate(newCnt, new Date()));
    
        // Verify the rate == 0 time difference is zero.
        lastCounterTime = new Date(currentTime.getTime() - 1000);
        flowReconcileMgr.lastPacketInCounter.increment(lastCounterTime, 1);
        assertEquals(0, flowReconcileMgr.getPktInRate(newCnt, lastCounterTime));
    
        /** verify the computation is correct.
         *  new = 2000, old = 1000, Tdiff = 1 second.
         *  rate should be 1000/second
         */
        newCnt = (SimpleCounter)SimpleCounter.createCounter(
                currentTime, type);
        newCnt.increment(currentTime, 2000);
    
        lastCounterTime = new Date(currentTime.getTime() - 1000);
        flowReconcileMgr.lastPacketInCounter =
                (SimpleCounter)SimpleCounter.createCounter(
                    lastCounterTime, type);
        flowReconcileMgr.lastPacketInCounter.increment(lastCounterTime, 1000);
        assertEquals(1000, flowReconcileMgr.getPktInRate(newCnt, currentTime));
    
        /** verify the computation is correct.
         *  new = 2,000,000, old = 1,000,000, Tdiff = 2 second.
         *  rate should be 1000/second
         */
        newCnt = (SimpleCounter)SimpleCounter.createCounter(
                currentTime, type);
        newCnt.increment(currentTime, 2000000);
    
        lastCounterTime = new Date(currentTime.getTime() - 2000);
        flowReconcileMgr.lastPacketInCounter =
                (SimpleCounter)SimpleCounter.createCounter(
                    lastCounterTime, type);
        flowReconcileMgr.lastPacketInCounter.increment(lastCounterTime,
                1000000);
        assertEquals(500000, flowReconcileMgr.getPktInRate(newCnt,
                    currentTime));
    }
    
    @Test
    public void testGetCurrentCapacity() throws Exception {
        // Disable the reconcile thread.
        flowReconcileMgr.flowReconcileEnabled = false;
    
        int minFlows = FlowReconcileManager.MIN_FLOW_RECONCILE_PER_SECOND *
                FlowReconcileManager.FLOW_RECONCILE_DELAY_MILLISEC / 1000;
    
        /** Verify the initial state, when packetIn counter has not
         *  been created.
         */
        expect(counterStore.getCounter(
                flowReconcileMgr.controllerPktInCounterName))
        .andReturn(null)
        .times(1);
    
        replay(counterStore);
        assertEquals(minFlows, flowReconcileMgr.getCurrentCapacity());
        verify(counterStore);
    
        /** Verify the initial state, when lastPacketInCounter is null */
        reset(counterStore);
        Date currentTime = new Date();
        SimpleCounter newCnt = (SimpleCounter)SimpleCounter.createCounter(
                        currentTime, CounterType.LONG);
    
        expect(counterStore.getCounter(
            flowReconcileMgr.controllerPktInCounterName))
        .andReturn(newCnt)
        .times(1);
        long initPktInCount = 1000;
        newCnt.increment(currentTime, initPktInCount);
    
        replay(counterStore);
        assertEquals(minFlows, flowReconcileMgr.getCurrentCapacity());
        verify(counterStore);
    
        /** Now the lastPacketInCounter has been set.
         *  lastCounter = 1,000 and newCounter = 3,000, t = 1 second
         *  packetInRate = 2,000/sec.
         *  capacity should be 10k - 2k = 8k
         */
        reset(counterStore);
        newCnt = (SimpleCounter)SimpleCounter.createCounter(
                    currentTime, CounterType.LONG);
        currentTime = new Date(currentTime.getTime() + 200);
        long nextPktInCount = 3000;
        newCnt.increment(currentTime, nextPktInCount);
    
        expect(counterStore.getCounter(
                flowReconcileMgr.controllerPktInCounterName))
        .andReturn(newCnt)
        .times(1);
    
        replay(counterStore);
        // Wait for 1 second so that enough elapsed time to compute capacity.
        Thread.sleep(1000);
        int capacity = flowReconcileMgr.getCurrentCapacity();
        verify(counterStore);
        long expectedCap = (FlowReconcileManager.MAX_SYSTEM_LOAD_PER_SECOND -
                (nextPktInCount - initPktInCount)) *
                FlowReconcileManager.FLOW_RECONCILE_DELAY_MILLISEC / 1000;
        assertEquals(expectedCap, capacity);
    }
    
    /** Verify the flows are sent to the reconcile pipeline in order.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testQueueFlowsOrder() {
        flowReconcileMgr.flowReconcileEnabled = false;
        
        IFlowReconcileListener r1 =
            EasyMock.createNiceMock(IFlowReconcileListener.class);
        
        expect(r1.getName()).andReturn("r1").anyTimes();
        
        // Set the listeners' order: r1 -> r2 -> r3
        expect(r1.isCallbackOrderingPrereq((OFType)anyObject(),
            (String)anyObject())).andReturn(false).anyTimes();
        expect(r1.isCallbackOrderingPostreq((OFType)anyObject(),
            (String)anyObject())).andReturn(false).anyTimes();
        
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andAnswer(new IAnswer<Command>() {
            @Override
            public Command answer() throws Throwable {
                ArrayList<OFMatchReconcile> ofmList =
                    (ArrayList<OFMatchReconcile>)EasyMock.
                        getCurrentArguments()[0];
                ListIterator<OFMatchReconcile> lit = ofmList.listIterator();
                int index = 0;
                while (lit.hasNext()) {
                    OFMatchReconcile ofm = lit.next();
                    assertEquals(index++, ofm.cookie);
                }
                return Command.STOP;
            }
        }).times(1);
        
        SimpleCounter cnt = (SimpleCounter)SimpleCounter.createCounter(
                            new Date(),
                            CounterType.LONG);
        cnt.increment();
        expect(counterStore.getCounter(
                flowReconcileMgr.controllerPktInCounterName))
                .andReturn(cnt)
                .anyTimes();
        
        replay(r1, counterStore);
        flowReconcileMgr.clearFlowReconcileListeners();
        flowReconcileMgr.addFlowReconcileListener(r1);
        
        OFMatchReconcile ofmRcIn = new OFMatchReconcile();
        int index = 0;
        for (index = 0; index < 10; index++) {
            ofmRcIn.cookie = index;
            flowReconcileMgr.reconcileFlow(ofmRcIn,EventPriority.HIGH);
        }
        flowReconcileMgr.flowReconcileEnabled = true;
        flowReconcileMgr.doReconcile();
        
        verify(r1);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testQueueFlowsByManyThreads() {
        // Disable the reconcile thread so that the queue won't be emptied.
        flowQueueTest(false);
    
        // Enable the reconcile thread. The queue should be empty.
        Date currentTime = new Date();
        SimpleCounter newCnt = (SimpleCounter)SimpleCounter.createCounter(
                    currentTime, CounterType.LONG);
    
        expect(counterStore.getCounter(
                    flowReconcileMgr.controllerPktInCounterName))
        .andReturn(newCnt)
        .anyTimes();
        long initPktInCount = 10000;
        newCnt.increment(currentTime, initPktInCount);
    
        IFlowReconcileListener r1 =
                EasyMock.createNiceMock(IFlowReconcileListener.class);
        
        expect(r1.getName()).andReturn("r1").anyTimes();
        
        // Set the listeners' order: r1 -> r2 -> r3
        expect(r1.isCallbackOrderingPrereq((OFType)anyObject(),
                (String)anyObject())).andReturn(false).anyTimes();
        expect(r1.isCallbackOrderingPostreq((OFType)anyObject(),
                (String)anyObject())).andReturn(false).anyTimes();
        
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()))
        .andReturn(Command.CONTINUE).anyTimes();
        
        flowReconcileMgr.clearFlowReconcileListeners();
        replay(r1, counterStore);
        flowQueueTest(true);
        verify(r1, counterStore);
    }
    
    protected void flowQueueTest(boolean enableReconcileThread) {
        flowReconcileMgr.flowReconcileEnabled = enableReconcileThread;
    
        // Simulate flow
        for (int i = 0; i < NUM_THREADS; i++) {
            Runnable worker = this.new FlowReconcileWorker();
            Thread t = new Thread(worker);
            t.start();
        }
    
        Date startTime = new Date();
        int totalFlows = NUM_THREADS * NUM_FLOWS_PER_THREAD;
        if (enableReconcileThread) {
            totalFlows = 0;
        }
        while (flowReconcileMgr.flowQueue.size() != totalFlows) {
            Date currTime = new Date();
            assertTrue((currTime.getTime() - startTime.getTime()) < 1000);
        }

        // Make sure all flows are in the queue.
        assertEquals(totalFlows, flowReconcileMgr.flowQueue.size());
    }
    
    private class FlowReconcileWorker implements Runnable {
    @Override
        public void run() {
            OFMatchReconcile ofmRc = new OFMatchReconcile();
            // push large number of flows to be reconciled.
            for (int i = 0; i < NUM_FLOWS_PER_THREAD; i++) {
                flowReconcileMgr.reconcileFlow(ofmRc,EventPriority.LOW);
            }
        }
    }
}
