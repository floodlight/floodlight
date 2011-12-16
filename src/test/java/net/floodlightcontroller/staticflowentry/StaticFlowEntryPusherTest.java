package net.floodlightcontroller.staticflowentry;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.util.HexString;

public class StaticFlowEntryPusherTest extends FloodlightTestCase {
    String flowMod1, flowMod2;
    static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";
    
    @Before
    public void setUp() {
        super.setUp();
        flowMod1 = "{\"switch\": \"00:00:00:00:00:00:00:01\", " +
                   "\"name\": \"flow-mod-1\", " +
                   "\"cookie\": \"0\", " +
                   "\"priority\": \"32768\", " +
                   "\"ingress-port\": \"1\"," +
                   "\"active\": \"true\", " +
                   "\"actions\": \"output=3\"}";
        
        flowMod2 = "{\"switch\": \"00:00:00:00:00:00:00:01\", " +
                   "\"name\": \"flow-mod-2\", " +
                   "\"cookie\": \"0\", " +
                   "\"priority\": \"32768\", " +
                   "\"ingress-port\": \"2\"," +
                   "\"active\": \"true\", " +
                   "\"actions\": \"output=3\"}";
    }
    
    @Test
    public void testAddAndRemoveEntries() throws Exception {
        StaticFlowEntryPusher staticFlowEntryPusher = new StaticFlowEntryPusher();
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        long dpid = HexString.toLong(TestSwitch1DPID);
        Capture<OFMessage> writeCapture = new Capture<OFMessage>(CaptureType.ALL);
        Capture<FloodlightContext> contextCapture = new Capture<FloodlightContext>(CaptureType.ALL);
        Capture<List<OFMessage>> writeCaptureList = new Capture<List<OFMessage>>(CaptureType.ALL);
        
        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.write(capture(writeCaptureList), capture(contextCapture));
        expectLastCall().anyTimes();
        
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
        switchMap.put(dpid, mockSwitch);
        mockFloodlightProvider.setSwitches(switchMap);
        staticFlowEntryPusher.setFloodlightProvider(mockFloodlightProvider);
        long timeSfpStart = System.currentTimeMillis();
        staticFlowEntryPusher.startUp();
        
        // if someone calls getId(), return this dpid instead
        expect(mockSwitch.getId()).andReturn(dpid).anyTimes();
        replay(mockSwitch);
        
        // hook the static pusher up to the fake switch
        staticFlowEntryPusher.addedSwitch(mockSwitch);
        
        verify(mockSwitch);
        
        // Add entries
        staticFlowEntryPusher.addEntry(flowMod1);
        staticFlowEntryPusher.addEntry(flowMod2);
        
        // Verify that the switch has gotten some flow_mods
        assertEquals(true, writeCapture.getValues().size() == 2);
        
        long timeNow = System.currentTimeMillis();
        // Sleep just long enough for static flow pusher to re-push entires
        long timeNeededToSleep = (staticFlowEntryPusher.getFlowPushTimeSeconds() * 1000) - (timeNow - timeSfpStart);
        if (timeNeededToSleep > 0)
            Thread.sleep(timeNeededToSleep);
        // Make sure the entries were pushed again
        assertEquals(true, writeCapture.getValues().size() == 4);
        
        // Remove entries
        staticFlowEntryPusher.removeEntry(flowMod1);
        staticFlowEntryPusher.removeEntry(flowMod2);
        
        Thread.sleep(staticFlowEntryPusher.getFlowPushTimeSeconds() * 1000);
        // Make sure the entries were NOT pushed again
        assertEquals(true, writeCapture.getValues().size() == 4);
    }
}
