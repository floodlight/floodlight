package net.floodlightcontroller.flowcache;

import static org.easymock.EasyMock.*;

import java.util.ArrayList;

import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;

public class FlowReconcileMgrTest extends FloodlightTestCase {

    protected MockFloodlightProvider mockFloodlightProvider;
    protected FlowReconcileManager flowReconcileMgr;
    
    OFStatisticsRequest ofStatsRequest;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        flowReconcileMgr = new FlowReconcileManager();
        
        flowReconcileMgr.init(fmc);
    }

    /** Verify pipeline listener registration and ordering
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testFlowReconcilePipeLine() throws Exception {
        IFlowReconcileListener r1 = EasyMock.createNiceMock(IFlowReconcileListener.class);
        IFlowReconcileListener r2 = EasyMock.createNiceMock(IFlowReconcileListener.class);
        IFlowReconcileListener r3 = EasyMock.createNiceMock(IFlowReconcileListener.class);
        
        expect(r1.getName()).andReturn("r1").anyTimes();
        expect(r2.getName()).andReturn("r2").anyTimes();
        expect(r3.getName()).andReturn("r3").anyTimes();
        
        // Set the listeners' order: r1 -> r2 -> r3
        expect(r1.isCallbackOrderingPrereq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(r1.isCallbackOrderingPostreq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        expect(r2.isCallbackOrderingPrereq((OFType)anyObject(), eq("r1"))).andReturn(true).anyTimes();
        expect(r2.isCallbackOrderingPrereq((OFType)anyObject(), eq("r3"))).andReturn(false).anyTimes();
        expect(r2.isCallbackOrderingPostreq((OFType)anyObject(), eq("r1"))).andReturn(false).anyTimes();
        expect(r2.isCallbackOrderingPostreq((OFType)anyObject(), eq("r3"))).andReturn(true).anyTimes();
        expect(r3.isCallbackOrderingPrereq((OFType)anyObject(), eq("r1"))).andReturn(false).anyTimes();
        expect(r3.isCallbackOrderingPrereq((OFType)anyObject(), eq("r2"))).andReturn(true).anyTimes();
        expect(r3.isCallbackOrderingPostreq((OFType)anyObject(), (String)anyObject())).andReturn(false).anyTimes();
        
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).
                  andThrow(new RuntimeException("This is NOT an error! We are testing exception catching."));
        
        replay(r1, r2, r3);
        flowReconcileMgr.clearFlowReconcileListeners();
        flowReconcileMgr.addFlowReconcileListener(r1);
        flowReconcileMgr.addFlowReconcileListener(r2);
        flowReconcileMgr.addFlowReconcileListener(r3);
        
        OFMatchReconcile ofmRcIn = new OFMatchReconcile();
        try {
            flowReconcileMgr.reconcileFlow(ofmRcIn);
        } catch (RuntimeException e) {
            assertEquals(e.getMessage().startsWith("This is NOT an error!"), true);
        }
        verify(r1, r2, r3);

        // verify STOP works
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.STOP).times(1);
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() {
                fail("Unexpected call");
                return Command.STOP;
            }
        }).anyTimes();
        
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn);
        verify(r1, r2, r3);
        
        // verify CONTINUE works
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.CONTINUE).times(1);
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.STOP).times(1);
        expect(r3.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() {
                fail("Unexpected call");
                return Command.STOP;
            }
        }).anyTimes();
        
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn);
        verify(r1, r2, r3);
        
        // verify CONTINUE works
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.CONTINUE).times(1);
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.CONTINUE).times(1);
        expect(r3.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.STOP).times(1);
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn);
        verify(r1, r2, r3);
        
        // Verify removeFlowReconcileListener
        flowReconcileMgr.removeFlowReconcileListener(r1);
        reset(r1, r2, r3);
        expect(r1.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject()));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            public Object answer() {
                fail("Unexpected call to a listener that is removed from the chain.");
                return Command.STOP;
            }
        }).anyTimes();
        expect(r2.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.CONTINUE).times(1);
        expect(r3.reconcileFlows((ArrayList<OFMatchReconcile>)anyObject())).andReturn(Command.STOP).times(1);
        replay(r1, r2, r3);
        flowReconcileMgr.reconcileFlow(ofmRcIn);
        verify(r1, r2, r3);
    }
}